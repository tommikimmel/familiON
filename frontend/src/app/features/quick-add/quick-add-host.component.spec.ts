import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Me } from '../../core/api/models/me.model';
import { AccountsStore } from '../../core/finance/accounts.store';
import { CategoriesStore } from '../../core/finance/categories.store';
import { FinanceEvents } from '../../core/finance/finance-events';
import { Account, Category } from '../../core/finance/finance.models';
import { QuickAddLauncher } from '../../core/layout/quick-add-launcher.service';
import { SessionStore } from '../../core/state/session.store';
import { ToastService } from '../../shared/ui';
import { QuickAddFamilyMember } from './data-access/quick-add-members.api';
import { QuickAddHostComponent } from './quick-add-host.component';

function createMockBreakpointObserver(initialDesktop: boolean) {
  const subject = new BehaviorSubject<BreakpointState>({
    matches: initialDesktop,
    breakpoints: { '(min-width: 1024px)': initialDesktop },
  });

  const observer: Partial<BreakpointObserver> = {
    observe: () => subject.asObservable(),
    isMatched: () => subject.value.matches,
  };

  return { observer, subject };
}

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-adult',
    email: 'carlos@familion.test',
    fullName: 'Carlos López',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia López',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'USD_MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

const SAMPLE_ACCOUNTS: Account[] = [
  {
    id: 'acc-cash-ars',
    name: 'Efectivo',
    accountType: 'CASH',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '50000',
    ownerUserId: null,
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
  {
    id: 'acc-visa',
    name: 'Visa Galicia',
    accountType: 'CREDIT_CARD',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '0',
    ownerUserId: null,
    creditLimit: '500000',
    closingDay: 20,
    dueDay: 5,
    isActive: true,
  },
  {
    id: 'acc-bank-usd',
    name: 'Banco USD',
    accountType: 'BANK',
    currencyCode: 'USD',
    initialBalance: '0',
    currentBalance: '400',
    ownerUserId: null,
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

const SAMPLE_CATEGORIES: Category[] = [
  {
    id: 'cat-food',
    parentCategoryId: null,
    name: 'Supermercado',
    kind: 'EXPENSE',
    icon: 'shopping-cart',
    colorHex: '#0F766E',
    isSystem: true,
    systemKey: 'FOOD',
    isActive: true,
  },
  {
    id: 'cat-other-expense',
    parentCategoryId: null,
    name: 'Otros gastos',
    kind: 'EXPENSE',
    icon: 'circle-ellipsis',
    colorHex: '#64748B',
    isSystem: true,
    systemKey: 'OTHER_EXPENSE',
    isActive: true,
  },
  {
    id: 'cat-salary',
    parentCategoryId: null,
    name: 'Sueldo',
    kind: 'INCOME',
    icon: 'briefcase',
    colorHex: '#16A34A',
    isSystem: true,
    systemKey: 'SALARY',
    isActive: true,
  },
];

const SAMPLE_MEMBERS: QuickAddFamilyMember[] = [
  {
    id: 'user-adult',
    fullName: 'Carlos López',
    role: 'ADULTO',
    accentColor: '#0F766E',
    isActive: true,
  },
  {
    id: 'user-child',
    fullName: 'Sofía López',
    role: 'HIJO',
    accentColor: '#7C3AED',
    isActive: true,
  },
];

async function flushMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

describe('QuickAddHostComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  async function setup(options: { desktop?: boolean; me?: Me; accounts?: Account[] } = {}) {
    const bp = createMockBreakpointObserver(options.desktop ?? false);

    TestBed.configureTestingModule({
      imports: [QuickAddHostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BreakpointObserver, useValue: bp.observer },
      ],
    });

    const fixture = TestBed.createComponent(QuickAddHostComponent);
    const http = TestBed.inject(HttpTestingController);
    const session = TestBed.inject(SessionStore);
    const launcher = TestBed.inject(QuickAddLauncher);
    const categoriesStore = TestBed.inject(CategoriesStore);
    const accountsStore = TestBed.inject(AccountsStore);
    const financeEvents = TestBed.inject(FinanceEvents);
    const toast = TestBed.inject(ToastService);

    session.setMe(options.me ?? buildMe());
    fixture.detectChanges();

    launcher.open();
    fixture.detectChanges();

    const catsReq = http.expectOne('/bff/api/categories');
    catsReq.flush(SAMPLE_CATEGORIES);

    const accsReq = http.expectOne((req) => req.url === '/bff/api/accounts');
    accsReq.flush(options.accounts ?? SAMPLE_ACCOUNTS);

    if ((options.me ?? buildMe()).role === 'ADULTO') {
      const membersReq = http.expectOne('/bff/api/family/members');
      membersReq.flush(SAMPLE_MEMBERS);
    }

    await flushMicrotasks();
    fixture.detectChanges();

    return {
      fixture,
      http,
      session,
      launcher,
      categoriesStore,
      accountsStore,
      financeEvents,
      toast,
    };
  }

  it('completes a 3-tap mobile expense flow via keypad, category chip, and Guardar', async () => {
    const { fixture, http, launcher, categoriesStore, financeEvents, toast } = await setup({
      desktop: false,
    });
    const host = fixture.nativeElement as HTMLElement;

    const recordUsageSpy = vi.spyOn(categoriesStore, 'recordUsage');

    // Camera icon is disabled with "Próximamente"
    const cameraBtn = host.querySelector('[data-testid="quick-add-camera-btn"]') as HTMLButtonElement;
    expect(cameraBtn.disabled).toBe(true);
    expect(cameraBtn.title).toBe('Próximamente');

    // Save disabled initially
    const saveBtn = () =>
      host.querySelector('[data-testid="quick-add-save-btn"] button') as HTMLButtonElement;
    expect(saveBtn().disabled).toBe(true);

    // 1) Tap keypad: 1, 5, 000, ',', ',', 5, backspace -> "15000." -> toApiDecimal "15000"
    (host.querySelector('[data-testid="keypad-key-1"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="keypad-key-5"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="keypad-key-000"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="keypad-key-,"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="keypad-key-,"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="keypad-key-5"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const display = host.querySelector('[data-testid="quick-add-amount-display"]') as HTMLElement;
    expect(display.textContent?.trim()).toBe('15.000,5');

    // Backspace removes "5" and ","
    (host.querySelector('[data-testid="keypad-backspace"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="keypad-backspace"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(display.textContent?.trim()).toBe('15.000');

    // Still disabled without category or description
    expect(saveBtn().disabled).toBe(true);

    // 2) Tap category chip
    (host.querySelector('[data-testid="category-chip-cat-food"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(saveBtn().disabled).toBe(false);

    // 3) Tap Guardar
    saveBtn().click();
    fixture.detectChanges();

    const req = http.expectOne('/bff/api/transactions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      type: 'EXPENSE',
      amount: '15000',
      currencyCode: 'ARS',
      transactionDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      accountId: 'acc-cash-ars',
      categoryId: 'cat-food',
    });

    req.flush({ id: 'tx-1' });
    await flushMicrotasks();
    fixture.detectChanges();

    expect(toast.current()?.kind).toBe('success');
    expect(toast.current()?.message).toBe('Gasto guardado');
    expect(recordUsageSpy).toHaveBeenCalledWith('cat-food');
    expect(financeEvents.last()?.type).toBe('transaction-created');
    expect(launcher.isOpen()).toBe(false);

    http.verify();
  });

  it('requires choosing amountMode for 6 installments and posts to /bff/api/installment-plans', async () => {
    const { fixture, http, launcher, toast } = await setup({ desktop: true });
    const host = fixture.nativeElement as HTMLElement;

    const amountInput = host.querySelector(
      '[data-testid="quick-add-amount-input"]',
    ) as HTMLInputElement;
    expect(document.activeElement).toBe(amountInput);

    // Enter amount 36000 and description only (to also exercise OTHER_EXPENSE fallback)
    amountInput.value = '36000';
    amountInput.dispatchEvent(new Event('input'));

    const descInput = host.querySelector(
      '[data-testid="quick-add-description"]',
    ) as HTMLInputElement;
    descInput.value = 'Zapatillas';
    descInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // Open advanced options
    (host.querySelector('[data-testid="quick-add-more-options-btn"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    // Set fixed date so preview is deterministic
    const dateInput = host.querySelector('[data-testid="quick-add-date-input"]') as HTMLInputElement;
    dateInput.value = '2026-11-10';
    dateInput.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    // Increment installments from 1 to 6
    const incBtn = host.querySelector('[data-testid="installments-increment"]') as HTMLButtonElement;
    for (let i = 0; i < 5; i++) {
      incBtn.click();
      fixture.detectChanges();
    }

    expect(
      host.querySelector('[data-testid="installments-count"]')?.textContent?.trim(),
    ).toBe('6 cuotas');

    // Without choosing amountMode, Guardar MUST be disabled
    const saveBtn = host.querySelector(
      '[data-testid="quick-add-save-btn"] button',
    ) as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(true);

    // Select PER_INSTALLMENT ("Es cada cuota")
    (host.querySelector('[data-testid="amount-mode-PER_INSTALLMENT"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(saveBtn.disabled).toBe(false);

    // Live preview has aria-live="polite" and shows expected calculation
    const previewEl = host.querySelector('[data-testid="installment-preview"]') as HTMLElement;
    expect(previewEl.getAttribute('aria-live')).toBe('polite');
    expect(previewEl.textContent).toContain('6 cuotas de');
    expect(previewEl.textContent).toContain('36.000/mes');
    expect(previewEl.textContent).toContain('216.000');
    expect(previewEl.textContent).toContain('(Dic 2026 → May 2027)');

    // Select attribution to Sofia
    (host.querySelector('[data-testid="attribution-member-user-child"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    // Save
    saveBtn.click();
    fixture.detectChanges();

    const req = http.expectOne('/bff/api/installment-plans');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      description: 'Zapatillas',
      categoryId: 'cat-other-expense',
      creditCardAccountId: 'acc-visa',
      currencyCode: 'ARS',
      totalInstallments: 6,
      amountMode: 'PER_INSTALLMENT',
      amount: '36000',
      hasInterest: false,
      purchaseDate: '2026-11-10',
      attributedToUserId: 'user-child',
    });

    req.flush({ id: 'plan-1' });
    await flushMicrotasks();
    fixture.detectChanges();

    expect(toast.current()?.kind).toBe('success');
    expect(toast.current()?.message).toBe('Gasto guardado');
    expect(launcher.isOpen()).toBe(false);

    http.verify();
  });

  it('keeps sheet open and displays error toast when /bff/api/installment-plans returns 404', async () => {
    const { fixture, http, launcher, toast } = await setup({ desktop: true });
    const host = fixture.nativeElement as HTMLElement;

    const amountInput = host.querySelector(
      '[data-testid="quick-add-amount-input"]',
    ) as HTMLInputElement;
    amountInput.value = '12000';
    amountInput.dispatchEvent(new Event('input'));

    (host.querySelector('[data-testid="category-chip-cat-food"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="quick-add-more-options-btn"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    (host.querySelector('[data-testid="installments-increment"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    (host.querySelector('[data-testid="amount-mode-TOTAL"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    (host.querySelector('[data-testid="quick-add-save-btn"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const req = http.expectOne('/bff/api/installment-plans');
    req.flush(
      { status: 404, detail: 'Endpoint de cuotas aún no disponible' },
      { status: 404, statusText: 'Not Found' },
    );

    await flushMicrotasks();
    fixture.detectChanges();

    expect(toast.current()?.kind).toBe('error');
    expect(toast.current()?.message).toBe('Endpoint de cuotas aún no disponible');
    expect(launcher.isOpen()).toBe(true);
    expect(amountInput.value).toBe('12000');

    http.verify();
  });

  it('hides "Para:" attribution section for HIJO users', async () => {
    const { fixture, http } = await setup({
      desktop: true,
      me: buildMe({ id: 'user-child', role: 'HIJO', familyVisibility: 'OWN_ONLY' }),
    });
    const host = fixture.nativeElement as HTMLElement;

    (host.querySelector('[data-testid="quick-add-more-options-btn"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="quick-add-attribution-section"]')).toBeNull();
    http.verify();
  });

  it('shows notice with link to /accounts when selecting installments without credit cards', async () => {
    const accountsWithoutCard = SAMPLE_ACCOUNTS.filter((acc) => acc.accountType !== 'CREDIT_CARD');
    const { fixture, http } = await setup({
      desktop: true,
      accounts: accountsWithoutCard,
    });
    const host = fixture.nativeElement as HTMLElement;

    const amountInput = host.querySelector(
      '[data-testid="quick-add-amount-input"]',
    ) as HTMLInputElement;
    amountInput.value = '5000';
    amountInput.dispatchEvent(new Event('input'));
    (host.querySelector('[data-testid="category-chip-cat-food"]') as HTMLButtonElement).click();
    (host.querySelector('[data-testid="quick-add-more-options-btn"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    (host.querySelector('[data-testid="installments-increment"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    (host.querySelector('[data-testid="amount-mode-TOTAL"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="quick-add-no-cards-notice"]')).not.toBeNull();
    const link = host.querySelector('[data-testid="quick-add-no-cards-link"]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/accounts');

    const saveBtn = host.querySelector(
      '[data-testid="quick-add-save-btn"] button',
    ) as HTMLButtonElement;
    expect(saveBtn.disabled).toBe(true);

    http.verify();
  });

  it('supports Enter to save income with comma decimal and Esc to close', async () => {
    const { fixture, http, launcher, toast } = await setup({ desktop: true });
    const host = fixture.nativeElement as HTMLElement;

    // Switch to Ingreso
    const tabs = host.querySelectorAll<HTMLButtonElement>('fo-segmented-toggle [role="tab"]');
    tabs[1].click();
    fixture.detectChanges();

    // Enter amount with comma decimal
    const amountInput = host.querySelector(
      '[data-testid="quick-add-amount-input"]',
    ) as HTMLInputElement;
    amountInput.value = '250,75';
    amountInput.dispatchEvent(new Event('input'));

    const descInput = host.querySelector(
      '[data-testid="quick-add-description"]',
    ) as HTMLInputElement;
    descInput.value = 'Venta bicicleta';
    descInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // Press Enter on description input to save
    descInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();

    const req = http.expectOne('/bff/api/transactions');
    expect(req.request.body).toEqual({
      type: 'INCOME',
      amount: '250.75',
      currencyCode: 'ARS',
      transactionDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      accountId: 'acc-cash-ars',
      description: 'Venta bicicleta',
    });

    req.flush({ id: 'tx-2' });
    await flushMicrotasks();
    fixture.detectChanges();

    expect(toast.current()?.message).toBe('Ingreso guardado');
    expect(launcher.isOpen()).toBe(false);

    // Reopen and close with Escape
    launcher.open();
    fixture.detectChanges();
    expect(launcher.isOpen()).toBe(true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(launcher.isOpen()).toBe(false);

    http.verify();
  });
});
