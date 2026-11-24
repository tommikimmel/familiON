import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { Account, Category } from '../../../core/finance/finance.models';
import { TransactionSort } from '../../../core/finance/transactions.models';
import {
  BottomSheetComponent,
  ButtonComponent,
  CardComponent,
  IconComponent,
} from '../../../shared/ui';
import {
  TRANSACTION_STATUS_OPTIONS,
  TRANSACTION_TYPE_GROUP_OPTIONS,
  TransactionStatusFilter,
  TransactionTypeGroup,
  TransactionsFilterState,
  statusFilterLabel,
  typeGroupLabel,
} from '../data-access/transactions.filters';
import { formatShortDate } from '../data-access/transactions.grouping';
import { TransactionsFamilyMember } from '../data-access/transactions-members.api';

interface ActiveChip {
  readonly id: string;
  readonly label: string;
  readonly remove: () => void;
}

const MOBILE_SORT_OPTIONS: readonly { value: TransactionSort; label: string }[] = [
  { value: 'transactionDate,desc', label: 'Fecha (más recientes)' },
  { value: 'transactionDate,asc', label: 'Fecha (más antiguos)' },
  { value: 'amount,desc', label: 'Monto (mayor a menor)' },
  { value: 'amount,asc', label: 'Monto (menor a mayor)' },
];

