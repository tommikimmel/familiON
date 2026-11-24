import { BreakpointObserver } from '@angular/cdk/layout';
import {
  DestroyRef,
  Injectable,
  Signal,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  EMPTY,
  Subject,
  Subscription,
  catchError,
  debounceTime,
  distinctUntilChanged,
  firstValueFrom,
  map,
  switchMap,
  tap,
} from 'rxjs';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { TransactionsApi } from '../../../core/finance/transactions.api';
import {
  TransactionItem,
  TransactionListParams,
  TransactionPatch,
  TransactionSort,
  TransactionSortField,
} from '../../../core/finance/transactions.models';
import { ViewContextStore } from '../../../core/state/view-context.store';
import {
  DEFAULT_SORT,
  TransactionStatusFilter,
  TransactionTypeGroup,
  TransactionsFilterState,
  countActiveFilters,
  createDefaultFilterState,
  filtersEqual,
  isValidIsoDate,
  mapStatusFilterToApiStatus,
  mapTypeGroupsToApiTypes,
  parseFiltersFromQueryParams,
  periodRangeFor,
  serializeFiltersToQueryParams,
} from './transactions.filters';
import {
  TransactionsFamilyMember,
  TransactionsMembersApi,
} from './transactions-members.api';
import { extractTransactionProblemMessage } from './transactions-problem';

export const TRANSACTIONS_PAGE_SIZE = 30;
const SEARCH_DEBOUNCE_MS = 300;
const DESKTOP_BREAKPOINT = '(min-width: 1024px)';
const DEFAULT_LOAD_ERROR = 'No pudimos cargar los movimientos. Intentá nuevamente.';
const DEFAULT_LOAD_MORE_ERROR = 'No pudimos cargar más movimientos.';

@Injectable()
export class TransactionsStore {
  private readonly api = inject(TransactionsApi);
  private readonly membersApi = inject(TransactionsMembersApi);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly router = inject(Router, { optional: true });
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  private readonly _rawFilters = signal<TransactionsFilterState>(this.readInitialFilters());
  private readonly _searchInput = signal<string>(this._rawFilters().q);

  private readonly _items = signal<readonly TransactionItem[]>([]);
  private readonly _page = signal<number>(0);
  private readonly _totalElements = signal<number>(0);
  private readonly _totalPages = signal<number>(0);
  private readonly _loading = signal<boolean>(false);
  private readonly _loadingMore = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _loadMoreError = signal<string | null>(null);
  private readonly _members = signal<readonly TransactionsFamilyMember[]>([]);
  private readonly _reloadTick = signal<number>(0);

  private readonly resetTrigger$ = new Subject<TransactionListParams>();
  private readonly searchInput$ = new Subject<string>();
  private loadMoreSub: Subscription | null = null;
  private generation = 0;
  private membersLoaded = false;
  private loadingMembers = false;

  readonly defaultDateRange = computed(() =>
    periodRangeFor(this.viewContext.anchorDate(), this.viewContext.granularity()),
  );

  readonly filters: Signal<TransactionsFilterState> = computed(() => {
    const raw = this._rawFilters();
    const range = this.defaultDateRange();
    const isFamily = this.viewContext.view() === 'FAMILY';

    return {
      ...raw,
      from: raw.customDateRange ? raw.from : range.from,
      to: raw.customDateRange ? raw.to : range.to,
      attributedToUserId: isFamily ? raw.attributedToUserId : null,
    };
  });

  readonly searchInput: Signal<string> = this._searchInput.asReadonly();
  readonly items: Signal<readonly TransactionItem[]> = this._items.asReadonly();
  readonly page: Signal<number> = this._page.asReadonly();
  readonly totalElements: Signal<number> = this._totalElements.asReadonly();
  readonly totalPages: Signal<number> = this._totalPages.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly loadingMore: Signal<boolean> = this._loadingMore.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly loadMoreError: Signal<string | null> = this._loadMoreError.asReadonly();
  readonly members: Signal<readonly TransactionsFamilyMember[]> = this._members.asReadonly();

  readonly hasMore: Signal<boolean> = computed(
    () => this._totalPages() > 0 && this._page() + 1 < this._totalPages(),
  );

  readonly activeFilterCount: Signal<number> = computed(() => countActiveFilters(this.filters()));

