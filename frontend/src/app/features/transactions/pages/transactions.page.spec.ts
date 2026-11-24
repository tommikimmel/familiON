import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { FinanceEvents } from '../../../core/finance/finance-events';
import {
  TransactionItem,
  TransactionPage,
} from '../../../core/finance/transactions.models';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../shared/ui';
import { TransactionsPage } from './transactions.page';

function createDesktopBreakpointObserver(): Partial<BreakpointObserver> {
  const subject = new BehaviorSubject<BreakpointState>({
    matches: true,
    breakpoints: { '(min-width: 1024px)': true },
  });
  return {
    observe: () => subject.asObservable(),
    isMatched: () => true,
  };
}

function makeTx(overrides: Partial<TransactionItem> = {}): TransactionItem {
  return {
    id: overrides.id ?? 'tx-100',
    type: overrides.type ?? 'EXPENSE',
    scope: overrides.scope ?? 'PERSONAL',
    status: overrides.status ?? 'POSTED',
    amount: overrides.amount ?? '15000.0000',
    currencyCode: overrides.currencyCode ?? 'ARS',
    transactionDate: overrides.transactionDate ?? '2026-11-21',
    description: overrides.description ?? 'Almuerzo',
    notes: overrides.notes ?? null,
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
    lensAmount: overrides.lensAmount ?? '15000.0000',
    lensUnit: overrides.lensUnit ?? 'ARS',
    lensApproximated: overrides.lensApproximated ?? false,
  };
}

function makePage(items: TransactionItem[]): TransactionPage {
  return {
    content: items,
    page: {
      size: 30,
      number: 0,
      totalElements: items.length,
      totalPages: items.length > 0 ? 1 : 0,
    },
  };
}

