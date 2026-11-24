import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Me } from '../../../core/api/models/me.model';
import { Account } from '../../../core/finance/finance.models';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { SessionStore } from '../../../core/state/session.store';
import { ToastService } from '../../../shared/ui';
import { CurrencyPosition, Exchange, IndexValue } from './data-access/exchanges.models';
import { ExchangesPage } from './exchanges.page';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'ana@familion.test',
    fullName: 'Ana García',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

const ACCOUNTS: Account[] = [
  {
    id: 'acc-ars',
    name: 'Galicia ARS',
    accountType: 'BANK',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '1500000.0000',
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
    currentBalance: '800.0000',
    ownerUserId: 'user-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

const POSITION: CurrencyPosition = {
  currencyCode: 'USD',
  units: '800.0000',
  weightedAvgRate: '1185.000000',
  totalCostBase: '948000.0000',
  currentRate: '1220.000000',
  currentValueBase: '976000.0000',
  unrealizedResultBase: '28000.0000',
  unrealizedResultPct: '2.9536',
};

const EXCHANGE: Exchange = {
  id: 'ex-1',
  exchangeDate: '2026-11-24',
  sourceAccount: { id: 'acc-ars', name: 'Galicia ARS' },
  targetAccount: { id: 'acc-usd', name: 'Caja USD' },
  sourceAmount: '948000.0000',
  sourceCurrency: 'ARS',
  targetAmount: '800.0000',
  targetCurrency: 'USD',
  appliedExchangeRate: '1185.000000',
  feeAmount: '0.0000',
  feeCurrency: null,
  notes: 'Ahorro',
  operation: 'BUY',
  createdBy: { id: 'user-1', fullName: 'Ana García' },
};

const INDICES: IndexValue[] = [
  { indexCode: 'USD_MEP', effectiveDate: '2026-11-24', value: '1190.000000', source: 'DOLARAPI' },
];

async function settle(fixture: ComponentFixture<ExchangesPage>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('ExchangesPage', () => {
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let financeEvents: FinanceEvents;
  let toast: ToastService;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    session = TestBed.inject(SessionStore);
    session.setMe(buildMe());
    financeEvents = TestBed.inject(FinanceEvents);
    toast = TestBed.inject(ToastService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads positions and history, and supports deleting an exchange with confirmation', async () => {
    const fixture = TestBed.createComponent(ExchangesPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL').flush([POSITION]);
    httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20').flush({
      content: [EXCHANGE],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    });
    httpMock.expectOne('/bff/api/economics/indices/latest').flush(INDICES);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);

    await settle(fixture);

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('USD 800');
    expect(host.textContent).toContain('Compraste a $1.185 promedio');
    expect(host.textContent).toContain('Ana García');

    const deleteBtn = host.querySelector('[data-testid="exchange-delete-btn"]') as HTMLButtonElement;
    deleteBtn.click();
    fixture.detectChanges();

    const confirmBtn = host.querySelector(
      '[data-testid="confirm-delete-exchange-btn"] button',
    ) as HTMLButtonElement;
    confirmBtn.click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/bff/api/currency-exchanges/ex-1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    await Promise.resolve();

    httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL').flush([]);
    httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20').flush({
      content: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    });
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);

    await settle(fixture);

    expect(financeEvents.last()?.type).toBe('plan-changed');
    expect(toast.current()?.message).toBe('Operación eliminada.');
    expect(host.querySelector('[data-testid="exchanges-empty-state"]')).not.toBeNull();
  });

  it('opens the new exchange sheet, submits a BUY, reloads positions, and shows toast', async () => {
    const fixture = TestBed.createComponent(ExchangesPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL').flush([]);
    httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20').flush({
      content: [],
      page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
    });
    httpMock.expectOne('/bff/api/economics/indices/latest').flush(INDICES);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);

    await settle(fixture);

    const host = fixture.nativeElement as HTMLElement;
    const openBtn = host.querySelector('[data-testid="new-exchange-btn"] button') as HTMLButtonElement;
    openBtn.click();
    fixture.detectChanges();

    const sourceInput = host.querySelector('[data-testid="exchange-source-amount"]') as HTMLInputElement;
    const targetInput = host.querySelector('[data-testid="exchange-target-amount"]') as HTMLInputElement;
    sourceInput.value = '948000';
    sourceInput.dispatchEvent(new Event('input'));
    targetInput.value = '800';
    targetInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form = host.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const postReq = httpMock.expectOne('/bff/api/currency-exchanges');
    expect(postReq.request.method).toBe('POST');
    postReq.flush(EXCHANGE);

    await Promise.resolve();

    httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL').flush([POSITION]);
    httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20').flush({
      content: [EXCHANGE],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    });
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);

    await settle(fixture);

    expect(financeEvents.last()?.type).toBe('plan-changed');
    expect(toast.current()?.message).toBe('Operación registrada.');
    expect(host.textContent).toContain('USD 800');
  });

  it('hides delete action for HIJO on exchanges created by another member', async () => {
    session.setMe(buildMe({ id: 'child-1', role: 'HIJO', fullName: 'Tomás García' }));

    const fixture = TestBed.createComponent(ExchangesPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL').flush([POSITION]);
    httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20').flush({
      content: [EXCHANGE],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    });
    httpMock.expectOne('/bff/api/economics/indices/latest').flush(INDICES);
    httpMock.expectOne('/bff/api/accounts?view=PERSONAL').flush(ACCOUNTS);

    await settle(fixture);

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="exchange-delete-btn"]')).toBeNull();
  });
});