  readonly isDesktop: Signal<boolean> = toSignal(
    this.breakpointObserver
      .observe(DESKTOP_BREAKPOINT)
      .pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_BREAKPOINT) },
  );

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.cancelLoadMore();
    });

    this.route?.queryParamMap.pipe(takeUntilDestroyed()).subscribe((queryMap) => {
      const parsed = parseFiltersFromQueryParams(queryMap, this.defaultDateRange());
      if (!filtersEqual(parsed, this._rawFilters())) {
        this._rawFilters.set(parsed);
        this._searchInput.set(parsed.q);
      }
    });

    this.searchInput$
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        map((value) => value.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe((trimmed) => {
        if (this._rawFilters().q === trimmed) {
          return;
        }
        this.applyFilterUpdate((current) => ({ ...current, q: trimmed }));
      });

    this.resetTrigger$
      .pipe(
        switchMap((params) => {
          this.generation += 1;
          this.cancelLoadMore();
          this._loading.set(true);
          this._loadingMore.set(false);
          this._error.set(null);
          this._loadMoreError.set(null);

          return this.api.list({ ...params, page: 0, size: TRANSACTIONS_PAGE_SIZE }).pipe(
            tap((response) => {
              this._items.set(response.content);
              this._page.set(response.page.number);
              this._totalElements.set(response.page.totalElements);
              this._totalPages.set(response.page.totalPages);
              this._loading.set(false);
            }),
            catchError((err: unknown) => {
              this._items.set([]);
              this._page.set(0);
              this._totalElements.set(0);
              this._totalPages.set(0);
              this._error.set(extractTransactionProblemMessage(err, DEFAULT_LOAD_ERROR));
              this._loading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();

    effect(() => {
      const view = this.viewContext.view();
      if (view !== 'FAMILY') {
        const raw = untracked(() => this._rawFilters());
        if (raw.attributedToUserId !== null) {
          untracked(() => {
            this.applyFilterUpdate((current) => ({ ...current, attributedToUserId: null }));
          });
        }
      }
    });

    effect(() => {
      const params = this.buildListParams();
      this.financeEvents.last();
      this._reloadTick();

      this.resetTrigger$.next(params);
    });
  }

  reload(): void {
    this._reloadTick.update((tick) => tick + 1);
  }

  loadMore(): void {
    if (this._loading() || this._loadingMore() || !this.hasMore()) {
      return;
    }

    const activeGeneration = this.generation;
    const nextPage = this._page() + 1;
    const params = this.buildListParams();

    this.cancelLoadMore();
    this._loadingMore.set(true);
    this._loadMoreError.set(null);

    this.loadMoreSub = this.api
      .list({ ...params, page: nextPage, size: TRANSACTIONS_PAGE_SIZE })
      .subscribe({
        next: (response) => {
          if (activeGeneration !== this.generation) {
            return;
          }
          this._items.update((existing) => {
            const seen = new Set(existing.map((item) => item.id));
            const uniqueNew = response.content.filter((item) => !seen.has(item.id));
            return [...existing, ...uniqueNew];
          });
          this._page.set(response.page.number);
          this._totalElements.set(response.page.totalElements);
          this._totalPages.set(response.page.totalPages);
          this._loadingMore.set(false);
          this.loadMoreSub = null;
        },
        error: (err: unknown) => {
          if (activeGeneration !== this.generation) {
            return;
          }
          this._loadMoreError.set(
            extractTransactionProblemMessage(err, DEFAULT_LOAD_MORE_ERROR),
          );
          this._loadingMore.set(false);
          this.loadMoreSub = null;
        },
      });
  }

  async loadMembers(): Promise<void> {
    if (this.membersLoaded || this.loadingMembers) {
      return;
    }
    this.loadingMembers = true;
    try {
      const list = await firstValueFrom(this.membersApi.list());
      this._members.set(list.filter((member) => member.isActive));
      this.membersLoaded = true;
    } catch {
      // Keep members empty on failure so filters remain usable.
    } finally {
      this.loadingMembers = false;
    }
  }

  getReferenceSnapshot(id: string): Promise<TransactionItem> {
    return firstValueFrom(
      this.api.get(id, {
        lens: 'REFERENCE_CURRENCY',
        view: this.viewContext.view(),
      }),
    );
  }

  updateTransaction(id: string, patch: TransactionPatch): Promise<TransactionItem> {
    return firstValueFrom(this.api.update(id, patch));
  }

  deleteTransaction(id: string): Promise<void> {
    return firstValueFrom(this.api.remove(id));
  }

  setSearchQuery(value: string): void {
    this._searchInput.set(value);
    this.searchInput$.next(value);
  }

  clearSearch(): void {
    this._searchInput.set('');
    this.searchInput$.next('');
    if (this._rawFilters().q !== '') {
      this.applyFilterUpdate((current) => ({ ...current, q: '' }));
    }
  }

  setDateRange(from: string | null, to: string | null): void {
    const validFrom = isValidIsoDate(from) ? from : null;
    const validTo = isValidIsoDate(to) ? to : null;
    this.applyFilterUpdate((current) => ({
      ...current,
      from: validFrom,
      to: validTo,
      customDateRange: true,
    }));
  }

  resetDateRange(): void {
    const range = this.defaultDateRange();
    this.applyFilterUpdate((current) => ({
      ...current,
      from: range.from,
      to: range.to,
      customDateRange: false,
    }));
  }

  toggleType(group: TransactionTypeGroup): void {
    this.applyFilterUpdate((current) => {
      const exists = current.types.includes(group);
      const types = exists
        ? current.types.filter((item) => item !== group)
        : [...current.types, group];
      return { ...current, types };
    });
  }

  removeType(group: TransactionTypeGroup): void {
    this.applyFilterUpdate((current) => ({
      ...current,
      types: current.types.filter((item) => item !== group),
    }));
  }

  setCategory(categoryId: string | null): void {
    this.applyFilterUpdate((current) => ({
      ...current,
      categoryId: categoryId?.trim() || null,
    }));
  }

  setAccount(accountId: string | null): void {
    this.applyFilterUpdate((current) => ({
      ...current,
      accountId: accountId?.trim() || null,
    }));
  }

  setAttributedTo(userId: string | null): void {
    this.applyFilterUpdate((current) => ({
      ...current,
      attributedToUserId: userId?.trim() || null,
    }));
  }

  setCurrency(currency: string | null): void {
    const normalized = currency?.trim().toUpperCase() || null;
    this.applyFilterUpdate((current) => ({ ...current, currency: normalized }));
  }

  setStatus(status: TransactionStatusFilter): void {
    this.applyFilterUpdate((current) => ({ ...current, status }));
  }

  setSort(sort: TransactionSort): void {
    this.applyFilterUpdate((current) => ({ ...current, sort }));
  }

  toggleSortBy(field: TransactionSortField): void {
    const current = this._rawFilters().sort;
    const [currentField, currentDir] = current.split(',') as [TransactionSortField, 'asc' | 'desc'];
    const nextSort: TransactionSort =
      currentField === field
        ? `${field},${currentDir === 'desc' ? 'asc' : 'desc'}`
        : `${field},desc`;
    this.setSort(nextSort);
  }

  clearAllFilters(): void {
    this._searchInput.set('');
    this.searchInput$.next('');
    const defaults = createDefaultFilterState(this.defaultDateRange());
    const next: TransactionsFilterState = {
      ...defaults,
      sort: this._rawFilters().sort,
    };
    this._rawFilters.set(next);
    this.syncUrl(next);
  }

  resetSort(): void {
    this.setSort(DEFAULT_SORT);
  }

  private readInitialFilters(): TransactionsFilterState {
    const range = periodRangeFor(
      this.viewContext.anchorDate(),
      this.viewContext.granularity(),
    );
    const paramMap = this.route?.snapshot?.queryParamMap;
    if (!paramMap) {
      return createDefaultFilterState(range);
    }
    return parseFiltersFromQueryParams(paramMap, range);
  }

  private applyFilterUpdate(
    updater: (current: TransactionsFilterState) => TransactionsFilterState,
  ): void {
    const current = this._rawFilters();
    const next = updater(current);
    if (filtersEqual(current, next)) {
      return;
    }
    this._rawFilters.set(next);
    this.syncUrl(next);
  }

  private syncUrl(state: TransactionsFilterState): void {
    if (!this.router || !this.route) {
      return;
    }
    const queryParams = serializeFiltersToQueryParams(state);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      replaceUrl: true,
    });
  }

  private buildListParams(): TransactionListParams {
    const f = this.filters();
    return {
      view: this.viewContext.view(),
      lens: this.viewContext.lens(),
      from: f.from,
      to: f.to,
      type: mapTypeGroupsToApiTypes(f.types),
      categoryId: f.categoryId,
      accountId: f.accountId,
      attributedToUserId: f.attributedToUserId,
      currency: f.currency,
      status: mapStatusFilterToApiStatus(f.status),
      q: f.q.trim() || null,
      sort: f.sort,
    };
  }

  private cancelLoadMore(): void {
    if (this.loadMoreSub) {
      this.loadMoreSub.unsubscribe();
      this.loadMoreSub = null;
    }
  }
}
