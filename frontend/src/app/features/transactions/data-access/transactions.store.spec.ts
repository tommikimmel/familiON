import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { vi } from 'vitest';
import { FinanceEvents } from '../../../core/finance/finance-events';
import {
  TransactionItem,
  TransactionPage,
} from '../../../core/finance/transactions.models';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { TRANSACTIONS_PAGE_SIZE, TransactionsStore } from './transactions.store';

function createMockBreakpointObserver(initialMatches: boolean) {
  const subject = new BehaviorSubject<BreakpointState>({
    matches: initialMatches,
    breakpoints: { '(min-width: 1024px)': initialMatches },
  });

  const observer: Partial<BreakpointObserver> = {
    observe: () => subject.asObservable(),
    isMatched: () => subject.value.matches,
  };

  return { observer };
}

function makeTransaction(overrides: Partial<TransactionItem> = {}): TransactionItem {
  return {
    id: overrides.id ?? 'tx-1',
    type: overrides.type ?? 'EXPENSE',
    scope: overrides.scope ?? 'PERSONAL',
    status: overrides.status ?? 'POSTED',
    amount: overrides.amount ?? '12500.0000',
    currencyCode: overrides.currencyCode ?? 'ARS',
    transactionDate: overrides.transactionDate ?? '2026-11-21',
    description: overrides.description ?? 'Café',
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
    lensAmount: overrides.lensAmount ?? '12500.0000',
    lensUnit: overrides.lensUnit ?? 'ARS',
    lensApproximated: overrides.lensApproximated ?? false,
  };
}

function makePage(
  items: TransactionItem[],
  number = 0,
  totalElements = items.length,
  totalPages = 1,
): TransactionPage {
  return {
    content: items,
    page: {
      size: TRANSACTIONS_PAGE_SIZE,
      number,
      totalElements,
      totalPages,
    },
  };
}

