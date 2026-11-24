import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { ViewContextStore } from '../../../core/state/view-context.store';
import {
  AnalyticsSummary,
  CalendarView,
  CategoryBreakdown,
  PersonBreakdown,
  Series,
} from './analytics.models';
import { AnalyticsStore } from './analytics.store';

const MOCK_SUMMARY: AnalyticsSummary = {
  period: {
    granularity: 'MONTH',
    from: '2026-11-01',
    to: '2026-11-30',
    label: 'Noviembre 2026',
  },
  view: 'PERSONAL',
  lens: 'NOMINAL',
  unit: 'ARS',
  income: '500000.0000',
  expense: '350000.0000',
  balance: '150000.0000',
  savingsRatePct: '30.0000',
  installmentCommitmentPct: '18.0000',
  previous: {
    income: '480000.0000',
    expense: '368421.0526',
  },
  realVariationPct: '8.0000',
  lensApproximated: false,
};

const MOCK_SERIES: Series = {
  lens: 'NOMINAL',
  unit: 'ARS',
  granularity: 'MONTH',
  points: [
    {
      periodStart: '2026-10-01',
      label: 'Oct',
      income: '480000',
      expense: '360000',
      balance: '120000',
    },
    {
      periodStart: '2026-11-01',
      label: 'Nov',
      income: '500000',
      expense: '350000',
      balance: '150000',
    },
  ],
};

const MOCK_BY_CATEGORY: CategoryBreakdown = {
  period: MOCK_SUMMARY.period,
  lens: 'NOMINAL',
  unit: 'ARS',
  total: '350000.0000',
  slices: [
    {
      categoryId: 'cat-1',
      name: 'Supermercado',
      colorHex: '#0f766e',
      icon: 'shopping-cart',
      amount: '200000.0000',
      pct: '57.14',
      variationPct: '-4.5',
    },
  ],
};

const MOCK_BY_PERSON: PersonBreakdown = {
  period: MOCK_SUMMARY.period,
  lens: 'NOMINAL',
  unit: 'ARS',
  total: '350000.0000',
  slices: [
    {
      userId: 'u-1',
      fullName: 'Ana Pérez',
      accentColor: '#0f766e',
      amount: '210000.0000',
      pct: '60.0',
    },
  ],
};

const MOCK_CALENDAR: CalendarView = {
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
          title: 'Cuota 2/6',
          amount: '45000',
          currencyCode: 'ARS',
        },
      ],
    },
  ],
};

