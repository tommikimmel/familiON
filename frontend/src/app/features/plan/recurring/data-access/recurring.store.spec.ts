import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { RecurringExpense } from './recurring.models';
import { RecurringStore } from './recurring.store';

function buildExpense(overrides: Partial<RecurringExpense> = {}): RecurringExpense {
  return {
    id: 'rec-1',
    name: 'Internet Fibra',
    category: { id: 'cat-1', name: 'Servicios', icon: 'plug-zap', colorHex: '#0F766E' },
    account: { id: 'acc-1', name: 'Banco Galicia ARS' },
    scope: 'FAMILY',
    baseAmount: '35000.0000',
    currencyCode: 'ARS',
    dueDay: 10,
    adjustmentRule: 'IPC',
    startDate: '2026-01-01',
    endDate: null,
    isActive: true,
    month: {
      month: '2026-11',
      dueDate: '2026-11-10',
      estimatedAmount: '38000.0000',
      status: 'PENDING',
      paidTransactionId: null,
      paidAmount: null,
      paidDate: null,
    },
    ...overrides,
  };
}

describe('RecurringStore', () => {
  let store: RecurringStore;
  let httpMock: HttpTestingController;
  let financeEvents: FinanceEvents;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    store = TestBed.inject(RecurringStore);
    httpMock = TestBed.inject(HttpTestingController);
    financeEvents = TestBed.inject(FinanceEvents);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads expenses for a month, hides NOT_APPLICABLE, sorts by dueDate, and computes summaries', async () => {
    const loadPromise = store.load('2026-11');

    const req = httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11');
    expect(req.request.method).toBe('GET');

    const laterPending = buildExpense({
      id: 'rec-late',
      name: 'Expensas',
      dueDay: 15,
      month: {
        month: '2026-11',
        dueDate: '2026-11-15',
        estimatedAmount: '120000.0000',
        status: 'PENDING',
        paidTransactionId: null,
        paidAmount: null,
        paidDate: null,
      },
    });
    const earlyPaid = buildExpense({
      id: 'rec-paid',
      name: 'Alquiler',
      dueDay: 5,
      month: {
        month: '2026-11',
        dueDate: '2026-11-05',
        estimatedAmount: '500000.0000',
        status: 'PAID',
        paidTransactionId: 'tx-1',
        paidAmount: '510000.0000',
        paidDate: '2026-11-04',
      },
    });
    const notApplicable = buildExpense({
      id: 'rec-na',
      name: 'Club verano',
      dueDay: 20,
      month: {
        month: '2026-11',
        dueDate: '2026-11-20',
        estimatedAmount: '45000.0000',
        status: 'NOT_APPLICABLE',
        paidTransactionId: null,
        paidAmount: null,
        paidDate: null,
      },
    });

    req.flush([laterPending, notApplicable, earlyPaid]);
    await loadPromise;

    expect(store.visibleItems().map((item) => item.id)).toEqual(['rec-paid', 'rec-late']);
    expect(store.paidCount()).toBe(1);
    expect(store.summaryByCurrency()).toEqual([
      {
        currencyCode: 'ARS',
        estimatedTotal: 620000,
        paidTotal: 510000,
        pendingTotal: 120000,
      },
    ]);
  });

  it('shifts month forward and backward', async () => {
    const first = store.load('2026-11');
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([]);
    await first;

    const next = store.shiftMonth(1);
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-12').flush([]);
    await next;
    expect(store.month()).toBe('2026-12');

    const prev = store.shiftMonth(-1);
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([]);
    await prev;
    expect(store.month()).toBe('2026-11');
  });

  it('confirms payment, updates item status, reloads accounts, and emits transaction-created', async () => {
    const initial = store.load('2026-11');
    const pending = buildExpense();
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([pending]);
    await initial;

    const confirmPromise = store.confirmPayment('rec-1', {
      month: '2026-11',
      amount: '39500',
      accountId: 'acc-1',
      paymentDate: '2026-11-10',
    });

    const postReq = httpMock.expectOne('/bff/api/recurring-expenses/rec-1/confirm-payment');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({
      month: '2026-11',
      amount: '39500',
      accountId: 'acc-1',
      paymentDate: '2026-11-10',
    });

    const paid = buildExpense({
      month: {
        month: '2026-11',
        dueDate: '2026-11-10',
        estimatedAmount: '38000.0000',
        status: 'PAID',
        paidTransactionId: 'tx-99',
        paidAmount: '39500.0000',
        paidDate: '2026-11-10',
      },
    });
    postReq.flush(paid);

    await Promise.resolve();
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush([]);

    const result = await confirmPromise;
    expect(result.month.status).toBe('PAID');
    expect(store.visibleItems()[0].month.status).toBe('PAID');
    expect(financeEvents.last()?.type).toBe('transaction-created');
  });

  it('creates, updates, and archives recurring expenses and emits plan-changed', async () => {
    const loadPromise = store.load('2026-11');
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([]);
    await loadPromise;

    const created = buildExpense({ id: 'rec-new', name: 'Luz' });
    const createPromise = store.create({
      name: 'Luz',
      categoryId: 'cat-1',
      scope: 'FAMILY',
      baseAmount: '25000',
      currencyCode: 'ARS',
      dueDay: 12,
      adjustmentRule: 'MANUAL',
      startDate: '2026-11-01',
    });

    const postReq = httpMock.expectOne('/bff/api/recurring-expenses');
    expect(postReq.request.method).toBe('POST');
    postReq.flush(created);

    await Promise.resolve();
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([created]);
    await createPromise;

    expect(store.visibleItems()).toEqual([created]);
    expect(financeEvents.last()?.type).toBe('plan-changed');

    const updatePromise = store.update('rec-new', { name: 'Electricidad' });
    const patchReq = httpMock.expectOne('/bff/api/recurring-expenses/rec-new');
    expect(patchReq.request.method).toBe('PATCH');
    const updated = buildExpense({ id: 'rec-new', name: 'Electricidad' });
    patchReq.flush(updated);

    await Promise.resolve();
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([updated]);
    await updatePromise;
    expect(store.visibleItems()[0].name).toBe('Electricidad');

    const archivePromise = store.archive('rec-new');
    const delReq = httpMock.expectOne('/bff/api/recurring-expenses/rec-new');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null, { status: 204, statusText: 'No Content' });

    await Promise.resolve();
    httpMock.expectOne('/bff/api/recurring-expenses?month=2026-11').flush([]);
    await archivePromise;
    expect(store.visibleItems()).toEqual([]);
    expect(financeEvents.last()?.type).toBe('plan-changed');
  });
});
