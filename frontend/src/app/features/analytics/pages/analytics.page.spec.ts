import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ViewContextStore } from '../../../core/state/view-context.store';
import {
  AnalyticsSummary,
  CalendarView,
  CategoryBreakdown,
  Series,
} from '../data-access/analytics.models';
import { AnalyticsPage } from './analytics.page';

const SUMMARY_NOMINAL: AnalyticsSummary = {
  period: {
    granularity: 'MONTH',
    from: '2026-11-01',
    to: '2026-11-30',
    label: 'Noviembre 2026',
  },
  view: 'PERSONAL',
  lens: 'NOMINAL',
  unit: 'ARS',
  income: '500000',
  expense: '350000',
  balance: '150000',
  savingsRatePct: '30',
  installmentCommitmentPct: '18',
  previous: { income: '480000', expense: '368421.0526' },
  realVariationPct: '8',
  lensApproximated: false,
};

const SERIES_NOMINAL: Series = {
  lens: 'NOMINAL',
  unit: 'ARS',
  granularity: 'MONTH',
  points: [
    { periodStart: '2026-10-01', label: 'Oct', income: '480000', expense: '368421', balance: '111579' },
    { periodStart: '2026-11-01', label: 'Nov', income: '500000', expense: '350000', balance: '150000' },
  ],
};

const CATEGORY_NOMINAL: CategoryBreakdown = {
  period: SUMMARY_NOMINAL.period,
  lens: 'NOMINAL',
  unit: 'ARS',
  total: '350000',
  slices: [
    {
      categoryId: 'cat-1',
      name: 'Supermercado',
      colorHex: '#0f766e',
      icon: 'shopping-cart',
      amount: '200000',
      pct: '57.1',
      variationPct: '-4',
    },
  ],
};

const CALENDAR_MOCK: CalendarView = {
  from: '2026-11-01',
  to: '2026-11-30',
  days: [
    {
      date: '2026-11-10',
      income: '0',
      expense: '45000',
      items: [
        {
          kind: 'INSTALLMENT',
          refId: 'inst-1',
          title: 'Cuota Notebook',
          amount: '45000',
          currencyCode: 'ARS',
        },
      ],
    },
  ],
};

describe('AnalyticsPage', () => {
  let fixture: ComponentFixture<AnalyticsPage>;
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AnalyticsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
    fixture = TestBed.createComponent(AnalyticsPage);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('updates all overview blocks and displays approximated banner when switching lens', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(SUMMARY_NOMINAL);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(SERIES_NOMINAL);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(CATEGORY_NOMINAL);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="lens-approximated-banner"]')).toBeNull();
    expect(el.querySelector('[data-testid="expense-comparison"]')?.textContent).toContain(
      'Gastos \u22125% vs. octubre',
    );
    expect(el.querySelector('[data-testid="real-variation-card"]')?.getAttribute('data-tone')).toBe(
      'amber',
    );
    expect(el.querySelector('[data-testid="installment-commitment-card"]')?.textContent).toContain(
      'Ya comprometido en cuotas: 18% del ingreso',
    );

    // Switch lens to CONSTANT_BASE ("A plata de hoy")
    el.querySelector<HTMLButtonElement>('[data-testid="analytics-lens-CONSTANT_BASE"]')?.click();
    fixture.detectChanges();

    expect(viewContext.lens()).toBe('CONSTANT_BASE');

    const sReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary');
    const serReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/series');
    const catReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category');

    expect(sReq.request.params.get('lens')).toBe('CONSTANT_BASE');
    expect(serReq.request.params.get('lens')).toBe('CONSTANT_BASE');
    expect(catReq.request.params.get('lens')).toBe('CONSTANT_BASE');

    sReq.flush({ ...SUMMARY_NOMINAL, lens: 'CONSTANT_BASE', lensApproximated: true });
    serReq.flush({ ...SERIES_NOMINAL, lens: 'CONSTANT_BASE' });
    catReq.flush({ ...CATEGORY_NOMINAL, lens: 'CONSTANT_BASE' });
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="lens-approximated-banner"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="analytics-lens-explanation"]')?.textContent).toContain(
      'IPC',
    );
  });

  it('switches to Calendario tab and fetches monthly calendar', () => {
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(SUMMARY_NOMINAL);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(SERIES_NOMINAL);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(CATEGORY_NOMINAL);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const tabs = el.querySelectorAll<HTMLButtonElement>(
      '[data-testid="analytics-tab-toggle"] button[role="tab"]',
    );
    tabs[1].click();
    fixture.detectChanges();

    const calReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/calendar');
    calReq.flush(CALENDAR_MOCK);
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="calendar-grid"]')).not.toBeNull();
  });
  it('exports the monthly summary of the period year with the Content-Disposition filename', async () => {
    const createObjectURL = vi.fn(() => 'blob:summary-1');
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true });
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true });
    const downloads: string[] = [];
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        downloads.push(this.download);
      });

    try {
      fixture.detectChanges();
      httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(SUMMARY_NOMINAL);
      httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(SERIES_NOMINAL);
      httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(CATEGORY_NOMINAL);
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      const trigger = el.querySelector<HTMLButtonElement>('[data-testid="analytics-export-btn"]')!;
      trigger.click();
      fixture.detectChanges();
      el.querySelector<HTMLButtonElement>('[data-testid="analytics-export-CSV"]')!.click();
      fixture.detectChanges();

      expect(trigger.disabled).toBe(true);

      const req = httpMock.expectOne((r) => r.url === '/bff/api/exports/monthly-summary');
      expect(req.request.responseType).toBe('blob');
      expect(req.request.params.get('format')).toBe('CSV');
      expect(req.request.params.get('year')).toBe(viewContext.anchorDate().slice(0, 4));
      expect(req.request.params.get('view')).toBe(viewContext.view());
      expect(req.request.params.get('lens')).toBe(viewContext.lens());

      req.flush(new Blob(['mes,ingresos']), {
        headers: { 'Content-Disposition': 'attachment; filename="resumen_2026.csv"' },
      });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(createObjectURL).toHaveBeenCalledTimes(1);
      expect(downloads).toEqual(['resumen_2026.csv']);
      expect(trigger.disabled).toBe(false);
    } finally {
      clickSpy.mockRestore();
    }
  });
});
