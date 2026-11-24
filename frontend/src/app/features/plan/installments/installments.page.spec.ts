import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { ToastService } from '../../../shared/ui';
import {
  InstallmentCommitment,
  InstallmentPlan,
  InstallmentPlanDetail,
} from './data-access/installments.models';
import { InstallmentsPage } from './installments.page';

function buildPlan(overrides: Partial<InstallmentPlan> = {}): InstallmentPlan {
  return {
    id: 'plan-1',
    description: 'Bicicleta',
    category: { id: 'cat-1', name: 'Deportes', icon: 'star', colorHex: '#0F766E' },
    creditCardAccount: { id: 'card-1', name: 'Visa Galicia' },
    currencyCode: 'ARS',
    totalInstallments: 6,
    paidInstallments: 2,
    hasInterest: true,
    cashPrice: '400000.0000',
    financedTotal: '472000.0000',
    installmentAmount: '78666.6667',
    surchargeAmount: '72000.0000',
    surchargePct: '18.0000',
    purchaseDate: '2026-09-10',
    firstBillingDate: '2026-10-10',
    lastBillingDate: '2027-03-10',
    remainingAmount: '314666.6668',
    realRemainingAmount: '290000.0000',
    nextInstallment: { number: 3, date: '2026-12-10', amount: '78666.6667' },
    status: 'ACTIVE',
    attributedTo: { id: 'user-1', fullName: 'Ana García' },
    ...overrides,
  };
}

function buildDetail(): InstallmentPlanDetail {
  return {
    ...buildPlan(),
    installments: [
      { number: 1, date: '2026-10-10', amount: '78666.6667', status: 'POSTED', transactionId: 'tx-1' },
      { number: 2, date: '2026-11-10', amount: '78666.6667', status: 'POSTED', transactionId: 'tx-2' },
      { number: 3, date: '2026-12-10', amount: '78666.6667', status: 'PROJECTED', transactionId: 'tx-3' },
    ],
  };
}

async function settle(fixture: ComponentFixture<InstallmentsPage>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('InstallmentsPage', () => {
  let httpMock: HttpTestingController;
  let toast: ToastService;
  let financeEvents: FinanceEvents;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    financeEvents = TestBed.inject(FinanceEvents);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads commitment and active plans, opens detail sheet, and cancels remaining installments', async () => {
    const fixture = TestBed.createComponent(InstallmentsPage);
    fixture.detectChanges();

    const commitment: InstallmentCommitment[] = [
      { month: '2026-12', amount: '78666.6667', currencyCode: 'ARS' },
    ];
    httpMock
      .expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE')
      .flush([buildPlan()]);
    httpMock
      .expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL')
      .flush(commitment);

    await settle(fixture);

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Lo que ya está comprometido');
    expect(host.textContent).toContain('Bicicleta');

    const cardBtn = host.querySelector('[data-testid="installment-plan-card"]') as HTMLButtonElement;
    cardBtn.click();
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/installment-plans/plan-1').flush(buildDetail());
    await settle(fixture);

    expect(host.querySelectorAll('[data-testid="schedule-item"]').length).toBe(3);

    const startCancel = host.querySelector(
      '[data-testid="start-cancel-remaining-btn"]',
    ) as HTMLButtonElement;
    startCancel.click();
    fixture.detectChanges();

    const confirmCancel = host.querySelector(
      '[data-testid="confirm-cancel-remaining-btn"]',
    ) as HTMLButtonElement;
    confirmCancel.click();

    const cancelReq = httpMock.expectOne('/bff/api/installment-plans/plan-1/cancel-remaining');
    expect(cancelReq.request.body).toEqual({ mode: 'CANCEL' });
    cancelReq.flush(buildPlan({ status: 'CANCELLED', remainingAmount: '0.0000' }));

    await Promise.resolve();
    httpMock.expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE').flush([]);
    httpMock.expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL').flush([]);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush([]);

    await settle(fixture);

    expect(financeEvents.last()?.type).toBe('plan-changed');
    expect(toast.current()?.message).toBe('Cuotas restantes canceladas.');
  });

  it('prepays remaining installments with selected date and emits plan-changed', async () => {
    const fixture = TestBed.createComponent(InstallmentsPage);
    fixture.detectChanges();

    httpMock
      .expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE')
      .flush([buildPlan()]);
    httpMock
      .expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL')
      .flush([{ month: '2026-12', amount: '78666.6667', currencyCode: 'ARS' }]);

    await settle(fixture);

    const host = fixture.nativeElement as HTMLElement;
    (host.querySelector('[data-testid="installment-plan-card"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/installment-plans/plan-1').flush(buildDetail());
    await settle(fixture);

    (host.querySelector('[data-testid="start-prepay-btn"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const dateInput = host.querySelector('[data-testid="prepay-date-input"]') as HTMLInputElement;
    dateInput.value = '2026-11-28';
    dateInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (host.querySelector('[data-testid="confirm-prepay-btn"]') as HTMLButtonElement).click();

    const prepayReq = httpMock.expectOne('/bff/api/installment-plans/plan-1/cancel-remaining');
    expect(prepayReq.request.body).toEqual({ mode: 'PREPAY', prepayDate: '2026-11-28' });
    prepayReq.flush(buildPlan({ status: 'COMPLETED', remainingAmount: '0.0000' }));

    await Promise.resolve();
    httpMock.expectOne('/bff/api/installment-plans?view=PERSONAL&status=ACTIVE').flush([]);
    httpMock.expectOne('/bff/api/installment-plans/commitment?months=12&view=PERSONAL').flush([]);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush([]);

    await settle(fixture);

    expect(financeEvents.last()?.type).toBe('plan-changed');
    expect(toast.current()?.message).toBe('Plan precancelado.');
  });
});
