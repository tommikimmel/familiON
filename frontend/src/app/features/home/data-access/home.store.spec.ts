import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { DEMO_HOME_PAYLOAD, DEMO_SERIES } from './home.fixtures';
import { HomePayload } from './home.models';
import { HomeStore } from './home.store';

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

describe('HomeStore', () => {
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;
  let financeEvents: FinanceEvents;
  let notificationBadge: NotificationBadgeService;
  let bp: ReturnType<typeof createMockBreakpointObserver>;

  function setup(isDesktop = false): HomeStore {
    TestBed.resetTestingModule();
    localStorage.clear();
    bp = createMockBreakpointObserver(isDesktop);

    TestBed.configureTestingModule({
      providers: [
        HomeStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BreakpointObserver, useValue: bp.observer },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
    financeEvents = TestBed.inject(FinanceEvents);
    notificationBadge = TestBed.inject(NotificationBadgeService);

    return TestBed.inject(HomeStore);
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('builds the expected /bff/home query params and updates NotificationBadgeService', () => {
    const store = setup(false);
    TestBed.flushEffects();

    const expectedDate = viewContext.anchorDate();
    const req = httpMock.expectOne((r) => r.url === '/bff/home');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('view')).toBe('PERSONAL');
    expect(req.request.params.get('lens')).toBe('NOMINAL');
    expect(req.request.params.get('granularity')).toBe('MONTH');
    expect(req.request.params.get('date')).toBe(expectedDate);
    expect(store.loading()).toBe(true);

    httpMock.expectNone((r) => r.url === '/bff/api/analytics/series');

    req.flush({ ...DEMO_HOME_PAYLOAD, unreadNotifications: 5 });

    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.payload()?.summary).toEqual(DEMO_HOME_PAYLOAD.summary);
    expect(notificationBadge.unread()).toBe(5);
  });

  it('fetches 6-month series on desktop alongside /bff/home', () => {
    const store = setup(true);
    TestBed.flushEffects();

    const homeReq = httpMock.expectOne((r) => r.url === '/bff/home');
    const seriesReq = httpMock.expectOne((r) => r.url === '/bff/api/analytics/series');

    expect(seriesReq.request.params.get('view')).toBe('PERSONAL');
    expect(seriesReq.request.params.get('lens')).toBe('NOMINAL');
    expect(seriesReq.request.params.get('granularity')).toBe('MONTH');
    expect(seriesReq.request.params.get('periods')).toBe('6');
    expect(seriesReq.request.params.get('date')).toBe(viewContext.anchorDate());

    homeReq.flush(DEMO_HOME_PAYLOAD);
    seriesReq.flush(DEMO_SERIES);

    expect(store.series()).toEqual(DEMO_SERIES);
    expect(store.seriesLoading()).toBe(false);
  });

  it('cancels in-flight requests via switchMap when ViewContextStore changes and reloads on FinanceEvents', () => {
    const store = setup(false);
    TestBed.flushEffects();

    const firstReq = httpMock.expectOne((r) => r.url === '/bff/home');
    expect(firstReq.request.params.get('view')).toBe('PERSONAL');

    // Change view while first request is still in-flight
    viewContext.setView('FAMILY');
    TestBed.flushEffects();

    expect(firstReq.cancelled).toBe(true);

    const secondReq = httpMock.expectOne((r) => r.url === '/bff/home');
    expect(secondReq.request.params.get('view')).toBe('FAMILY');
    secondReq.flush(DEMO_HOME_PAYLOAD);

    // Emit a finance event -> triggers reload
    financeEvents.emit('transaction-created');
    TestBed.flushEffects();

    const thirdReq = httpMock.expectOne((r) => r.url === '/bff/home');
    expect(thirdReq.request.params.get('view')).toBe('FAMILY');
    thirdReq.flush(DEMO_HOME_PAYLOAD);

    expect(store.loading()).toBe(false);
  });

  it('handles partial nulls without crashing and preserves notification badge when unreadNotifications is null', () => {
    const store = setup(false);
    notificationBadge.set(2);
    TestBed.flushEffects();

    const partialPayload: HomePayload = {
      summary: DEMO_HOME_PAYLOAD.summary,
      byCategory: null,
      byPerson: null,
      recentTransactions: null,
      latestRates: null,
      unreadNotifications: null,
    };

    httpMock.expectOne((r) => r.url === '/bff/home').flush(partialPayload);

    expect(store.error()).toBeNull();
    expect(store.payload()).toEqual(partialPayload);
    expect(notificationBadge.unread()).toBe(2);
  });

  it('sets error state on HTTP failure and supports retry via reload()', () => {
    const store = setup(false);
    TestBed.flushEffects();

    const failedReq = httpMock.expectOne((r) => r.url === '/bff/home');
    failedReq.flush(
      { status: 503, detail: 'Servicio temporalmente no disponible' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(store.loading()).toBe(false);
    expect(store.payload()).toBeNull();
    expect(store.error()).toBe('Servicio temporalmente no disponible');

    store.reload();
    TestBed.flushEffects();

    const retryReq = httpMock.expectOne((r) => r.url === '/bff/home');
    retryReq.flush(DEMO_HOME_PAYLOAD);

    expect(store.error()).toBeNull();
    expect(store.payload()).toEqual(DEMO_HOME_PAYLOAD);
  });

  it('serves fixtures without HTTP calls when demoMode is enabled', () => {
    const store = setup(true);
    store.setDemoMode(true);
    TestBed.flushEffects();

    httpMock.expectNone((r) => r.url === '/bff/home');
    httpMock.expectNone((r) => r.url === '/bff/api/analytics/series');

    expect(store.payload()?.summary).not.toBeNull();
    expect(store.series()?.points).toHaveLength(6);
    expect(store.error()).toBeNull();
    expect(notificationBadge.unread()).toBe(3);
  });
});
