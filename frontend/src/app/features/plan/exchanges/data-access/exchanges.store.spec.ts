import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { ViewContextStore } from '../../../../core/state/view-context.store';
import {
  CurrencyPosition,
  Exchange,
  ExchangePageResponse,
  IndexValue,
} from './exchanges.models';
import { ExchangesStore } from './exchanges.store';

function buildExchange(overrides: Partial<Exchange> = {}): Exchange {
  return {
    id: 'ex-1',
    exchangeDate: '2026-11-24',
    sourceAccount: { id: 'acc-ars', name: 'Banco Galicia ARS' },
    targetAccount: { id: 'acc-usd', name: 'Caja USD' },
    sourceAmount: '948000.0000',
    sourceCurrency: 'ARS',
    targetAmount: '800.0000',
    targetCurrency: 'USD',
    appliedExchangeRate: '1185.000000',
    feeAmount: '0.0000',
    feeCurrency: null,
    notes: 'Compra MEP',
    operation: 'BUY',
    createdBy: { id: 'user-1', fullName: 'Ana García' },
    ...overrides,
  };
}

function buildPosition(overrides: Partial<CurrencyPosition> = {}): CurrencyPosition {
  return {
    currencyCode: 'USD',
    units: '800.0000',
    weightedAvgRate: '1185.000000',
    totalCostBase: '948000.0000',
    currentRate: '1220.000000',
    currentValueBase: '976000.0000',
    unrealizedResultBase: '28000.0000',
    unrealizedResultPct: '2.9536',
    ...overrides,
  };
}

function buildPage(content: Exchange[], number = 0, totalElements = content.length, totalPages = 1): ExchangePageResponse {
  return {
    content,
    page: {
      size: 20,
      number,
      totalElements,
      totalPages,
    },
  };
}

describe('ExchangesStore', () => {
  let store: ExchangesStore;
  let httpMock: HttpTestingController;
  let financeEvents: FinanceEvents;
  let viewContext: ViewContextStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    store = TestBed.inject(ExchangesStore);
    httpMock = TestBed.inject(HttpTestingController);
    financeEvents = TestBed.inject(FinanceEvents);
    viewContext = TestBed.inject(ViewContextStore);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('loads positions, paginated history, and latest indices', async () => {
    const loadPromise = store.load('PERSONAL');

    const posReq = httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL');
    const listReq = httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20');
    const indicesReq = httpMock.expectOne('/bff/api/economics/indices/latest');

    const position = buildPosition();
    const exchange = buildExchange();
    const indices: IndexValue[] = [
      { indexCode: 'USD_MEP', effectiveDate: '2026-11-24', value: '1190.000000', source: 'DOLARAPI' },
    ];

    posReq.flush([position]);
    listReq.flush(buildPage([exchange]));
    indicesReq.flush(indices);

    await loadPromise;

    expect(store.positions()).toEqual([position]);
    expect(store.exchanges()).toEqual([exchange]);
    expect(store.latestIndices()).toEqual(indices);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('creates an exchange, reloads positions, history and accounts, and emits plan-changed', async () => {
    const createPromise = store.create({
      sourceAccountId: 'acc-ars',
      targetAccountId: 'acc-usd',
      sourceAmount: '948000',
      targetAmount: '800',
      appliedExchangeRate: '1185',
    });

    const postReq = httpMock.expectOne('/bff/api/currency-exchanges');
    expect(postReq.request.method).toBe('POST');
    const created = buildExchange();
    postReq.flush(created);

    await Promise.resolve();

    const posReq = httpMock.expectOne('/bff/api/currency-exchanges/positions?view=PERSONAL');
    const listReq = httpMock.expectOne('/bff/api/currency-exchanges?view=PERSONAL&page=0&size=20');
    const accountsReq = httpMock.expectOne('/bff/api/accounts?view=PERSONAL');

    const updatedPosition = buildPosition({ units: '1600.0000' });
    posReq.flush([updatedPosition]);
    listReq.flush(buildPage([created]));
    accountsReq.flush([]);

    const result = await createPromise;
    expect(result).toEqual(created);
    expect(store.positions()).toEqual([updatedPosition]);
    expect(store.exchanges()).toEqual([created]);
    expect(financeEvents.last()?.type).toBe('plan-changed');
  });

  it('deletes an exchange, reloads positions and history, and emits plan-changed', async () => {
    viewContext.setView('FAMILY');

    // Seed page 1 with a single item to verify stepping back to page 0 after deleting it.
    const pagePromise = store.loadPage(1, 'FAMILY');
    httpMock
      .expectOne('/bff/api/currency-exchanges?view=FAMILY&page=1&size=20')
      .flush(buildPage([buildExchange({ id: 'ex-21' })], 1, 21, 2));
    await pagePromise;

    const removePromise = store.remove('ex-21');
    const deleteReq = httpMock.expectOne('/bff/api/currency-exchanges/ex-21');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    await Promise.resolve();

    httpMock
      .expectOne('/bff/api/currency-exchanges/positions?view=FAMILY')
      .flush([]);
    httpMock
      .expectOne('/bff/api/currency-exchanges?view=FAMILY&page=0&size=20')
      .flush(buildPage([], 0, 0, 0));
    httpMock.expectOne('/bff/api/accounts?view=FAMILY').flush([]);

    await removePromise;

    expect(store.positions()).toEqual([]);
    expect(store.exchanges()).toEqual([]);
    expect(store.page().number).toBe(0);
    expect(financeEvents.last()?.type).toBe('plan-changed');
  });
});
