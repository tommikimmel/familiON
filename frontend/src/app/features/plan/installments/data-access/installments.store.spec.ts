import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { ViewContextStore } from '../../../../core/state/view-context.store';
import {
  InstallmentCommitment,
  InstallmentPlan,
  InstallmentPlanDetail,
} from './installments.models';
import { InstallmentsStore } from './installments.store';

function buildPlan(overrides: Partial<InstallmentPlan> = {}): InstallmentPlan {
  return {
    id: 'plan-1',
    description: 'Notebook ThinkPad',
    category: { id: 'cat-1', name: 'Tecnología', icon: 'laptop', colorHex: '#0F766E' },
    creditCardAccount: { id: 'card-1', name: 'Visa Galicia' },
    currencyCode: 'ARS',
    totalInstallments: 6,
    paidInstallments: 2,
    hasInterest: false,
    cashPrice: null,
    financedTotal: '600000.0000',
    installmentAmount: '100000.0000',
    surchargeAmount: null,
    surchargePct: null,
    purchaseDate: '2026-09-10',
    firstBillingDate: '2026-10-10',
    lastBillingDate: '2027-03-10',
    remainingAmount: '400000.0000',
    realRemainingAmount: '362500.0000',
    nextInstallment: { number: 3, date: '2026-12-10', amount: '100000.0000' },
    status: 'ACTIVE',
    attributedTo: { id: 'user-1', fullName: 'Ana García' },
    ...overrides,
  };
}

function buildDetail(overrides: Partial<InstallmentPlanDetail> = {}): InstallmentPlanDetail {
  return {
    ...buildPlan(),
    installments: [
      { number: 1, date: '2026-10-10', amount: '100000.0000', status: 'POSTED', transactionId: 'tx-1' },
      { number: 2, date: '2026-11-10', amount: '100000.0000', status: 'POSTED', transactionId: 'tx-2' },
      { number: 3, date: '2026-12-10', amount: '100000.0000', status: 'PROJECTED', transactionId: 'tx-3' },
    ],
    ...overrides,
  };
}

describe('InstallmentsStore', () => {
  let store: InstallmentsStore;
  let httpMock: HttpTestingController;
  let financeEvents: FinanceEvents;
  let viewContext: ViewContextStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    store = TestBed.inject(InstallmentsStore);
    httpMock = TestBed.inject(HttpTestingController);
    financeEvents = TestBed.inject(FinanceEvents);
    viewContext = TestBed.inject(ViewContextStore);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads plans and 12-month commitment and computes pending totals by currency', async () => {
    const loadPromise = store.load('PERSONAL', 'ACTIVE');

    const listReq = httpMock.expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE');
    const commitReq = httpMock.expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL');

    const plans = [
      buildPlan({ id: 'plan-1', remainingAmount: '400000.0000', currencyCode: 'ARS' }),
      buildPlan({ id: 'plan-2', remainingAmount: '150000.0000', currencyCode: 'ARS' }),
    ];
    const commitment: InstallmentCommitment[] = [
      { month: '2026-12', amount: '150000.0000', currencyCode: 'ARS' },
      { month: '2027-01', amount: '150000.0000', currencyCode: 'ARS' },
    ];

    listReq.flush(plans);
    commitReq.flush(commitment);

    await loadPromise;

    expect(store.plans()).toEqual(plans);
    expect(store.commitment()).toEqual(commitment);
    expect(store.pendingTotals()).toEqual([{ currencyCode: 'ARS', amount: 550000 }]);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('switches status tab without losing cached active pending totals', async () => {
    const initialLoad = store.load('PERSONAL', 'ACTIVE');
    httpMock
      .expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE')
      .flush([buildPlan({ remainingAmount: '300000.0000' })]);
    httpMock
      .expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL')
      .flush([{ month: '2026-12', amount: '100000.0000', currencyCode: 'ARS' }]);
    await initialLoad;

    const statusPromise = store.setStatus('COMPLETED', 'PERSONAL');
    const completedPlan = buildPlan({ id: 'plan-done', status: 'COMPLETED', remainingAmount: '0.0000' });
    httpMock
      .expectOne('/bff/api/installment-plans?view=PERSONAL&status=COMPLETED')
      .flush([completedPlan]);
    await statusPromise;

    expect(store.status()).toBe('COMPLETED');
    expect(store.plans()).toEqual([completedPlan]);
    expect(store.pendingTotals()).toEqual([{ currencyCode: 'ARS', amount: 300000 }]);
  });

  it('loads plan detail and clears detail state', async () => {
    const detail = buildDetail();
    const detailPromise = store.loadDetail('plan-1');

    const req = httpMock.expectOne('/bff/api/installment-plans/plan-1');
    expect(req.request.method).toBe('GET');
    req.flush(detail);

    await detailPromise;

    expect(store.selectedDetail()).toEqual(detail);
    expect(store.detailLoading()).toBe(false);
    expect(store.detailError()).toBeNull();

    store.clearDetail();
    expect(store.selectedDetail()).toBeNull();
  });

  it('cancels or prepays remaining installments, reloads state, and emits plan-changed', async () => {
    viewContext.setView('FAMILY');

    const cancelPromise = store.cancelRemaining('plan-1', {
      mode: 'PREPAY',
      prepayDate: '2026-11-24',
    });

    const postReq = httpMock.expectOne('/bff/api/installment-plans/plan-1/cancel-remaining');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ mode: 'PREPAY', prepayDate: '2026-11-24' });

    const cancelledPlan = buildPlan({ status: 'COMPLETED', remainingAmount: '0.0000' });
    postReq.flush(cancelledPlan);

    await Promise.resolve();

    httpMock.expectOne('/bff/api/installment-plans?view=FAMILY&status=ACTIVE').flush([]);
    httpMock.expectOne('/bff/api/installment-plans/commitment?months=12&view=FAMILY').flush([]);
    httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([]);

    const result = await cancelPromise;

    expect(result).toEqual(cancelledPlan);
    expect(store.plans()).toEqual([]);
    expect(store.pendingTotals()).toEqual([]);
    expect(financeEvents.last()?.type).toBe('plan-changed');
    expect(store.mutating()).toBe(false);
  });

  it('captures problem detail error messages when loading fails', async () => {
    const loadPromise = store.load('PERSONAL', 'ACTIVE');

    httpMock
      .expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE')
      .flush({ detail: 'No se pudo consultar el servicio de cuotas.' }, { status: 500, statusText: 'Error' });
    httpMock
      .expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL')
      .flush([]);

    await loadPromise;

    expect(store.error()).toBe('No se pudo consultar el servicio de cuotas.');
    expect(store.loading()).toBe(false);
  });
});