describe('TransactionsPage', () => {
  let httpMock: HttpTestingController;
  let financeEvents: FinanceEvents;
  let toast: ToastService;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [TransactionsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BreakpointObserver, useValue: createDesktopBreakpointObserver() },
      ],
    }).compileComponents();

    const session = TestBed.inject(SessionStore);
    session.setMe({
      id: 'user-1',
      email: 'ana@familion.app',
      fullName: 'Ana Pérez',
      role: 'ADULTO',
      familyVisibility: 'FAMILY_FULL',
      themePreference: 'LIGHT',
      accentColor: '#0f766e',
      twoFactorEnabled: false,
      emailVerified: true,
      family: {
        id: 'fam-1',
        name: 'Pérez',
        baseCurrency: 'ARS',
        referenceCurrency: 'USD',
        referenceRateSource: 'USD_MEP',
        timezone: 'America/Argentina/Buenos_Aires',
        enabledCurrencies: ['ARS', 'USD'],
      },
    });

    httpMock = TestBed.inject(HttpTestingController);
    financeEvents = TestBed.inject(FinanceEvents);
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  function flushInitialRequests(items: TransactionItem[]) {
    httpMock.expectOne((r) => r.url === '/bff/api/categories').flush([]);
    httpMock.expectOne((r) => r.url === '/bff/api/accounts').flush([]);
    httpMock.expectOne((r) => r.url === '/bff/api/family/members').flush([
      { id: 'user-1', fullName: 'Ana Pérez', role: 'ADULTO', accentColor: '#0f766e', isActive: true },
    ]);
    httpMock.expectOne((r) => r.url === '/bff/api/transactions').flush(makePage(items));
  }

  it('sends PATCH with only changed fields and displays 409 problem detail on conflict', async () => {
    const tx = makeTx();
    const fixture = TestBed.createComponent(TransactionsPage);
    fixture.detectChanges();
    flushInitialRequests([tx]);
    fixture.detectChanges();

    // Click row to open detail sheet
    const row = fixture.nativeElement.querySelector(
      '[data-testid="tx-table-row-tx-100"]',
    ) as HTMLTableRowElement;
    row.click();
    fixture.detectChanges();

    // Detail requests reference currency snapshot
    const snapshotReq = httpMock.expectOne(
      (r) =>
        r.url === '/bff/api/transactions/tx-100' &&
        r.params.get('lens') === 'REFERENCE_CURRENCY',
    );
    snapshotReq.flush(
      makeTx({ lens: 'REFERENCE_CURRENCY', lensAmount: '12.5000', lensUnit: 'USD' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    // Enter edit mode
    const editBtn = fixture.nativeElement.querySelector(
      '[data-testid="detail-edit-btn"] button',
    ) as HTMLButtonElement;
    editBtn.click();
    fixture.detectChanges();

    // Modify only notes
    const notesEl = fixture.nativeElement.querySelector(
      '[data-testid="edit-notes-input"]',
    ) as HTMLTextAreaElement;
    notesEl.value = 'Con propina';
    notesEl.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // Submit PATCH -> respond with 409 Conflict problem detail
    const form = fixture.nativeElement.querySelector(
      '[data-testid="transaction-edit-form"]',
    ) as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const conflictReq = httpMock.expectOne(
      (r) => r.url === '/bff/api/transactions/tx-100' && r.method === 'PATCH',
    );
    expect(conflictReq.request.body).toEqual({ notes: 'Con propina' });
    conflictReq.flush(
      {
        status: 409,
        title: 'Conflict',
        detail: 'Este tipo de movimiento no permite ese cambio.',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    const errorBanner = fixture.nativeElement.querySelector(
      '[data-testid="detail-error-banner"]',
    ) as HTMLElement | null;
    expect(errorBanner?.textContent).toContain('Este tipo de movimiento no permite ese cambio.');

    // Retry submit -> 200 OK emits FinanceEvents + toast
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const okReq = httpMock.expectOne(
      (r) => r.url === '/bff/api/transactions/tx-100' && r.method === 'PATCH',
    );
    expect(okReq.request.body).toEqual({ notes: 'Con propina' });
    okReq.flush(makeTx({ notes: 'Con propina' }));

    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    expect(financeEvents.last()?.type).toBe('transaction-updated');
    expect(toast.current()?.message).toBe('Movimiento actualizado');

    // FinanceEvents reload request
    httpMock.expectOne((r) => r.url === '/bff/api/transactions').flush(makePage([makeTx({ notes: 'Con propina' })]));
  });

  it('deletes a transaction after confirmation, emits FinanceEvents and shows toast', async () => {
    const tx = makeTx();
    const fixture = TestBed.createComponent(TransactionsPage);
    fixture.detectChanges();
    flushInitialRequests([tx]);
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector(
      '[data-testid="tx-table-row-tx-100"]',
    ) as HTMLTableRowElement;
    row.click();
    fixture.detectChanges();

    httpMock
      .expectOne((r) => r.url === '/bff/api/transactions/tx-100')
      .flush(makeTx({ lens: 'REFERENCE_CURRENCY', lensAmount: '12.5000', lensUnit: 'USD' }));
    await Promise.resolve();
    fixture.detectChanges();

    // Open delete confirmation
    const deleteBtn = fixture.nativeElement.querySelector(
      '[data-testid="detail-delete-btn"] button',
    ) as HTMLButtonElement;
    deleteBtn.click();
    fixture.detectChanges();

    // Confirm delete
    const confirmBtn = fixture.nativeElement.querySelector(
      '[data-testid="confirm-delete-btn"] button',
    ) as HTMLButtonElement;
    confirmBtn.click();
    fixture.detectChanges();

    const delReq = httpMock.expectOne(
      (r) => r.url === '/bff/api/transactions/tx-100' && r.method === 'DELETE',
    );
    delReq.flush(null, { status: 204, statusText: 'No Content' });

    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();

    expect(financeEvents.last()?.type).toBe('transaction-deleted');
    expect(toast.current()?.message).toBe('Movimiento eliminado');

    httpMock.expectOne((r) => r.url === '/bff/api/transactions').flush(makePage([]));
  });
});
