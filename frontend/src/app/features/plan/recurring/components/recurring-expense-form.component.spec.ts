import { TestBed } from '@angular/core/testing';
import { Account, Category } from '../../../../core/finance/finance.models';
import {
  RecurringExpense,
  RecurringExpenseCreate,
  RecurringExpensePatch,
} from '../data-access/recurring.models';
import { RecurringExpenseFormComponent } from './recurring-expense-form.component';

const CATEGORIES: Category[] = [
  {
    id: 'cat-1',
    parentCategoryId: null,
    name: 'Servicios',
    kind: 'EXPENSE',
    icon: 'plug-zap',
    colorHex: '#0F766E',
    isSystem: false,
    systemKey: null,
    isActive: true,
  },
];

const ACCOUNTS: Account[] = [
  {
    id: 'acc-ars',
    name: 'Banco Galicia ARS',
    accountType: 'BANK',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '500000.0000',
    ownerUserId: 'user-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

function setInput(el: HTMLInputElement, value: string): void {
  el.value = value;
  el.dispatchEvent(new Event('input'));
}

function setSelect(el: HTMLSelectElement, value: string): void {
  el.value = value;
  el.dispatchEvent(new Event('change'));
}

describe('RecurringExpenseFormComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('shows validation errors for due day outside 1–31 and amount <= 0 and blocks submission', () => {
    const fixture = TestBed.createComponent(RecurringExpenseFormComponent);
    fixture.componentRef.setInput('categories', CATEGORIES);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.detectChanges();

    const created: RecurringExpenseCreate[] = [];
    fixture.componentInstance.createSubmitted.subscribe((payload) => created.push(payload));

    const host = fixture.nativeElement as HTMLElement;
    const nameInput = host.querySelector('[data-testid="recurring-name-input"]') as HTMLInputElement;
    const catSelect = host.querySelector(
      '[data-testid="recurring-category-select"]',
    ) as HTMLSelectElement;
    const amountInput = host.querySelector(
      '[data-testid="recurring-amount-input"]',
    ) as HTMLInputElement;
    const dueDayInput = host.querySelector(
      '[data-testid="recurring-due-day-input"]',
    ) as HTMLInputElement;

    setInput(nameInput, 'Alquiler');
    setSelect(catSelect, 'cat-1');
    setInput(amountInput, '0');
    setInput(dueDayInput, '32');
    fixture.detectChanges();

    const form = host.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(created).toHaveLength(0);
    expect(host.querySelector('[data-testid="recurring-amount-error"]')?.textContent).toContain(
      'mayor a 0',
    );
    expect(host.querySelector('[data-testid="recurring-due-day-error"]')?.textContent).toContain(
      'entre 1 y 31',
    );

    // Also reject dueDay = 0 and negative amounts
    setInput(dueDayInput, '0');
    setInput(amountInput, '-150');
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(created).toHaveLength(0);
    expect(host.querySelector('[data-testid="recurring-due-day-error"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="recurring-amount-error"]')).not.toBeNull();
  });

  it('displays adjustment rule explanations and emits valid create payload', () => {
    const fixture = TestBed.createComponent(RecurringExpenseFormComponent);
    fixture.componentRef.setInput('categories', CATEGORIES);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Monto fijo');
    expect(host.textContent).toContain('Se ajusta por inflación');
    expect(host.textContent).toContain('Está en dólares/UVA');
    expect(host.textContent).toContain('Usa lo último que pagaste + inflación');

    const created: RecurringExpenseCreate[] = [];
    fixture.componentInstance.createSubmitted.subscribe((payload) => created.push(payload));

    setInput(host.querySelector('[data-testid="recurring-name-input"]') as HTMLInputElement, 'Internet');
    setSelect(host.querySelector('[data-testid="recurring-category-select"]') as HTMLSelectElement, 'cat-1');
    setInput(host.querySelector('[data-testid="recurring-amount-input"]') as HTMLInputElement, '42500,50');
    setSelect(host.querySelector('[data-testid="recurring-account-select"]') as HTMLSelectElement, 'acc-ars');
    setInput(host.querySelector('[data-testid="recurring-due-day-input"]') as HTMLInputElement, '15');
    setInput(
      host.querySelector('[data-testid="recurring-start-date-input"]') as HTMLInputElement,
      '2026-11-01',
    );

    const ipcRadio = host.querySelector('input[type="radio"][value="IPC"]') as HTMLInputElement;
    ipcRadio.click();
    fixture.detectChanges();

    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(created).toHaveLength(1);
    expect(created[0]).toEqual({
      name: 'Internet',
      categoryId: 'cat-1',
      accountId: 'acc-ars',
      scope: 'FAMILY',
      baseAmount: '42500.50',
      currencyCode: 'ARS',
      dueDay: 15,
      adjustmentRule: 'IPC',
      startDate: '2026-11-01',
    });
  });

  it('emits patch payload in edit mode and rejects endDate earlier than startDate', () => {
    const existing: RecurringExpense = {
      id: 'rec-1',
      name: 'Gimnasio',
      category: { id: 'cat-1', name: 'Servicios', icon: 'plug-zap', colorHex: '#0F766E' },
      account: { id: 'acc-ars', name: 'Banco Galicia ARS' },
      scope: 'PERSONAL',
      baseAmount: '30000.0000',
      currencyCode: 'ARS',
      dueDay: 5,
      adjustmentRule: 'MANUAL',
      startDate: '2026-06-01',
      endDate: null,
      isActive: true,
      month: {
        month: '2026-11',
        dueDate: '2026-11-05',
        estimatedAmount: '30000.0000',
        status: 'PENDING',
        paidTransactionId: null,
        paidAmount: null,
        paidDate: null,
      },
    };

    const fixture = TestBed.createComponent(RecurringExpenseFormComponent);
    fixture.componentRef.setInput('expense', existing);
    fixture.componentRef.setInput('categories', CATEGORIES);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.detectChanges();

    const patches: RecurringExpensePatch[] = [];
    fixture.componentInstance.updateSubmitted.subscribe((patch) => patches.push(patch));

    const host = fixture.nativeElement as HTMLElement;
    const endDateInput = host.querySelector(
      '[data-testid="recurring-end-date-input"]',
    ) as HTMLInputElement;
    setInput(endDateInput, '2026-05-01');
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(patches).toHaveLength(0);
    expect(host.querySelector('[data-testid="recurring-end-date-error"]')).not.toBeNull();

    setInput(endDateInput, '2026-12-31');
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(patches).toHaveLength(1);
    expect(patches[0].endDate).toBe('2026-12-31');
    expect(patches[0].dueDay).toBe(5);
  });
});
