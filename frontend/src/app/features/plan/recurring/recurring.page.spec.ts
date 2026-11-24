import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Me } from '../../../core/api/models/me.model';
import { Account, Category } from '../../../core/finance/finance.models';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../shared/ui';
import { RecurringExpense } from './data-access/recurring.models';
import { RecurringPage } from './recurring.page';
import { currentYearMonth } from './utils/recurring-format';

function buildMe(role: 'ADULTO' | 'HIJO'): Me {
  return {
    id: 'user-1',
    email: 'ana@example.com',
    fullName: 'Ana García',
    role,
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'SYSTEM',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'USD_MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
  };
}

function buildExpense(overrides: Partial<RecurringExpense> = {}): RecurringExpense {
  const ym = currentYearMonth();
  return {
    id: 'rec-1',
    name: 'Internet',
    category: { id: 'cat-1', name: 'Servicios', icon: 'plug-zap', colorHex: '#0F766E' },
    account: { id: 'acc-1', name: 'Banco Galicia' },
    scope: 'FAMILY',
    baseAmount: '40000.0000',
    currencyCode: 'ARS',
    dueDay: 10,
    adjustmentRule: 'IPC',
    startDate: '2026-01-01',
    endDate: null,
    isActive: true,
    month: {
      month: ym,
      dueDate: `${ym}-10`,
      estimatedAmount: '42000.0000',
      status: 'OVERDUE',
      paidTransactionId: null,
      paidAmount: null,
      paidDate: null,
    },
    ...overrides,
  };
}

const ACCOUNTS: Account[] = [
  {
    id: 'acc-1',
    name: 'Banco Galicia',
    accountType: 'BANK',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '300000.0000',
    ownerUserId: 'user-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

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

describe('RecurringPage', () => {
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let toast: ToastService;
  let financeEvents: FinanceEvents;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    toast = TestBed.inject(ToastService);
    financeEvents = TestBed.inject(FinanceEvents);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('confirms a recurring payment in 2 taps, emits transaction-created, and shows toast', async () => {
    session.setMe(buildMe('ADULTO'));
    const fixture = TestBed.createComponent(RecurringPage);
    fixture.detectChanges();

    const ym = currentYearMonth();
    const expense = buildExpense();
    httpMock.expectOne(`/bff/api/recurring-expenses?month=${ym}`).flush([expense]);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);
    httpMock.expectOne('/bff/api/categories').flush(CATEGORIES);

    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="new-recurring-btn"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="recurring-status-badge"]')?.textContent).toContain(
      'Vencido',
    );

    // Tap 1: open pay sheet
    (host.querySelector('[data-testid="recurring-pay-btn"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const amountInput = host.querySelector('[data-testid="pay-amount-input"]') as HTMLInputElement;
    expect(amountInput.value).toBe('42000');

    // Tap 2: confirm payment
    const form = host.querySelector('fo-confirm-payment-sheet form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));

    const postReq = httpMock.expectOne('/bff/api/recurring-expenses/rec-1/confirm-payment');
    expect(postReq.request.body).toMatchObject({
      month: ym,
      amount: '42000',
      accountId: 'acc-1',
    });

    postReq.flush(
      buildExpense({
        month: {
          ...expense.month,
          status: 'PAID',
          paidTransactionId: 'tx-10',
          paidAmount: '42000.0000',
          paidDate: `${ym}-10`,
        },
      }),
    );

    await Promise.resolve();
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);

    await fixture.whenStable();
    fixture.detectChanges();

    expect(financeEvents.last()?.type).toBe('transaction-created');
    expect(toast.current()?.message).toContain('Internet');
    expect(host.querySelector('[data-testid="recurring-status-badge"]')?.textContent).toContain(
      'Pagado',
    );
  });

  it('hides adult CRUD buttons for HIJO role', async () => {
    session.setMe(buildMe('HIJO'));
    const fixture = TestBed.createComponent(RecurringPage);
    fixture.detectChanges();

    const ym = currentYearMonth();
    httpMock.expectOne(`/bff/api/recurring-expenses?month=${ym}`).flush([buildExpense()]);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);
    httpMock.expectOne('/bff/api/categories').flush(CATEGORIES);

    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="new-recurring-btn"]')).toBeNull();
    expect(host.querySelector('[data-testid="recurring-edit-btn"]')).toBeNull();
    expect(host.querySelector('[data-testid="recurring-archive-btn"]')).toBeNull();
    expect(host.querySelector('[data-testid="recurring-pay-btn"]')).not.toBeNull();
  });
});
