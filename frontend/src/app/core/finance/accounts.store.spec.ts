import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Me } from '../api/models/me.model';
import { SessionStore } from '../state/session.store';
import { ViewContextStore } from '../state/view-context.store';
import { AccountsStore } from './accounts.store';
import { Account, AccountCreate } from './finance.models';

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

function buildAccount(overrides: Partial<Account> = {}): Account {
  return {
    id: 'acc-1',
    name: 'Efectivo',
    accountType: 'CASH',
    currencyCode: 'ARS',
    initialBalance: '10000.0000',
    currentBalance: '15000.0000',
    ownerUserId: 'user-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
    ...overrides,
  };
}

describe('AccountsStore', () => {
  let store: AccountsStore;
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let viewContext: ViewContextStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    session = TestBed.inject(SessionStore);
    session.setMe(buildMe());
    viewContext = TestBed.inject(ViewContextStore);
    store = TestBed.inject(AccountsStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads accounts for the requested or active view and tracks loading state', async () => {
    viewContext.setView('FAMILY');
    const sample = [
      buildAccount({ id: 'acc-1', name: 'Efectivo' }),
      buildAccount({ id: 'acc-2', name: 'Galicia', accountType: 'BANK', ownerUserId: null }),
    ];

    const loadPromise = store.load();
    expect(store.loading()).toBe(true);

    const req = httpMock.expectOne('/bff/api/accounts?view=FAMILY');
    expect(req.request.method).toBe('GET');
    req.flush(sample);

    await loadPromise;

    expect(store.loading()).toBe(false);
    expect(store.accounts()).toEqual(sample);

    const personalLoad = store.load('PERSONAL');
    const personalReq = httpMock.expectOne('/bff/api/accounts?view=PERSONAL');
    personalReq.flush([sample[0]]);
    await personalLoad;

    expect(store.accounts()).toEqual([sample[0]]);
  });

  it('creates, updates, and removes accounts through the BFF endpoints', async () => {
    const payload: AccountCreate = {
      name: 'Mercado Pago',
      accountType: 'WALLET',
      currencyCode: 'ARS',
      initialBalance: '5000.0000',
      ownerUserId: 'user-1',
    };
    const created = buildAccount({
      id: 'acc-mp',
      name: 'Mercado Pago',
      accountType: 'WALLET',
      initialBalance: '5000.0000',
      currentBalance: '5000.0000',
    });

    const createPromise = store.create(payload);
    const createReq = httpMock.expectOne('/bff/api/accounts');
    expect(createReq.request.method).toBe('POST');
    expect(createReq.request.body).toEqual(payload);
    createReq.flush(created);

    expect(await createPromise).toEqual(created);
    expect(store.accounts()).toEqual([created]);

    const renamed = { ...created, name: 'MP Principal' };
    const updatePromise = store.update('acc-mp', { name: 'MP Principal' });
    const updateReq = httpMock.expectOne('/bff/api/accounts/acc-mp');
    expect(updateReq.request.method).toBe('PATCH');
    expect(updateReq.request.body).toEqual({ name: 'MP Principal' });
    updateReq.flush(renamed);

    expect(await updatePromise).toEqual(renamed);
    expect(store.accounts()[0].name).toBe('MP Principal');

    const removePromise = store.remove('acc-mp');
    const removeReq = httpMock.expectOne('/bff/api/accounts/acc-mp');
    expect(removeReq.request.method).toBe('DELETE');
    removeReq.flush(null, { status: 204, statusText: 'No Content' });

    await removePromise;
    expect(store.accounts()).toEqual([]);
  });

  it('returns active credit cards via creditCards signal', async () => {
    const loadPromise = store.load('FAMILY');
    httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([
      buildAccount({ id: 'acc-cash', accountType: 'CASH' }),
      buildAccount({
        id: 'acc-visa',
        name: 'Visa',
        accountType: 'CREDIT_CARD',
        creditLimit: '500000.0000',
        closingDay: 20,
        dueDay: 5,
      }),
      buildAccount({
        id: 'acc-old-card',
        name: 'Master Vieja',
        accountType: 'CREDIT_CARD',
        isActive: false,
      }),
    ]);
    await loadPromise;

    expect(store.creditCards().map((a) => a.id)).toEqual(['acc-visa']);
  });

  it('resolves defaultAccountFor using localStorage override or first non-card usable account', async () => {
    const loadPromise = store.load('FAMILY');
    httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([
      buildAccount({
        id: 'acc-visa',
        name: 'Visa ARS',
        accountType: 'CREDIT_CARD',
        currencyCode: 'ARS',
        creditLimit: '400000.0000',
        closingDay: 18,
        dueDay: 2,
      }),
      buildAccount({
        id: 'acc-other-member',
        name: 'Efectivo Lucas',
        accountType: 'CASH',
        currencyCode: 'ARS',
        ownerUserId: 'user-2',
      }),
      buildAccount({
        id: 'acc-shared-bank',
        name: 'Banco Compartido',
        accountType: 'BANK',
        currencyCode: 'ARS',
        ownerUserId: null,
      }),
      buildAccount({
        id: 'acc-own-wallet',
        name: 'Billetera Ana',
        accountType: 'WALLET',
        currencyCode: 'ARS',
        ownerUserId: 'user-1',
      }),
      buildAccount({
        id: 'acc-usd-card',
        name: 'Visa USD',
        accountType: 'CREDIT_CARD',
        currencyCode: 'USD',
      }),
    ]);
    await loadPromise;

    // Usable excludes other member's private account.
    expect(store.usableFor('ARS')().map((a) => a.id)).toEqual([
      'acc-visa',
      'acc-shared-bank',
      'acc-own-wallet',
    ]);

    // Fallback picks first non-credit-card usable account in ARS.
    expect(store.defaultAccountFor('ARS')?.id).toBe('acc-shared-bank');

    // Only credit cards exist for USD -> fallback returns null.
    expect(store.defaultAccountFor('USD')).toBeNull();

    // User override stored in localStorage takes precedence.
    store.setDefaultAccount('ARS', 'acc-own-wallet');
    expect(localStorage.getItem('fo.defaultAccount.ARS')).toBe('acc-own-wallet');
    expect(store.defaultAccountFor('ARS')?.id).toBe('acc-own-wallet');

    // Stale override pointing to nonexistent/inaccessible account falls back cleanly.
    localStorage.setItem('fo.defaultAccount.ARS', 'acc-other-member');
    expect(store.defaultAccountFor('ARS')?.id).toBe('acc-shared-bank');
  });
});
