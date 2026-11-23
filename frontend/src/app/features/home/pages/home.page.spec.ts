import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { HomePayload } from '../data-access/home.models';
import { HomePage } from './home.page';

function createMockBreakpointObserver(initialMatches: boolean) {
  const subject = new BehaviorSubject<BreakpointState>({
    matches: initialMatches,
    breakpoints: { '(min-width: 1024px)': initialMatches },
  });

  const observer: Partial<BreakpointObserver> = {
    observe: () => subject.asObservable(),
    isMatched: () => subject.value.matches,
  };

  return {
    observer,
    setDesktop(matches: boolean): void {
      subject.next({
        matches,
        breakpoints: { '(min-width: 1024px)': matches },
      });
    },
  };
}

function createMockActivatedRoute(demo: string | null) {
  const paramMap = convertToParamMap(demo ? { demo } : {});
  const queryParamMap$ = new BehaviorSubject(paramMap);
  return {
    snapshot: { queryParamMap: paramMap },
    queryParamMap: queryParamMap$.asObservable(),
  };
}

describe('HomePage', () => {
  let httpMock: HttpTestingController;

  afterEach(() => {
    httpMock?.verify();
    localStorage.clear();
  });

  it('renders full mobile and desktop dashboard with fixtures when ?demo=1 is active', () => {
    TestBed.resetTestingModule();
    localStorage.clear();
    const bp = createMockBreakpointObserver(false);

    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BreakpointObserver, useValue: bp.observer },
        { provide: ActivatedRoute, useValue: createMockActivatedRoute('1') },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    const viewContext = TestBed.inject(ViewContextStore);
    viewContext.setView('FAMILY');

    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    // No BFF calls in demo mode
    httpMock.expectNone((r) => r.url === '/bff/home');
    httpMock.expectNone((r) => r.url === '/bff/api/analytics/series');

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="home-demo-banner"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="home-mobile-layout"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="home-rate-pill"]')?.textContent).toContain(
      'Dólar MEP',
    );
    expect(host.querySelector('fo-hero-balance')).not.toBeNull();
    expect(host.querySelector('fo-breakdown-card')).not.toBeNull();
    expect(host.querySelector('fo-recent-list')).not.toBeNull();

    // Switch to desktop breakpoint
    bp.setDesktop(true);
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="home-desktop-layout"]')).not.toBeNull();
    expect(host.querySelectorAll('fo-kpi-card')).toHaveLength(3);
    expect(host.querySelector('fo-evolution-chart')).not.toBeNull();
    expect(host.querySelector('fo-movements-table')).not.toBeNull();
    expect(
      host.querySelector('[data-testid="desktop-installment-commitment"]')?.textContent,
    ).toContain('Comprometido en cuotas: 18% del ingreso');
  });

  it('shows error state with Retry button when /bff/home fails and reloads when clicked', () => {
    TestBed.resetTestingModule();
    localStorage.clear();
    const bp = createMockBreakpointObserver(false);

    TestBed.configureTestingModule({
      imports: [HomePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BreakpointObserver, useValue: bp.observer },
        { provide: ActivatedRoute, useValue: createMockActivatedRoute(null) },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="home-mobile-skeleton"]')).not.toBeNull();

    httpMock
      .expectOne((r) => r.url === '/bff/home')
      .flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="home-error-state"]')).not.toBeNull();
    const retryBtn = host.querySelector(
      '[data-testid="home-retry-btn"] button',
    ) as HTMLButtonElement;
    retryBtn.click();
    fixture.detectChanges();

    const emptyPayload: HomePayload = {
      summary: {
        period: {
          granularity: 'MONTH',
          from: '2026-11-01',
          to: '2026-11-30',
          label: 'Noviembre 2026',
        },
        view: 'PERSONAL',
        lens: 'NOMINAL',
        unit: 'ARS',
        income: '0.0000',
        expense: '0.0000',
        balance: '0.0000',
        savingsRatePct: null,
        installmentCommitmentPct: null,
        previous: { income: '0.0000', expense: '0.0000' },
        realVariationPct: null,
        lensApproximated: false,
      },
      byCategory: null,
      byPerson: null,
      recentTransactions: [],
      latestRates: [],
      unreadNotifications: 0,
    };

    httpMock.expectOne((r) => r.url === '/bff/home').flush(emptyPayload);
    fixture.detectChanges();

    const emptyState = host.querySelector('[data-testid="home-empty-state"]');
    expect(emptyState?.textContent).toContain(
      'Todavía no hay movimientos en este período. Toca + para cargar el primero.',
    );
  });
});