describe('AnalyticsStore', () => {
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;
  let financeEvents: FinanceEvents;

  function setup(): AnalyticsStore {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [AnalyticsStore, provideHttpClient(), provideHttpClientTesting()],
    });

    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
    financeEvents = TestBed.inject(FinanceEvents);

    return TestBed.inject(AnalyticsStore);
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('requests summary, series (12 periods) and by-category in PERSONAL view without by-person', () => {
    const store = setup();
    TestBed.flushEffects();

    const date = viewContext.anchorDate();

    const summaryReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary');
    const seriesReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/series');
    const categoryReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category');

    httpMock.expectNone((r) => r.url === '/bff/api/analytics/by-person');
    httpMock.expectNone((r) => r.url === '/bff/api/analytics/calendar');

    expect(summaryReq.request.params.get('view')).toBe('PERSONAL');
    expect(summaryReq.request.params.get('lens')).toBe('NOMINAL');
    expect(summaryReq.request.params.get('granularity')).toBe('MONTH');
    expect(summaryReq.request.params.get('date')).toBe(date);
    expect(seriesReq.request.params.get('periods')).toBe('12');

    expect(store.loading()).toBe(true);

    summaryReq.flush(MOCK_SUMMARY);
    seriesReq.flush(MOCK_SERIES);
    categoryReq.flush(MOCK_BY_CATEGORY);

    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.summary()).toEqual(MOCK_SUMMARY);
    expect(store.series()).toEqual(MOCK_SERIES);
    expect(store.byCategory()).toEqual(MOCK_BY_CATEGORY);
    expect(store.byPerson()).toBeNull();
    expect(store.showPersonSection()).toBe(false);
  });

  it('requests by-person in FAMILY view and hides section when by-person returns 403', () => {
    const store = setup();
    viewContext.setView('FAMILY');
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(MOCK_SUMMARY);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(MOCK_SERIES);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(MOCK_BY_CATEGORY);

    const personReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-person');
    expect(personReq.request.params.get('view')).toBe('FAMILY');
    personReq.flush(
      { status: 403, detail: 'Forbidden' },
      { status: 403, statusText: 'Forbidden' },
    );

    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.summary()).toEqual(MOCK_SUMMARY);
    expect(store.byPerson()).toBeNull();
    expect(store.personForbidden()).toBe(true);
    expect(store.showPersonSection()).toBe(false);
  });

  it('loads by-person in FAMILY view when permitted', () => {
    const store = setup();
    viewContext.setView('FAMILY');
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(MOCK_SUMMARY);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(MOCK_SERIES);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(MOCK_BY_CATEGORY);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-person').flush(MOCK_BY_PERSON);

    expect(store.personForbidden()).toBe(false);
    expect(store.showPersonSection()).toBe(true);
    expect(store.byPerson()).toEqual(MOCK_BY_PERSON);
  });

  it('reloads analytics when lens changes or FinanceEvents emits', () => {
    const store = setup();
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(MOCK_SUMMARY);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(MOCK_SERIES);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(MOCK_BY_CATEGORY);

    viewContext.setLens('CONSTANT_BASE');
    TestBed.flushEffects();

    const s2 = httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary');
    const ser2 = httpMock.expectOne((r) => r.url === '/bff/api/analytics/series');
    const cat2 = httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category');

    expect(s2.request.params.get('lens')).toBe('CONSTANT_BASE');
    expect(ser2.request.params.get('lens')).toBe('CONSTANT_BASE');
    expect(cat2.request.params.get('lens')).toBe('CONSTANT_BASE');

    s2.flush({ ...MOCK_SUMMARY, lens: 'CONSTANT_BASE', lensApproximated: true });
    ser2.flush({ ...MOCK_SERIES, lens: 'CONSTANT_BASE' });
    cat2.flush({ ...MOCK_BY_CATEGORY, lens: 'CONSTANT_BASE' });

    expect(store.summary()?.lensApproximated).toBe(true);

    financeEvents.emit('transaction-created');
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(MOCK_SUMMARY);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(MOCK_SERIES);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(MOCK_BY_CATEGORY);
  });

  it('loads calendar lazily on CALENDAR tab with independent month navigation', () => {
    const store = setup();
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/api/analytics/summary').flush(MOCK_SUMMARY);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/series').flush(MOCK_SERIES);
    httpMock.expectOne((r) => r.url === '/bff/api/analytics/by-category').flush(MOCK_BY_CATEGORY);

    store.setCalendarMonth(2026, 11);
    store.setActiveTab('CALENDAR');
    TestBed.flushEffects();

    const calReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/calendar');
    expect(calReq.request.params.get('view')).toBe('PERSONAL');
    expect(calReq.request.params.get('from')).toBe('2026-11-01');
    expect(calReq.request.params.get('to')).toBe('2026-11-30');
    calReq.flush(MOCK_CALENDAR);

    expect(store.calendar()).toEqual(MOCK_CALENDAR);
    expect(store.calendarLoading()).toBe(false);

    // Navigating calendar month does NOT trigger overview reload
    store.shiftCalendarMonth(1);
    TestBed.flushEffects();

    httpMock.expectNone((r) => r.url === '/bff/api/analytics/summary');
    const decReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/calendar');
    expect(decReq.request.params.get('from')).toBe('2026-12-01');
    expect(decReq.request.params.get('to')).toBe('2026-12-31');
    decReq.flush({ from: '2026-12-01', to: '2026-12-31', days: [] });
  });

  it('sets error state when all core analytics requests fail', () => {
    const store = setup();
    TestBed.flushEffects();

    httpMock
      .expectOne((r) => r.url === '/bff/api/analytics/summary')
      .flush({ detail: 'Servicio no disponible' }, { status: 503, statusText: 'Unavailable' });
    httpMock
      .expectOne((r) => r.url === '/bff/api/analytics/series')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    httpMock
      .expectOne((r) => r.url === '/bff/api/analytics/by-category')
      .flush({}, { status: 503, statusText: 'Unavailable' });

    expect(store.loading()).toBe(false);
    expect(store.error()).toBe('Servicio no disponible');
  });
});
