import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { ViewContextStore } from '../../../../core/state/view-context.store';
import { FULL_PLAN_FIXTURE, PARTIAL_NULL_PLAN_FIXTURE } from './plan-overview.fixtures';
import { PlanOverviewStore } from './plan-overview.store';

describe('PlanOverviewStore', () => {
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;
  let financeEvents: FinanceEvents;

  function setup(): PlanOverviewStore {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [PlanOverviewStore, provideHttpClient(), provideHttpClientTesting()],
    });

    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
    financeEvents = TestBed.inject(FinanceEvents);

    return TestBed.inject(PlanOverviewStore);
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('requests /bff/plan with view and yyyy-MM month from ViewContextStore', () => {
    const store = setup();
    TestBed.flushEffects();

    const expectedMonth = viewContext.anchorDate().slice(0, 7);
    const req = httpMock.expectOne((r) => r.url === '/bff/plan');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('view')).toBe('PERSONAL');
    expect(req.request.params.get('month')).toBe(expectedMonth);
    expect(store.loading()).toBe(true);

    req.flush(FULL_PLAN_FIXTURE);

    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.payload()).toEqual(FULL_PLAN_FIXTURE);
    expect(store.month()).toBe(expectedMonth);
  });

  it('cancels in-flight requests on ViewContextStore.view change and reloads on FinanceEvents', () => {
    const store = setup();
    TestBed.flushEffects();

    const firstReq = httpMock.expectOne((r) => r.url === '/bff/plan');
    expect(firstReq.request.params.get('view')).toBe('PERSONAL');

    viewContext.setView('FAMILY');
    TestBed.flushEffects();

    expect(firstReq.cancelled).toBe(true);

    const secondReq = httpMock.expectOne((r) => r.url === '/bff/plan');
    expect(secondReq.request.params.get('view')).toBe('FAMILY');
    secondReq.flush(FULL_PLAN_FIXTURE);

    financeEvents.emit('plan-changed');
    TestBed.flushEffects();

    const thirdReq = httpMock.expectOne((r) => r.url === '/bff/plan');
    expect(thirdReq.request.params.get('view')).toBe('FAMILY');
    thirdReq.flush(FULL_PLAN_FIXTURE);

    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('preserves partial null sections cleanly without entering global error state', () => {
    const store = setup();
    TestBed.flushEffects();

    const partial = {
      ...PARTIAL_NULL_PLAN_FIXTURE,
      goals: FULL_PLAN_FIXTURE.goals,
    };

    httpMock.expectOne((r) => r.url === '/bff/plan').flush(partial);

    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.payload()).toEqual(partial);
  });

  it('sets error message on HTTP failure and reloads on reload()', () => {
    const store = setup();
    TestBed.flushEffects();

    httpMock
      .expectOne((r) => r.url === '/bff/plan')
      .flush(
        { status: 503, detail: 'Planificación temporalmente no disponible' },
        { status: 503, statusText: 'Service Unavailable' },
      );

    expect(store.loading()).toBe(false);
    expect(store.payload()).toBeNull();
    expect(store.error()).toBe('Planificación temporalmente no disponible');

    store.reload();
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/plan').flush(FULL_PLAN_FIXTURE);

    expect(store.error()).toBeNull();
    expect(store.payload()).toEqual(FULL_PLAN_FIXTURE);
  });
});
