import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { AllowancesStore } from './allowances.store';
import { Allowance, AllowanceCreate, DisburseResult, FamilyMemberSummary } from './allowances.models';

const SAMPLE_ALLOWANCE: Allowance = {
  id: 'al-1',
  child: { id: 'child-1', fullName: 'Tomás García', accentColor: '#2563EB' },
  assignedBy: { id: 'user-1', fullName: 'Papá' },
  sourceAccount: { id: 'acc-1', name: 'Banco Galicia ARS' },
  targetAccount: { id: 'acc-2', name: 'Mercado Pago Tomás' },
  amount: '15000.0000',
  currencyCode: 'ARS',
  frequency: 'MONTHLY',
  dayOfPeriod: 1,
  adjustmentRule: 'IPC',
  isActive: true,
  currentAmount: '16500.0000',
  nextDisbursementDate: '2026-12-01',
  lastDisbursementDate: '2026-11-01',
  childBalance: '8200.0000',
};

const SAMPLE_MEMBER: FamilyMemberSummary = {
  id: 'child-1',
  fullName: 'Tomás García',
  role: 'HIJO',
  accentColor: '#2563EB',
};

describe('AllowancesStore', () => {
  let store: AllowancesStore;
  let http: HttpTestingController;
  let financeEvents: FinanceEvents;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [AllowancesStore, provideHttpClient(), provideHttpClientTesting()],
    });

    store = TestBed.inject(AllowancesStore);
    http = TestBed.inject(HttpTestingController);
    financeEvents = TestBed.inject(FinanceEvents);
  });

  afterEach(() => {
    http.verify();
  });

  it('loads allowances, family members, and family accounts in parallel', async () => {
    const promise = store.loadAll();
    expect(store.loading()).toBe(true);

    const reqAllowances = http.expectOne('/bff/api/family/allowances');
    const reqMembers = http.expectOne('/bff/api/family/members');
    const reqAccounts = http.expectOne('/bff/api/accounts?view=FAMILY');

    reqAllowances.flush([SAMPLE_ALLOWANCE]);
    reqMembers.flush([SAMPLE_MEMBER]);
    reqAccounts.flush([{ id: 'acc-2', name: 'Mercado Pago Tomás', currencyCode: 'ARS' }]);

    await promise;

    expect(store.loading()).toBe(false);
    expect(store.allowances().length).toBe(1);
    expect(store.allowances()[0].id).toBe('al-1');
    expect(store.children().length).toBe(1);
    expect(store.children()[0].fullName).toBe('Tomás García');
    expect(store.accounts().length).toBe(1);
  });

  it('creates an allowance', async () => {
    const payload: AllowanceCreate = {
      childUserId: 'child-1',
      targetAccountId: 'acc-2',
      amount: '20000',
      currencyCode: 'ARS',
      frequency: 'MONTHLY',
      dayOfPeriod: 10,
      adjustmentRule: 'MANUAL',
    };

    const promise = store.create(payload);

    const req = http.expectOne('/bff/api/family/allowances');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);

    const created: Allowance = {
      ...SAMPLE_ALLOWANCE,
      id: 'al-2',
      amount: payload.amount,
      currentAmount: payload.amount,
      frequency: payload.frequency,
      dayOfPeriod: payload.dayOfPeriod,
    };
    req.flush(created);

    const result = await promise;
    expect(result.id).toBe('al-2');
    expect(store.allowances().length).toBe(1);
    expect(store.allowances()[0].id).toBe('al-2');
  });

  it('updates an allowance', async () => {
    const loadPromise = store.loadAll();
    http.expectOne('/bff/api/family/allowances').flush([SAMPLE_ALLOWANCE]);
    http.expectOne('/bff/api/family/members').flush([SAMPLE_MEMBER]);
    http.expectOne('/bff/api/accounts?view=FAMILY').flush([]);
    await loadPromise;

    const patchPromise = store.update('al-1', { amount: '18000' });
    const req = http.expectOne('/bff/api/family/allowances/al-1');
    expect(req.request.method).toBe('PATCH');

    req.flush({ ...SAMPLE_ALLOWANCE, amount: '18000', currentAmount: '18000' });
    const updated = await patchPromise;

    expect(updated.amount).toBe('18000');
    expect(store.allowances()[0].amount).toBe('18000');
  });

  it('removes an allowance', async () => {
    const loadPromise = store.loadAll();
    http.expectOne('/bff/api/family/allowances').flush([SAMPLE_ALLOWANCE]);
    http.expectOne('/bff/api/family/members').flush([]);
    http.expectOne('/bff/api/accounts?view=FAMILY').flush([]);
    await loadPromise;

    const removePromise = store.remove('al-1');
    const req = http.expectOne('/bff/api/family/allowances/al-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    await removePromise;
    expect(store.allowances().length).toBe(0);
  });

  it('disburses an allowance, updates store, and emits transaction-created', async () => {
    const loadPromise = store.loadAll();
    http.expectOne('/bff/api/family/allowances').flush([SAMPLE_ALLOWANCE]);
    http.expectOne('/bff/api/family/members').flush([]);
    http.expectOne('/bff/api/accounts?view=FAMILY').flush([]);
    await loadPromise;

    const disbursePromise = store.disburse('al-1', { amount: '16500' });
    const req = http.expectOne('/bff/api/family/allowances/al-1/disburse');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: '16500' });

    const disburseResult: DisburseResult = {
      allowance: {
        ...SAMPLE_ALLOWANCE,
        childBalance: '24700.0000',
        lastDisbursementDate: '2026-11-24',
      },
      outTransactionId: 'tx-out-1',
      inTransactionId: 'tx-in-1',
    };
    req.flush(disburseResult);

    const result = await disbursePromise;
    expect(result.allowance.childBalance).toBe('24700.0000');
    expect(store.allowances()[0].childBalance).toBe('24700.0000');
    expect(financeEvents.last()?.type).toBe('transaction-created');
  });
});
