import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Account, Category } from '../../../core/finance/finance.models';
import {
  TransactionItem,
  TransactionPatch,
} from '../../../core/finance/transactions.models';
import { TransactionsFamilyMember } from '../data-access/transactions-members.api';
import { TransactionDetailSheetComponent } from './transaction-detail-sheet.component';

function makeTx(overrides: Partial<TransactionItem> = {}): TransactionItem {
  return {
    id: overrides.id ?? 'tx-1',
    type: overrides.type ?? 'EXPENSE',
    scope: overrides.scope ?? 'PERSONAL',
    status: overrides.status ?? 'POSTED',
    amount: overrides.amount ?? '25300.0000',
    currencyCode: overrides.currencyCode ?? 'ARS',
    transactionDate: overrides.transactionDate ?? '2026-11-21',
    description: overrides.description ?? 'Supermercado',
    notes: overrides.notes ?? 'Compra semanal',
    category: overrides.category ?? {
      id: 'cat-1',
      name: 'Comida',
      icon: 'shopping-cart',
      colorHex: '#0f766e',
    },
    account: overrides.account ?? {
      id: 'acc-1',
      name: 'Galicia ARS',
      accountType: 'BANK',
    },
    paidBy: overrides.paidBy ?? { id: 'user-1', fullName: 'Ana Pérez' },
    attributedTo: overrides.attributedTo ?? { id: 'user-1', fullName: 'Ana Pérez' },
    installment: overrides.installment ?? null,
    lens: overrides.lens ?? 'NOMINAL',
    lensAmount: overrides.lensAmount ?? '25300.0000',
    lensUnit: overrides.lensUnit ?? 'ARS',
    lensApproximated: overrides.lensApproximated ?? false,
  };
}

const CATEGORIES: Category[] = [
  {
    id: 'cat-1',
    parentCategoryId: null,
    name: 'Comida',
    kind: 'EXPENSE',
    icon: 'shopping-cart',
    colorHex: '#0f766e',
    isSystem: false,
    systemKey: null,
    isActive: true,
  },
  {
    id: 'cat-2',
    parentCategoryId: null,
    name: 'Transporte',
    kind: 'EXPENSE',
    icon: 'bus',
    colorHex: '#2563eb',
    isSystem: false,
    systemKey: null,
    isActive: true,
  },
];

