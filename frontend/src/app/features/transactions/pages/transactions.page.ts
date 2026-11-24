import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { AccountsStore } from '../../../core/finance/accounts.store';
import { CategoriesStore } from '../../../core/finance/categories.store';
import { TransactionItem } from '../../../core/finance/transactions.models';
import { QuickAddLauncher } from '../../../core/layout/quick-add-launcher.service';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
} from '../../../shared/ui';
import { TransactionsDayListComponent } from '../components/transactions-day-list.component';
import { TransactionsFilterBarComponent } from '../components/transactions-filter-bar.component';
import { TransactionsTableComponent } from '../components/transactions-table.component';
import { TransactionsStore } from '../data-access/transactions.store';

@Component({
  selector: 'fo-transactions-page',
  standalone: true,
  providers: [TransactionsStore],
  imports: [
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    IconComponent,
    SkeletonComponent,
    TransactionsDayListComponent,
    TransactionsFilterBarComponent,
    TransactionsTableComponent,
  ],
  template: `
    <section class="mx-auto max-w-7xl space-y-4">
      <h1 class="sr-only">Movimientos</h1>

      <fo-transactions-filter-bar
        [filters]="store.filters()"
        [searchInput]="store.searchInput()"
        [isDesktop]="store.isDesktop()"
        [isFamilyView]="isFamilyView()"
        [categories]="categoriesStore.categories()"
        [accounts]="accountsStore.accounts()"
        [members]="store.members()"
        [currencies]="availableCurrencies()"
        [activeFilterCount]="store.activeFilterCount()"
        (searchChange)="store.setSearchQuery($event)"
        (searchClear)="store.clearSearch()"
        (dateRangeChange)="store.setDateRange($event.from, $event.to)"
        (dateRangeReset)="store.resetDateRange()"
        (typeToggle)="store.toggleType($event)"
        (typeRemove)="store.removeType($event)"
        (categoryChange)="store.setCategory($event)"
        (accountChange)="store.setAccount($event)"
        (attributedToChange)="store.setAttributedTo($event)"
        (currencyChange)="store.setCurrency($event)"
        (statusChange)="store.setStatus($event)"
        (sortChange)="store.setSort($event)"
        (clearAll)="store.clearAllFilters()"
      />

      @if (store.loading()) {
        <div data-testid="transactions-loading-skeleton" class="space-y-3" aria-busy="true">
          @if (store.isDesktop()) {
            <fo-card padding="md">
              <div class="space-y-3">
                @for (row of [1, 2, 3, 4, 5, 6]; track row) {
                  <fo-skeleton height="2.75rem" rounded="md" />
                }
              </div>
            </fo-card>
          } @else {
            @for (group of [1, 2]; track group) {
              <div class="space-y-2">
                <fo-skeleton width="30%" height="0.875rem" />
                <fo-card padding="md">
                  <div class="space-y-3">
                    @for (row of [1, 2, 3]; track row) {
                      <fo-skeleton height="3rem" rounded="md" />
                    }
                  </div>
                </fo-card>
              </div>
            }
          }
        </div>
      } @else if (store.error(); as errorMessage) {
        <fo-card padding="lg" data-testid="transactions-error-state">
          <div class="flex flex-col items-center py-6 text-center">
            <div
              class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-danger/12 text-danger"
            >
              <fo-icon name="alert-circle" [size]="24" />
            </div>
            <h2 class="text-base font-semibold text-text">No pudimos cargar los movimientos</h2>
            <p class="mt-1 max-w-sm text-sm text-muted">{{ errorMessage }}</p>
            <div class="mt-4">
              <fo-button data-testid="transactions-retry-btn" (click)="store.reload()">
                Reintentar
              </fo-button>
            </div>
          </div>
        </fo-card>
      } @else if (store.items().length === 0) {
        @if (store.activeFilterCount() > 0) {
          <fo-empty-state
            data-testid="transactions-filtered-empty"
            icon="arrow-left-right"
            title="Sin movimientos con estos filtros"
            description="Probá ajustando las fechas, quitando filtros o buscando otro término."
          >
            <fo-button variant="ghost" (click)="store.clearAllFilters()">
              Limpiar filtros
            </fo-button>
          </fo-empty-state>
        } @else {
          <fo-empty-state
            data-testid="transactions-empty-state"
            icon="wallet"
            title="Sin movimientos en este período"
            description="Todavía no hay movimientos registrados para este período."
          >
            <fo-button (click)="quickAdd.open()">Nuevo movimiento</fo-button>
          </fo-empty-state>
        }
      } @else if (store.isDesktop()) {
        <fo-transactions-table
          [items]="store.items()"
          [lens]="viewContext.lens()"
          [sort]="store.filters().sort"
          [totalElements]="store.totalElements()"
          [hasMore]="store.hasMore()"
          [loadingMore]="store.loadingMore()"
          [loadMoreError]="store.loadMoreError()"
          (selectTransaction)="onSelectTransaction($event)"
          (sortToggle)="store.toggleSortBy($event)"
          (loadMore)="store.loadMore()"
        />
      } @else {
        <fo-transactions-day-list
          [items]="store.items()"
          [lens]="viewContext.lens()"
          [hasMore]="store.hasMore()"
          [loadingMore]="store.loadingMore()"
          [loadMoreError]="store.loadMoreError()"
          (selectTransaction)="onSelectTransaction($event)"
          (loadMore)="store.loadMore()"
        />
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsPage {
  protected readonly store = inject(TransactionsStore);
  protected readonly accountsStore = inject(AccountsStore);
  protected readonly categoriesStore = inject(CategoriesStore);
  protected readonly viewContext = inject(ViewContextStore);
  protected readonly quickAdd = inject(QuickAddLauncher);
  private readonly session = inject(SessionStore);

  protected readonly isFamilyView = computed(() => this.viewContext.view() === 'FAMILY');

  protected readonly availableCurrencies = computed<readonly string[]>(() => {
    const set = new Set<string>();
    const family = this.session.me()?.family;
    if (family) {
      for (const code of family.enabledCurrencies) {
        if (code.trim()) {
          set.add(code.trim().toUpperCase());
        }
      }
      if (family.baseCurrency) {
        set.add(family.baseCurrency.trim().toUpperCase());
      }
    }
    for (const acc of this.accountsStore.accounts()) {
      if (acc.currencyCode) {
        set.add(acc.currencyCode.trim().toUpperCase());
      }
    }
    if (set.size === 0) {
      set.add('ARS');
      set.add('USD');
    }
    return [...set];
  });

  constructor() {
    void this.categoriesStore.load();

    effect(() => {
      const view = this.viewContext.view();
      void this.accountsStore.load(view);
      if (view === 'FAMILY' || this.session.isAdult()) {
        void this.store.loadMembers();
      }
    });
  }

  protected onSelectTransaction(_tx: TransactionItem): void {
    // Wired to TransactionDetailSheetComponent in the next step.
    void _tx;
  }
}