describe('TransactionsStore', () => {
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;
  let financeEvents: FinanceEvents;
  let navigateSpy: ReturnType<typeof vi.fn>;
  let queryParamSubject: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  function setup(initialQueryParams: Params = {}, initialView: 'PERSONAL' | 'FAMILY' = 'PERSONAL') {
    TestBed.resetTestingModule();
    localStorage.clear();
    if (initialView === 'FAMILY') {
      localStorage.setItem(
        'fo.viewContext',
        JSON.stringify({ view: 'FAMILY', lens: 'NOMINAL', granularity: 'MONTH' }),
      );
    }

    queryParamSubject = new BehaviorSubject(convertToParamMap(initialQueryParams));
    const mockRoute: Partial<ActivatedRoute> = {
      snapshot: {
        get queryParamMap() {
          return queryParamSubject.value;
        },
      } as ActivatedRoute['snapshot'],
      queryParamMap: queryParamSubject.asObservable(),
    };

    navigateSpy = vi.fn().mockImplementation((_commands: unknown[], extras?: { queryParams?: Params }) => {
      if (extras?.queryParams) {
        queryParamSubject.next(convertToParamMap(extras.queryParams));
      }
      return Promise.resolve(true);
    });

    TestBed.configureTestingModule({
      providers: [
        TransactionsStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BreakpointObserver, useValue: createMockBreakpointObserver(true).observer },
        { provide: ActivatedRoute, useValue: mockRoute },
        { provide: Router, useValue: { navigate: navigateSpy } },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
    financeEvents = TestBed.inject(FinanceEvents);

    return TestBed.inject(TransactionsStore);
  }

  afterEach(() => {
    vi.useRealTimers();
    httpMock.verify();
    localStorage.clear();
  });

  it('restores filters from URL query params and sends them to /bff/api/transactions', () => {
    const store = setup(
      {
        from: '2026-10-01',
        to: '2026-10-31',
        type: 'EXPENSE,TRANSFER',
        categoryId: 'cat-food',
        accountId: 'acc-1',
        attributedToUserId: 'user-2',
        currency: 'usd',
        status: 'PROJECTED',
        q: 'super',
        sort: 'amount,asc',
      },
      'FAMILY',
    );
    TestBed.flushEffects();

    expect(store.filters()).toEqual({
      from: '2026-10-01',
      to: '2026-10-31',
      customDateRange: true,
      types: ['EXPENSE', 'TRANSFER'],
      categoryId: 'cat-food',
      accountId: 'acc-1',
      attributedToUserId: 'user-2',
      currency: 'USD',
      status: 'PROJECTED',
      q: 'super',
      sort: 'amount,asc',
    });

    const req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('view')).toBe('FAMILY');
    expect(req.request.params.get('lens')).toBe('NOMINAL');
    expect(req.request.params.get('from')).toBe('2026-10-01');
    expect(req.request.params.get('to')).toBe('2026-10-31');
    expect(req.request.params.getAll('type')).toEqual(['EXPENSE', 'TRANSFER_OUT', 'TRANSFER_IN']);
    expect(req.request.params.get('categoryId')).toBe('cat-food');
    expect(req.request.params.get('accountId')).toBe('acc-1');
    expect(req.request.params.get('attributedToUserId')).toBe('user-2');
    expect(req.request.params.get('currency')).toBe('USD');
    expect(req.request.params.getAll('status')).toEqual(['PROJECTED']);
    expect(req.request.params.get('q')).toBe('super');
    expect(req.request.params.get('sort')).toBe('amount,asc');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('30');

    req.flush(makePage([makeTransaction({ id: 'tx-1' })]));
    expect(store.items().map((item) => item.id)).toEqual(['tx-1']);
  });

  it('syncs filter changes to URL query params and reloads page 0', () => {
    const store = setup();
    TestBed.flushEffects();
    httpMock.expectOne((r) => r.url === '/bff/api/transactions').flush(makePage([]));

    store.toggleType('INCOME');
    store.setCategory('cat-salary');
    store.setDateRange('2026-09-01', '2026-09-30');
    TestBed.flushEffects();

    expect(navigateSpy).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({
        replaceUrl: true,
        queryParams: {
          from: '2026-09-01',
          to: '2026-09-30',
          type: 'INCOME',
          categoryId: 'cat-salary',
        },
      }),
    );

    const req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(req.request.params.getAll('type')).toEqual(['INCOME']);
    expect(req.request.params.get('categoryId')).toBe('cat-salary');
    expect(req.request.params.get('from')).toBe('2026-09-01');
    expect(req.request.params.get('to')).toBe('2026-09-30');
    req.flush(makePage([makeTransaction({ id: 'tx-inc', type: 'INCOME' })]));

    expect(store.items().map((i) => i.id)).toEqual(['tx-inc']);
  });

  it('accumulates items across pages on loadMore and updates hasMore', () => {
    const store = setup();
    TestBed.flushEffects();

    const page0Req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(page0Req.request.params.get('page')).toBe('0');
    page0Req.flush(
      makePage([makeTransaction({ id: 'tx-1' }), makeTransaction({ id: 'tx-2' })], 0, 3, 2),
    );

    expect(store.items().map((i) => i.id)).toEqual(['tx-1', 'tx-2']);
    expect(store.hasMore()).toBe(true);
    expect(store.page()).toBe(0);

    store.loadMore();
    expect(store.loadingMore()).toBe(true);

    const page1Req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(page1Req.request.params.get('page')).toBe('1');
    expect(page1Req.request.params.get('size')).toBe('30');
    page1Req.flush(
      makePage([makeTransaction({ id: 'tx-2' }), makeTransaction({ id: 'tx-3' })], 1, 3, 2),
    );

    expect(store.loadingMore()).toBe(false);
    expect(store.page()).toBe(1);
    expect(store.hasMore()).toBe(false);
    expect(store.items().map((i) => i.id)).toEqual(['tx-1', 'tx-2', 'tx-3']);

    // Calling loadMore when hasMore is false does not fire another request
    store.loadMore();
    httpMock.expectNone((r) => r.url === '/bff/api/transactions');
  });

  it('resets pagination and reloads when view, lens, or FinanceEvents change', () => {
    const store = setup({ attributedToUserId: 'user-2' }, 'FAMILY');
    TestBed.flushEffects();

    httpMock
      .expectOne((r) => r.url === '/bff/api/transactions')
      .flush(makePage([makeTransaction({ id: 'tx-1' })], 0, 2, 2));

    store.loadMore();
    httpMock
      .expectOne((r) => r.url === '/bff/api/transactions')
      .flush(makePage([makeTransaction({ id: 'tx-2' })], 1, 2, 2));

    expect(store.items()).toHaveLength(2);
    expect(store.page()).toBe(1);

    // Switching view to PERSONAL resets page to 0 and clears attributedToUserId
    viewContext.setView('PERSONAL');
    TestBed.flushEffects();

    const personalReq = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(personalReq.request.params.get('view')).toBe('PERSONAL');
    expect(personalReq.request.params.get('page')).toBe('0');
    expect(personalReq.request.params.get('attributedToUserId')).toBeNull();
    personalReq.flush(makePage([makeTransaction({ id: 'tx-personal' })], 0, 1, 1));

    expect(store.page()).toBe(0);
    expect(store.items().map((i) => i.id)).toEqual(['tx-personal']);

    // Changing lens triggers reload
    viewContext.setLens('REFERENCE_CURRENCY');
    TestBed.flushEffects();

    const lensReq = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(lensReq.request.params.get('lens')).toBe('REFERENCE_CURRENCY');
    lensReq.flush(makePage([makeTransaction({ id: 'tx-usd', lensUnit: 'USD' })], 0, 1, 1));

    // Emitting a finance event triggers reload
    financeEvents.emit('transaction-updated');
    TestBed.flushEffects();

    const eventReq = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    eventReq.flush(makePage([makeTransaction({ id: 'tx-after-event' })], 0, 1, 1));
    expect(store.items().map((i) => i.id)).toEqual(['tx-after-event']);
  });

  it('debounces search query input by 300ms before updating URL and fetching', () => {
    vi.useFakeTimers();
    const store = setup();
    TestBed.flushEffects();
    httpMock.expectOne((r) => r.url === '/bff/api/transactions').flush(makePage([]));

    store.setSearchQuery('c');
    vi.advanceTimersByTime(100);
    store.setSearchQuery('ca');
    vi.advanceTimersByTime(100);
    store.setSearchQuery('cafe');
    TestBed.flushEffects();

    // Still inside debounce window
    httpMock.expectNone((r) => r.url === '/bff/api/transactions');
    expect(store.searchInput()).toBe('cafe');
    expect(store.filters().q).toBe('');

    vi.advanceTimersByTime(300);
    TestBed.flushEffects();

    expect(store.filters().q).toBe('cafe');
    const req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(req.request.params.get('q')).toBe('cafe');
    req.flush(makePage([makeTransaction({ id: 'tx-cafe', description: 'Café' })]));

    expect(store.items().map((i) => i.id)).toEqual(['tx-cafe']);
  });

  it('toggles desktop sort order between desc and asc', () => {
    const store = setup();
    TestBed.flushEffects();
    httpMock.expectOne((r) => r.url === '/bff/api/transactions').flush(makePage([]));

    store.toggleSortBy('amount');
    TestBed.flushEffects();
    let req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(req.request.params.get('sort')).toBe('amount,desc');
    req.flush(makePage([]));

    store.toggleSortBy('amount');
    TestBed.flushEffects();
    req = httpMock.expectOne((r) => r.url === '/bff/api/transactions');
    expect(req.request.params.get('sort')).toBe('amount,asc');
    req.flush(makePage([]));
  });
});