const ACCOUNTS: Account[] = [
  {
    id: 'acc-1',
    name: 'Galicia ARS',
    accountType: 'BANK',
    currencyCode: 'ARS',
    initialBalance: '0.0000',
    currentBalance: '100000.0000',
    ownerUserId: null,
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

const MEMBERS: TransactionsFamilyMember[] = [
  { id: 'user-1', fullName: 'Ana Pérez', role: 'ADULTO', accentColor: '#0f766e', isActive: true },
  { id: 'user-2', fullName: 'Lucas Pérez', role: 'HIJO', accentColor: '#2563eb', isActive: true },
];

describe('TransactionDetailSheetComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionDetailSheetComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('displays the implicit reference currency snapshot sentence', () => {
    const fixture = TestBed.createComponent(TransactionDetailSheetComponent);
    const tx = makeTx();
    const snapshot = makeTx({
      lens: 'REFERENCE_CURRENCY',
      lensAmount: '25.3000',
      lensUnit: 'USD',
    });

    fixture.componentRef.setInput('transaction', tx);
    fixture.componentRef.setInput('referenceSnapshot', snapshot);
    fixture.componentRef.setInput('isAdult', true);
    fixture.componentRef.setInput('categories', CATEGORIES);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.componentRef.setInput('members', MEMBERS);
    fixture.detectChanges();

    const snapshotEl = fixture.nativeElement.querySelector(
      '[data-testid="reference-snapshot"]',
    ) as HTMLElement | null;
    expect(snapshotEl?.textContent).toContain('Equivalía a USD 25,30 ese día');
  });

  it('emits PATCH containing only changed fields and hides attribution for non-adults', () => {
    const fixture = TestBed.createComponent(TransactionDetailSheetComponent);
    const tx = makeTx();
    const emitted: { id: string; patch: TransactionPatch }[] = [];

    fixture.componentRef.setInput('transaction', tx);
    fixture.componentRef.setInput('isAdult', false);
    fixture.componentRef.setInput('currentUserId', 'user-1');
    fixture.componentRef.setInput('categories', CATEGORIES);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.componentRef.setInput('members', MEMBERS);
    fixture.componentInstance.savePatch.subscribe((payload) => emitted.push(payload));
    fixture.detectChanges();

    // Open edit mode
    const editBtn = fixture.nativeElement.querySelector(
      '[data-testid="detail-edit-btn"] button',
    ) as HTMLButtonElement;
    editBtn.click();
    fixture.detectChanges();

    // Attribution select ("Para:") must not be shown for non-adult
    expect(
      fixture.nativeElement.querySelector('[data-testid="edit-attribution-select"]'),
    ).toBeNull();

    // Change only description
    const descInput = fixture.nativeElement.querySelector(
      '[data-testid="edit-description-input"]',
    ) as HTMLInputElement;
    descInput.value = 'Supermercado mayorista';
    descInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector(
      '[data-testid="transaction-edit-form"]',
    ) as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(emitted).toEqual([
      {
        id: 'tx-1',
        patch: { description: 'Supermercado mayorista' },
      },
    ]);
  });

  it('disables amount and date for installments and shows Plan -> Cuotas link instead of delete', () => {
    const fixture = TestBed.createComponent(TransactionDetailSheetComponent);
    const installmentTx = makeTx({
      installment: { planId: 'plan-1', number: 2, total: 6 },
    });

    fixture.componentRef.setInput('transaction', installmentTx);
    fixture.componentRef.setInput('isAdult', true);
    fixture.componentRef.setInput('categories', CATEGORIES);
    fixture.componentRef.setInput('accounts', ACCOUNTS);
    fixture.componentRef.setInput('members', MEMBERS);
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector(
      '[data-testid="installment-delete-notice"]',
    ) as HTMLElement | null;
    expect(notice?.textContent).toContain('Las cuotas se gestionan desde Plan → Cuotas');
    expect(
      fixture.nativeElement.querySelector('[data-testid="installment-plan-link"]'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="detail-delete-btn"]')).toBeNull();

    // Enter edit mode
    const editBtn = fixture.nativeElement.querySelector(
      '[data-testid="detail-edit-btn"] button',
    ) as HTMLButtonElement;
    editBtn.click();
    fixture.detectChanges();

    const amountInput = fixture.nativeElement.querySelector(
      '[data-testid="edit-amount-input"]',
    ) as HTMLInputElement;
    const dateInput = fixture.nativeElement.querySelector(
      '[data-testid="edit-date-input"]',
    ) as HTMLInputElement;
    const restrictionNotice = fixture.nativeElement.querySelector(
      '[data-testid="edit-restriction-notice"]',
    ) as HTMLElement | null;

    expect(amountInput.disabled).toBe(true);
    expect(dateInput.disabled).toBe(true);
    expect(restrictionNotice?.textContent).toContain('cuota');
  });

  it('disables amount and date with explanation for currency exchange transactions', () => {
    const fixture = TestBed.createComponent(TransactionDetailSheetComponent);
    const exchangeTx = makeTx({
      type: 'EXCHANGE_OUT',
      category: null,
    });

    fixture.componentRef.setInput('transaction', exchangeTx);
    fixture.componentRef.setInput('isAdult', true);
    fixture.detectChanges();

    const editBtn = fixture.nativeElement.querySelector(
      '[data-testid="detail-edit-btn"] button',
    ) as HTMLButtonElement;
    editBtn.click();
    fixture.detectChanges();

    const amountInput = fixture.nativeElement.querySelector(
      '[data-testid="edit-amount-input"]',
    ) as HTMLInputElement;
    const dateInput = fixture.nativeElement.querySelector(
      '[data-testid="edit-date-input"]',
    ) as HTMLInputElement;

    expect(amountInput.disabled).toBe(true);
    expect(dateInput.disabled).toBe(true);
    expect(
      fixture.nativeElement.querySelector('[data-testid="edit-restriction-notice"]')?.textContent,
    ).toContain('divisa');
  });
});
