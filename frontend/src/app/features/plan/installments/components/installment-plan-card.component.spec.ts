import { TestBed } from '@angular/core/testing';
import { InstallmentPlan } from '../data-access/installments.models';
import { InstallmentPlanCardComponent } from './installment-plan-card.component';

function buildPlan(overrides: Partial<InstallmentPlan> = {}): InstallmentPlan {
  return {
    id: 'plan-1',
    description: 'Heladera Inverter',
    category: { id: 'cat-1', name: 'Hogar', icon: 'sofa', colorHex: '#0F766E' },
    creditCardAccount: { id: 'card-1', name: 'Visa Santander' },
    currencyCode: 'ARS',
    totalInstallments: 6,
    paidInstallments: 3,
    hasInterest: false,
    cashPrice: null,
    financedTotal: '600000.0000',
    installmentAmount: '100000.0000',
    surchargeAmount: null,
    surchargePct: null,
    purchaseDate: '2026-08-15',
    firstBillingDate: '2026-09-10',
    lastBillingDate: '2027-02-10',
    remainingAmount: '300000.0000',
    realRemainingAmount: '275000.0000',
    nextInstallment: { number: 4, date: '2026-12-10', amount: '100000.0000' },
    status: 'ACTIVE',
    attributedTo: { id: 'user-1', fullName: 'Ana García' },
    ...overrides,
  };
}

describe('InstallmentPlanCardComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders interest-free chip, progress, next installment, and real remaining liquation text', () => {
    const fixture = TestBed.createComponent(InstallmentPlanCardComponent);
    const plan = buildPlan();
    fixture.componentRef.setInput('plan', plan);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="plan-surcharge-chip"]')?.textContent?.trim()).toBe(
      'Sin interés',
    );
    expect(host.querySelector('[data-testid="plan-progress-label"]')?.textContent?.trim()).toBe(
      'Cuota 3 de 6',
    );
    expect(host.querySelector('[data-testid="plan-real-remaining"]')?.textContent).toContain(
      'en plata de hoy',
    );
    expect(host.querySelector('[data-testid="plan-liquation-note"]')?.textContent).toContain(
      'Las cuotas fijas se licúan con la inflación',
    );

    const emitted: InstallmentPlan[] = [];
    fixture.componentInstance.selected.subscribe((p) => emitted.push(p));
    (host.querySelector('[data-testid="installment-plan-card"]') as HTMLButtonElement).click();
    expect(emitted).toEqual([plan]);
  });

  it('renders surcharge percentage chip when the plan has interest', () => {
    const fixture = TestBed.createComponent(InstallmentPlanCardComponent);
    fixture.componentRef.setInput(
      'plan',
      buildPlan({
        hasInterest: true,
        cashPrice: '500000.0000',
        financedTotal: '590000.0000',
        surchargeAmount: '90000.0000',
        surchargePct: '18.0000',
      }),
    );
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="plan-surcharge-chip"]')?.textContent?.trim()).toBe(
      'Recargo 18%',
    );
  });

  it('hides liquation note when realRemainingAmount is null or plan is completed', () => {
    const fixture = TestBed.createComponent(InstallmentPlanCardComponent);
    fixture.componentRef.setInput(
      'plan',
      buildPlan({
        status: 'COMPLETED',
        paidInstallments: 6,
        remainingAmount: '0.0000',
        realRemainingAmount: '0.0000',
        nextInstallment: null,
      }),
    );
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="plan-real-remaining"]')).toBeNull();
    expect(host.querySelector('[data-testid="plan-liquation-note"]')).toBeNull();
  });
});
