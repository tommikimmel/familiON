import { TestBed } from '@angular/core/testing';
import { Account } from '../../../../core/finance/finance.models';
import { ExchangeCreate, IndexValue } from '../data-access/exchanges.models';
import { ExchangeFormComponent } from './exchange-form.component';

const ACCOUNTS: Account[] = [
  {
    id: 'acc-ars',
    name: 'Banco Galicia ARS',
    accountType: 'BANK',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '2000000.0000',
    ownerUserId: 'user-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
  {
    id: 'acc-usd',
    name: 'Caja USD',
    accountType: 'SAVINGS',
    currencyCode: 'USD',
    initialBalance: '0',
    currentBalance: '500.0000',
    ownerUserId: 'user-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

const INDICES: IndexValue[] = [
  { indexCode: 'USD_MEP', effectiveDate: '2026-11-24', value: '1190.000000', source: 'DOLARAPI' },
];

function setInputValue(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ExchangeFormComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('computes live rate and reference comparison on BUY and submits payload with fee and notes', () => {
    const fixture = TestBed.createComponent(ExchangeFormComponent);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.componentRef.setInput('latestIndices', INDICES);
    fixture.componentRef.setInput('preferredRateSource', 'MEP');
    fixture.detectChanges();

    const emitted: ExchangeCreate[] = [];
    fixture.componentInstance.submitted.subscribe((payload) => emitted.push(payload));

    const host = fixture.nativeElement as HTMLElement;
    const sourceInput = host.querySelector('[data-testid="exchange-source-amount"]') as HTMLInputElement;
    const targetInput = host.querySelector('[data-testid="exchange-target-amount"]') as HTMLInputElement;
    const feeInput = host.querySelector('[data-testid="exchange-fee-amount"]') as HTMLInputElement;
    const notesInput = host.querySelector('[data-testid="exchange-notes"]') as HTMLInputElement;

    setInputValue(sourceInput, '948000');
    setInputValue(targetInput, '800');
    setInputValue(feeInput, '1500');
    setInputValue(notesInput, 'Compra mensual');
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="exchange-live-rate"]')?.textContent?.trim()).toBe(
      '1 USD = $1.185,00',
    );
    expect(
      host.querySelector('[data-testid="exchange-reference-comparison"]')?.textContent?.trim(),
    ).toBe('MEP hoy: $1.190 · pagaste 0,4% menos');

    const form = host.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(emitted).toHaveLength(1);
    expect(emitted[0]).toMatchObject({
      sourceAccountId: 'acc-ars',
      targetAccountId: 'acc-usd',
      sourceAmount: '948000',
      targetAmount: '800',
      appliedExchangeRate: '1185',
      feeAmount: '1500',
      feeCurrency: 'ARS',
      notes: 'Compra mensual',
    });
  });

  it('recalculates target amount when rate is edited in SELL mode', () => {
    const fixture = TestBed.createComponent(ExchangeFormComponent);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.componentRef.setInput('latestIndices', INDICES);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const sellTab = Array.from(host.querySelectorAll('button[role="tab"]')).find((btn) =>
      btn.textContent?.includes('Vender'),
    ) as HTMLButtonElement;
    sellTab.click();
    fixture.detectChanges();

    const sourceSelect = host.querySelector('[data-testid="exchange-source-account"]') as HTMLSelectElement;
    const targetSelect = host.querySelector('[data-testid="exchange-target-account"]') as HTMLSelectElement;
    expect(sourceSelect.value).toBe('acc-usd');
    expect(targetSelect.value).toBe('acc-ars');

    const sourceInput = host.querySelector('[data-testid="exchange-source-amount"]') as HTMLInputElement;
    const rateInput = host.querySelector('[data-testid="exchange-rate-input"]') as HTMLInputElement;
    const targetInput = host.querySelector('[data-testid="exchange-target-amount"]') as HTMLInputElement;

    setInputValue(sourceInput, '200');
    setInputValue(rateInput, '1200');
    fixture.detectChanges();

    expect(targetInput.value).toBe('240000');
    expect(
      host.querySelector('[data-testid="exchange-reference-comparison"]')?.textContent?.trim(),
    ).toBe('MEP hoy: $1.190 · cobraste 0,8% más');
  });
});