@Component({
  selector: 'fo-transactions-filter-bar',
  standalone: true,
  imports: [BottomSheetComponent, ButtonComponent, CardComponent, IconComponent],
  template: `
    @if (isDesktop()) {
      <fo-card padding="md" data-testid="desktop-filter-bar">
        <div class="space-y-3">
          <!-- Row 1: Search + Date range -->
          <div class="flex flex-wrap items-end gap-3">
            <div class="min-w-60 flex-1">
              <label for="tx-search-desktop" class="mb-1 block text-xs font-medium text-muted">
                Buscar
              </label>
              <div class="relative">
                <input
                  id="tx-search-desktop"
                  data-testid="transactions-search-input"
                  type="search"
                  [value]="searchInput()"
                  (input)="onSearchInput($event)"
                  placeholder="Buscar por descripción..."
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 py-2 pr-8 pl-3 text-sm text-text placeholder:text-muted"
                />
                @if (searchInput().length > 0) {
                  <button
                    type="button"
                    aria-label="Limpiar búsqueda"
                    (click)="searchClear.emit()"
                    class="fo-focus-ring absolute top-1/2 right-2 -translate-y-1/2 cursor-pointer rounded-md p-1 text-muted hover:text-text"
                  >
                    <fo-icon name="x" [size]="14" />
                  </button>
                }
              </div>
            </div>

            <div class="flex items-end gap-2">
              <div>
                <label for="tx-from-desktop" class="mb-1 block text-xs font-medium text-muted">
                  Desde
                </label>
                <input
                  id="tx-from-desktop"
                  data-testid="filter-from-input"
                  type="date"
                  [value]="filters().from ?? ''"
                  (change)="onFromChange($event)"
                  class="fo-focus-ring rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text tabular-nums"
                />
              </div>

              <div>
                <label for="tx-to-desktop" class="mb-1 block text-xs font-medium text-muted">
                  Hasta
                </label>
                <input
                  id="tx-to-desktop"
                  data-testid="filter-to-input"
                  type="date"
                  [value]="filters().to ?? ''"
                  (change)="onToChange($event)"
                  class="fo-focus-ring rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text tabular-nums"
                />
              </div>
            </div>
          </div>

          <!-- Row 2: Type pills -->
          <div class="flex flex-wrap items-center gap-1.5" role="group" aria-label="Filtrar por tipo">
            <span class="mr-1 text-xs font-medium text-muted">Tipo:</span>
            @for (opt of typeOptions; track opt.value) {
              <button
                type="button"
                [attr.data-testid]="'filter-type-' + opt.value"
                [attr.aria-pressed]="isTypeSelected(opt.value)"
                (click)="typeToggle.emit(opt.value)"
                class="fo-focus-ring cursor-pointer rounded-full border px-3 py-1 text-xs font-medium transition-colors"
                [class.border-accent]="isTypeSelected(opt.value)"
                [class.bg-accent-soft]="isTypeSelected(opt.value)"
                [class.text-accent]="isTypeSelected(opt.value)"
                [class.border-border]="!isTypeSelected(opt.value)"
                [class.bg-surface-2]="!isTypeSelected(opt.value)"
                [class.text-muted]="!isTypeSelected(opt.value)"
                [class.hover:text-text]="!isTypeSelected(opt.value)"
              >
                {{ opt.label }}
              </button>
            }
          </div>

          <!-- Row 3: Selectors -->
          <div class="grid grid-cols-2 gap-3 lg:grid-cols-5">
            <div>
              <label for="tx-category-desktop" class="mb-1 block text-xs font-medium text-muted">
                Categoría
              </label>
              <select
                id="tx-category-desktop"
                data-testid="filter-category-select"
                [value]="filters().categoryId ?? ''"
                (change)="onCategorySelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
              >
                <option value="">Todas</option>
                @for (cat of sortedCategories(); track cat.id) {
                  <option [value]="cat.id">{{ cat.name }}</option>
                }
              </select>
            </div>

            <div>
              <label for="tx-account-desktop" class="mb-1 block text-xs font-medium text-muted">
                Cuenta
              </label>
              <select
                id="tx-account-desktop"
                data-testid="filter-account-select"
                [value]="filters().accountId ?? ''"
                (change)="onAccountSelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
              >
                <option value="">Todas</option>
                @for (acc of accounts(); track acc.id) {
                  <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
                }
              </select>
            </div>

            @if (isFamilyView()) {
              <div>
                <label for="tx-person-desktop" class="mb-1 block text-xs font-medium text-muted">
                  Atribuido a
                </label>
                <select
                  id="tx-person-desktop"
                  data-testid="filter-person-select"
                  [value]="filters().attributedToUserId ?? ''"
                  (change)="onPersonSelect($event)"
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
                >
                  <option value="">Toda la familia</option>
                  @for (member of members(); track member.id) {
                    <option [value]="member.id">{{ member.fullName }}</option>
                  }
                </select>
              </div>
            }

            <div>
              <label for="tx-currency-desktop" class="mb-1 block text-xs font-medium text-muted">
                Divisa
              </label>
              <select
                id="tx-currency-desktop"
                data-testid="filter-currency-select"
                [value]="filters().currency ?? ''"
                (change)="onCurrencySelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
              >
                <option value="">Todas</option>
                @for (code of currencies(); track code) {
                  <option [value]="code">{{ code }}</option>
                }
              </select>
            </div>

            <div>
              <label for="tx-status-desktop" class="mb-1 block text-xs font-medium text-muted">
                Estado
              </label>
              <select
                id="tx-status-desktop"
                data-testid="filter-status-select"
                [value]="filters().status"
                (change)="onStatusSelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
              >
                @for (opt of statusOptions; track opt.value) {
                  <option [value]="opt.value">{{ opt.label }}</option>
                }
              </select>
            </div>
          </div>
        </div>
      </fo-card>
    } @else {
      <!-- Mobile search + Filtros trigger -->
      <div data-testid="mobile-filter-bar" class="flex items-center gap-2">
        <div class="relative flex-1">
          <label for="tx-search-mobile" class="sr-only">Buscar movimientos</label>
          <input
            id="tx-search-mobile"
            data-testid="transactions-search-input"
            type="search"
            [value]="searchInput()"
            (input)="onSearchInput($event)"
            placeholder="Buscar movimientos..."
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface py-2.5 pr-8 pl-3 text-sm text-text placeholder:text-muted"
          />
          @if (searchInput().length > 0) {
            <button
              type="button"
              aria-label="Limpiar búsqueda"
              (click)="searchClear.emit()"
              class="fo-focus-ring absolute top-1/2 right-2 -translate-y-1/2 cursor-pointer rounded-md p-1 text-muted hover:text-text"
            >
              <fo-icon name="x" [size]="14" />
            </button>
          }
        </div>

        <button
          type="button"
          data-testid="mobile-filters-open-btn"
          (click)="mobileSheetOpen.set(true)"
          class="fo-focus-ring inline-flex shrink-0 cursor-pointer items-center gap-1.5 rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm font-medium text-text transition-colors hover:bg-surface-2"
          [class.border-accent]="activeFilterCount() > 0"
          [class.text-accent]="activeFilterCount() > 0"
        >
          <fo-icon name="calendar-range" [size]="16" />
          <span>Filtros</span>
          @if (activeFilterCount() > 0) {
            <span
              class="inline-flex min-w-5 items-center justify-center rounded-full bg-accent px-1.5 py-0.5 text-[11px] font-bold text-accent-contrast tabular-nums"
            >
              {{ activeFilterCount() }}
            </span>
          }
        </button>
      </div>

      <fo-bottom-sheet [(open)]="mobileSheetOpen" title="Filtros">
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label for="tx-from-mobile" class="mb-1 block text-xs font-medium text-muted">
                Desde
              </label>
              <input
                id="tx-from-mobile"
                type="date"
                [value]="filters().from ?? ''"
                (change)="onFromChange($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text tabular-nums"
              />
            </div>
            <div>
              <label for="tx-to-mobile" class="mb-1 block text-xs font-medium text-muted">
                Hasta
              </label>
              <input
                id="tx-to-mobile"
                type="date"
                [value]="filters().to ?? ''"
                (change)="onToChange($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text tabular-nums"
              />
            </div>
          </div>

          <div>
            <span class="mb-1.5 block text-xs font-medium text-muted">Tipo</span>
            <div class="flex flex-wrap gap-1.5" role="group" aria-label="Filtrar por tipo">
              @for (opt of typeOptions; track opt.value) {
                <button
                  type="button"
                  [attr.aria-pressed]="isTypeSelected(opt.value)"
                  (click)="typeToggle.emit(opt.value)"
                  class="fo-focus-ring cursor-pointer rounded-full border px-3 py-1.5 text-xs font-medium transition-colors"
                  [class.border-accent]="isTypeSelected(opt.value)"
                  [class.bg-accent-soft]="isTypeSelected(opt.value)"
                  [class.text-accent]="isTypeSelected(opt.value)"
                  [class.border-border]="!isTypeSelected(opt.value)"
                  [class.bg-surface-2]="!isTypeSelected(opt.value)"
                  [class.text-muted]="!isTypeSelected(opt.value)"
                >
                  {{ opt.label }}
                </button>
              }
            </div>
          </div>

          <div>
            <label for="tx-category-mobile" class="mb-1 block text-xs font-medium text-muted">
              Categoría
            </label>
            <select
              id="tx-category-mobile"
              [value]="filters().categoryId ?? ''"
              (change)="onCategorySelect($event)"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-sm text-text"
            >
              <option value="">Todas</option>
              @for (cat of sortedCategories(); track cat.id) {
                <option [value]="cat.id">{{ cat.name }}</option>
              }
            </select>
          </div>

          <div>
            <label for="tx-account-mobile" class="mb-1 block text-xs font-medium text-muted">
              Cuenta
            </label>
            <select
              id="tx-account-mobile"
              [value]="filters().accountId ?? ''"
              (change)="onAccountSelect($event)"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-sm text-text"
            >
              <option value="">Todas</option>
              @for (acc of accounts(); track acc.id) {
                <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
              }
            </select>
          </div>

          @if (isFamilyView()) {
            <div>
              <label for="tx-person-mobile" class="mb-1 block text-xs font-medium text-muted">
                Persona atribuida
              </label>
              <select
                id="tx-person-mobile"
                [value]="filters().attributedToUserId ?? ''"
                (change)="onPersonSelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-sm text-text"
              >
                <option value="">Toda la familia</option>
                @for (member of members(); track member.id) {
                  <option [value]="member.id">{{ member.fullName }}</option>
                }
              </select>
            </div>
          }

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label for="tx-currency-mobile" class="mb-1 block text-xs font-medium text-muted">
                Divisa
              </label>
              <select
                id="tx-currency-mobile"
                [value]="filters().currency ?? ''"
                (change)="onCurrencySelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-sm text-text"
              >
                <option value="">Todas</option>
                @for (code of currencies(); track code) {
                  <option [value]="code">{{ code }}</option>
                }
              </select>
            </div>

            <div>
              <label for="tx-status-mobile" class="mb-1 block text-xs font-medium text-muted">
                Estado
              </label>
              <select
                id="tx-status-mobile"
                [value]="filters().status"
                (change)="onStatusSelect($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-sm text-text"
              >
                @for (opt of statusOptions; track opt.value) {
                  <option [value]="opt.value">{{ opt.label }}</option>
                }
              </select>
            </div>
          </div>

          <div>
            <label for="tx-sort-mobile" class="mb-1 block text-xs font-medium text-muted">
              Ordenar por
            </label>
            <select
              id="tx-sort-mobile"
              [value]="filters().sort"
              (change)="onSortSelect($event)"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2.5 text-sm text-text"
            >
              @for (opt of mobileSortOptions; track opt.value) {
                <option [value]="opt.value">{{ opt.label }}</option>
              }
            </select>
          </div>

          <div class="flex items-center justify-between gap-3 border-t border-border pt-3">
            <fo-button variant="ghost" size="md" (click)="onClearAllFromSheet()">
              Limpiar
            </fo-button>
            <fo-button variant="primary" size="md" (click)="mobileSheetOpen.set(false)">
              Ver resultados
            </fo-button>
          </div>
        </div>
      </fo-bottom-sheet>
    }

    @if (activeChips().length > 0) {
      <div
        data-testid="active-filter-chips"
        class="mt-3 flex flex-wrap items-center gap-2"
        aria-label="Filtros activos"
      >
        @for (chip of activeChips(); track chip.id) {
          <span
            class="inline-flex items-center gap-1 rounded-full border border-accent/30 bg-accent-soft py-1 pr-1.5 pl-3 text-xs font-medium text-accent"
          >
            <span>{{ chip.label }}</span>
            <button
              type="button"
              [attr.data-testid]="'remove-chip-' + chip.id"
              [attr.aria-label]="'Quitar filtro ' + chip.label"
              (click)="chip.remove()"
              class="fo-focus-ring inline-flex h-4 w-4 cursor-pointer items-center justify-center rounded-full transition-colors hover:bg-accent/20"
            >
              <fo-icon name="x" [size]="12" />
            </button>
          </span>
        }

        <button
          type="button"
          data-testid="clear-all-filters-btn"
          (click)="clearAll.emit()"
          class="fo-focus-ring cursor-pointer rounded-full px-2.5 py-1 text-xs font-medium text-muted transition-colors hover:bg-surface-2 hover:text-text"
        >
          Limpiar todo
        </button>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsFilterBarComponent {
  readonly filters = input.required<TransactionsFilterState>();
  readonly searchInput = input.required<string>();
  readonly isDesktop = input.required<boolean>();
  readonly isFamilyView = input.required<boolean>();
  readonly categories = input.required<readonly Category[]>();
  readonly accounts = input.required<readonly Account[]>();
  readonly members = input.required<readonly TransactionsFamilyMember[]>();
  readonly currencies = input.required<readonly string[]>();
  readonly activeFilterCount = input<number>(0);

  readonly searchChange = output<string>();
  readonly searchClear = output<void>();
  readonly dateRangeChange = output<{ from: string | null; to: string | null }>();
  readonly dateRangeReset = output<void>();
  readonly typeToggle = output<TransactionTypeGroup>();
  readonly typeRemove = output<TransactionTypeGroup>();
  readonly categoryChange = output<string | null>();
  readonly accountChange = output<string | null>();
  readonly attributedToChange = output<string | null>();
  readonly currencyChange = output<string | null>();
  readonly statusChange = output<TransactionStatusFilter>();
  readonly sortChange = output<TransactionSort>();
  readonly clearAll = output<void>();

  protected readonly mobileSheetOpen = signal<boolean>(false);
  protected readonly typeOptions = TRANSACTION_TYPE_GROUP_OPTIONS;
  protected readonly statusOptions = TRANSACTION_STATUS_OPTIONS;
  protected readonly mobileSortOptions = MOBILE_SORT_OPTIONS;

  protected readonly sortedCategories = computed(() =>
    [...this.categories()]
      .filter((cat) => cat.isActive !== false)
      .sort((a, b) => a.name.localeCompare(b.name, 'es')),
  );

  protected readonly activeChips = computed<readonly ActiveChip[]>(() => {
    const f = this.filters();
    const chips: ActiveChip[] = [];

    if (f.customDateRange) {
      const dateLabel =
        f.from && f.to
          ? `${formatShortDate(f.from)} – ${formatShortDate(f.to)}`
          : f.from
            ? `Desde ${formatShortDate(f.from)}`
            : f.to
              ? `Hasta ${formatShortDate(f.to)}`
              : 'Rango personalizado';
      chips.push({
        id: 'date',
        label: `Fechas: ${dateLabel}`,
        remove: () => this.dateRangeReset.emit(),
      });
    }

    for (const typeGroup of f.types) {
      chips.push({
        id: `type-${typeGroup}`,
        label: typeGroupLabel(typeGroup),
        remove: () => this.typeRemove.emit(typeGroup),
      });
    }

    if (f.categoryId) {
      const name = this.categories().find((c) => c.id === f.categoryId)?.name ?? 'Seleccionada';
      chips.push({
        id: 'category',
        label: `Categoría: ${name}`,
        remove: () => this.categoryChange.emit(null),
      });
    }

    if (f.accountId) {
      const name = this.accounts().find((a) => a.id === f.accountId)?.name ?? 'Seleccionada';
      chips.push({
        id: 'account',
        label: `Cuenta: ${name}`,
        remove: () => this.accountChange.emit(null),
      });
    }

    if (this.isFamilyView() && f.attributedToUserId) {
      const name =
        this.members().find((m) => m.id === f.attributedToUserId)?.fullName ?? 'Miembro';
      chips.push({
        id: 'person',
        label: `Atribuido a: ${name}`,
        remove: () => this.attributedToChange.emit(null),
      });
    }

    if (f.currency) {
      chips.push({
        id: 'currency',
        label: `Divisa: ${f.currency}`,
        remove: () => this.currencyChange.emit(null),
      });
    }

    if (f.status !== 'ALL') {
      chips.push({
        id: 'status',
        label: `Estado: ${statusFilterLabel(f.status)}`,
        remove: () => this.statusChange.emit('ALL'),
      });
    }

    if (f.q.trim().length > 0) {
      chips.push({
        id: 'q',
        label: `Búsqueda: «${f.q.trim()}»`,
        remove: () => this.searchClear.emit(),
      });
    }

    return chips;
  });

  protected isTypeSelected(group: TransactionTypeGroup): boolean {
    return this.filters().types.includes(group);
  }

  protected onSearchInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.searchChange.emit(target?.value ?? '');
  }

  protected onFromChange(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    const from = target?.value?.trim() || null;
    this.dateRangeChange.emit({ from, to: this.filters().to });
  }

  protected onToChange(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    const to = target?.value?.trim() || null;
    this.dateRangeChange.emit({ from: this.filters().from, to });
  }

  protected onCategorySelect(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? '';
    this.categoryChange.emit(value || null);
  }

  protected onAccountSelect(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? '';
    this.accountChange.emit(value || null);
  }

  protected onPersonSelect(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? '';
    this.attributedToChange.emit(value || null);
  }

  protected onCurrencySelect(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? '';
    this.currencyChange.emit(value || null);
  }

  protected onStatusSelect(event: Event): void {
    const value = ((event.target as HTMLSelectElement | null)?.value ??
      'ALL') as TransactionStatusFilter;
    this.statusChange.emit(value);
  }

  protected onSortSelect(event: Event): void {
    const value = ((event.target as HTMLSelectElement | null)?.value ??
      'transactionDate,desc') as TransactionSort;
    this.sortChange.emit(value);
  }

  protected onClearAllFromSheet(): void {
    this.clearAll.emit();
    this.mobileSheetOpen.set(false);
  }
}
