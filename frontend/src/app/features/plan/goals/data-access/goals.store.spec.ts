import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { GoalsStore } from './goals.store';
import { Contribution, SavingsGoal, SavingsGoalCreate } from './goals.models';

const SAMPLE_GOAL: SavingsGoal = {
  id: 'goal-1',
  name: 'Vacaciones familiares',
  scope: 'FAMILY',
  owner: { id: 'u-1', fullName: 'Papá' },
  targetAmount: '1000',
  targetUnit: 'USD',
  targetDate: '2026-12-31',
  status: 'ACTIVE',
  accumulatedInUnit: '250',
  progressPct: '25.0',
  remainingInUnit: '750',
  monthsLeft: 10,
  suggestedMonthlyInUnit: '75',
  contributionsCount: 2,
  lastContributionDate: '2026-02-15',
  accumulatedInBaseToday: '300000',
};

const SAMPLE_CONTRIBUTION: Contribution = {
  id: 'c-1',
  user: { id: 'u-1', fullName: 'Papá' },
  amount: '100',
  currencyCode: 'USD',
  amountInGoalUnit: '100',
  contributionDate: '2026-02-15',
  notes: 'Primer aporte',
  transactionId: null,
};

describe('GoalsStore', () => {
  let store: GoalsStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [GoalsStore, provideHttpClient(), provideHttpClientTesting()],
    });

    store = TestBed.inject(GoalsStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads savings goals with view and status params', async () => {
    const promise = store.load('FAMILY', 'ACTIVE');
    expect(store.loading()).toBe(true);

    const req = http.expectOne((r) => r.url === '/bff/api/savings-goals');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('view')).toBe('FAMILY');
    expect(req.request.params.get('status')).toBe('ACTIVE');

    req.flush([SAMPLE_GOAL]);
    await promise;

    expect(store.loading()).toBe(false);
    expect(store.goals().length).toBe(1);
    expect(store.goals()[0].id).toBe('goal-1');
    expect(store.activeGoals().length).toBe(1);
    expect(store.completedGoals().length).toBe(0);
  });

  it('creates a new savings goal and prepends it', async () => {
    const payload: SavingsGoalCreate = {
      name: 'Fondo de emergencia',
      scope: 'PERSONAL',
      targetAmount: '500000',
      targetUnit: 'ARS_IPC',
      targetDate: null,
    };

    const promise = store.create(payload);

    const req = http.expectOne('/bff/api/savings-goals');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);

    const created: SavingsGoal = {
      ...SAMPLE_GOAL,
      id: 'goal-2',
      name: payload.name,
      scope: 'PERSONAL',
      targetAmount: payload.targetAmount,
      targetUnit: 'ARS_IPC',
      accumulatedInUnit: '0',
      progressPct: '0',
    };
    req.flush(created);

    const result = await promise;
    expect(result.id).toBe('goal-2');
    expect(store.goals()[0].id).toBe('goal-2');
  });

  it('updates a savings goal', async () => {
    // Populate store
    const loadPromise = store.load('PERSONAL', 'ALL');
    http.expectOne((r) => r.url === '/bff/api/savings-goals').flush([SAMPLE_GOAL]);
    await loadPromise;

    const updatePromise = store.update('goal-1', { name: 'Viaje a Brasil' });
    const patchReq = http.expectOne('/bff/api/savings-goals/goal-1');
    expect(patchReq.request.method).toBe('PATCH');

    patchReq.flush({ ...SAMPLE_GOAL, name: 'Viaje a Brasil' });
    const updated = await updatePromise;

    expect(updated.name).toBe('Viaje a Brasil');
    expect(store.goals()[0].name).toBe('Viaje a Brasil');
  });

  it('adds contribution and reloads goals', async () => {
    const promise = store.addContribution('goal-1', {
      amount: '50',
      currencyCode: 'USD',
      notes: 'Aporte extra',
    });

    const postReq = http.expectOne('/bff/api/savings-goals/goal-1/contributions');
    expect(postReq.request.method).toBe('POST');
    postReq.flush(SAMPLE_CONTRIBUTION);

    await Promise.resolve();

    // Should trigger reload
    const reloadReq = http.expectOne((r) => r.url === '/bff/api/savings-goals');
    reloadReq.flush([{ ...SAMPLE_GOAL, accumulatedInUnit: '300' }]);

    const contribution = await promise;
    expect(contribution.id).toBe('c-1');
    expect(store.goals()[0].accumulatedInUnit).toBe('300');
  });

  it('deletes contribution and reloads goals', async () => {
    const promise = store.deleteContribution('goal-1', 'c-1');

    const delReq = http.expectOne('/bff/api/savings-goals/goal-1/contributions/c-1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null, { status: 204, statusText: 'No Content' });

    await Promise.resolve();

    // Should trigger reload
    const reloadReq = http.expectOne((r) => r.url === '/bff/api/savings-goals');
    reloadReq.flush([SAMPLE_GOAL]);

    await promise;
    expect(store.goals().length).toBe(1);
  });
});
