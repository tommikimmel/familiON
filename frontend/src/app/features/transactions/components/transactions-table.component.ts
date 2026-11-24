import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import {
  TransactionItem,
  TransactionSort,
  TransactionSortField,
} from '../../../core/finance/transactions.models';
import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent, CardComponent, IconComponent, SkeletonComponent } from '../../../shared/ui';
import {
  fallbackTransactionIcon,
  formatShortDate,
  formatSignedAmount,
  resolveLensDisplayUnit,
  transactionAmountTone,
  transactionTypeLabel,
} from '../data-access/transactions.grouping';

@Component({
  selector: 'fo-transactions-table',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe, SkeletonComponent],
  template: `
    <fo-card padding="none" data-testid="transactions-desktop-table">
      <div class="flex items-center justify-between border-b border-border px-5 py-3">
        <p class="text-xs font-medium text-muted tabular-nums">
          Mostrando {{ items().length }} de {{ totalElements() }} movimientos
        </p>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full border-collapse text-left text-sm">
          <thead>
            <tr class="border-b border-border bg-surface-2/50 text-xs font-medium text-muted">
              <th scope="col" class="px-5 py-2.5">Movimiento</th>
              <th scope="col" class="px-3 py-2.5">Categoría</th>
              <th scope="col" class="px-3 py-2.5">Atribuido a / pagó</th>
              <th scope="col" class="px-3 py-2.5">Cuenta / Cuota</th>
              <th
                scope="col"
                class="px-3 py-2.5"
                [attr.aria-sort]="ariaSort('transactionDate')"
              >
                <button
                  type="button"
                  data-testid="sort-date-btn"
                  (click)="sortToggle.emit('transactionDate')"
                  class="fo-focus-ring inline-flex cursor-pointer items-center gap-1 rounded-md py-0.5 font-medium transition-colors hover:text-text"
                  [class.text-text]="isSortedBy('transactionDate')"
                >
                  <span>Fecha</span>
                  <fo-icon
                    name="chevron-down"
                    [size]="14"
                    class="transition-transform"
                    [class.opacity-40]="!isSortedBy('transactionDate')"
                    [class.rotate-180]="isSortAsc('transactionDate')"
                  />
                </button>
              </th>
              <th
                scope="col"
                class="px-5 py-2.5 text-right"
                [attr.aria-sort]="ariaSort('amount')"
              >
                <button
                  type="button"
                  data-testid="sort-amount-btn"
                  (click)="sortToggle.emit('amount')"
                  class="fo-focus-ring inline-flex cursor-pointer items-center justify-end gap-1 rounded-md py-0.5 font-medium transition-colors hover:text-text"
                  [class.text-text]="isSortedBy('amount')"
                >
                  <span>Monto</span>
                  <fo-icon
                    name="chevron-down"
                    [size]="14"
                    class="transition-transform"
                    [class.opacity-40]="!isSortedBy('amount')"
                    [class.rotate-180]="isSortAsc('amount')"
                  />
                </button>
              </th>
            </tr>
          </thead>

          <tbody class="divide-y divide-border/70">
            @for (tx of items(); track tx.id) {
              <tr
                tabindex="0"
                [attr.data-testid]="'tx-table-row-' + tx.id"
                (click)="selectTransaction.emit(tx)"
                (keydown.enter)="selectTransaction.emit(tx)"
                (keydown.space)="onRowSpace($event, tx)"
                class="fo-focus-ring cursor-pointer transition-colors hover:bg-surface-2/50"
              >
                <td class="px-5 py-3">
                  <div class="flex items-center gap-3">
                    <span
                      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                      [style.background-color]="iconBg(tx)"
                      [style.color]="iconColor(tx)"
                    >
                      <fo-icon [name]="tx.category?.icon ?? fallbackIcon(tx)" [size]="16" />
                    </span>
                    <div class="min-w-0">
                      <div class="flex items-center gap-2">
                        <p class="truncate font-medium text-text">{{ primaryTitle(tx) }}</p>
                        @if (tx.status === 'PROJECTED') {
                          <span
                            data-testid="projected-badge"
                            class="shrink-0 rounded-md bg-warning/15 px-1.5 py-0.5 text-[11px] font-semibold text-warning"
                          >
                            Proyectada
                          </span>
                        } @else if (tx.status === 'CANCELLED') {
                          <span
                            class="shrink-0 rounded-md bg-surface-2 px-1.5 py-0.5 text-[11px] font-medium text-muted"
                          >
                            Cancelada
                          </span>
                        }
                      </div>
                      @if (tx.notes) {
                        <p class="max-w-60 truncate text-xs text-muted">{{ tx.notes }}</p>
                      }
                    </div>
                  </div>
                </td>

                <td class="px-3 py-3 text-text">
                  @if (tx.category; as cat) {
                    <span class="inline-flex items-center gap-1.5">
                      <span
                        class="h-2 w-2 shrink-0 rounded-full"
                        [style.background-color]="cat.colorHex || 'var(--fo-accent)'"
                        aria-hidden="true"
                      ></span>
                      <span class="truncate">{{ cat.name }}</span>
                    </span>
                  } @else {
                    <span class="text-muted">{{ typeLabel(tx) }}</span>
                  }
                </td>

                <td class="px-3 py-3">
                  <p class="text-text">{{ tx.attributedTo.fullName }}</p>
                  @if (tx.paidBy.id !== tx.attributedTo.id) {
                    <p class="text-xs text-muted">Pagado por {{ tx.paidBy.fullName }}</p>
                  }
                </td>

                <td class="px-3 py-3">
                  <p class="text-text">{{ tx.account.name }}</p>
                  @if (tx.installment; as inst) {
                    <span
                      class="mt-0.5 inline-block rounded-md bg-accent-soft px-1.5 py-0.5 text-[11px] font-semibold text-accent tabular-nums"
                    >
                      Cuota {{ inst.number }}/{{ inst.total }}
                    </span>
                  }
                </td>

                <td class="px-3 py-3 whitespace-nowrap text-muted tabular-nums">
                  {{ formatDate(tx.transactionDate) }}
                </td>

                <td class="px-5 py-3 text-right whitespace-nowrap">
                  <p
                    class="font-semibold tabular-nums"
                    [class.text-success]="amountTone(tx) === 'positive'"
                    [class.text-danger]="amountTone(tx) === 'negative'"
                    [class.text-muted]="amountTone(tx) === 'neutral'"
                  >
                    {{ signedAmount(tx) }}
                  </p>
                  @if (showLensSubtitle(tx)) {
                    <p data-testid="table-lens-amount" class="text-xs text-muted tabular-nums">
                      {{ tx.lensApproximated ? '~' : ''
                      }}{{ tx.lensAmount | money: lensUnit(tx) }}
                    </p>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      @if (loadingMore()) {
        <div class="space-y-2 border-t border-border p-4" aria-busy="true">
          <fo-skeleton height="2.25rem" rounded="md" />
          <fo-skeleton height="2.25rem" rounded="md" />
        </div>
      }

      @if (loadMoreError(); as errMsg) {
        <div
          role="alert"
          class="flex items-center justify-between gap-3 border-t border-danger/30 bg-danger/10 px-5 py-3 text-xs text-danger"
        >
          <span>{{ errMsg }}</span>
          <fo-button size="sm" variant="ghost" (click)="loadMore.emit()">Reintentar</fo-button>
        </div>
      }

      @if (hasMore()) {
        <div #sentinel class="flex justify-center border-t border-border/70 py-3">
          @if (!loadingMore()) {
            <fo-button
              data-testid="load-more-btn"
              size="sm"
              variant="ghost"
              (click)="loadMore.emit()"
            >
              Cargar más movimientos
            </fo-button>
          }
        </div>
      }
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsTableComponent {
  readonly items = input.required<readonly TransactionItem[]>();
  readonly lens = input.required<Lens>();
  readonly sort = input.required<TransactionSort>();
  readonly totalElements = input<number>(0);
  readonly hasMore = input<boolean>(false);
  readonly loadingMore = input<boolean>(false);
  readonly loadMoreError = input<string | null>(null);

  readonly selectTransaction = output<TransactionItem>();
  readonly sortToggle = output<TransactionSortField>();
  readonly loadMore = output<void>();

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');

  constructor() {
    effect((onCleanup) => {
      const target = this.sentinel()?.nativeElement;
      if (!target || typeof IntersectionObserver === 'undefined') {
        return;
      }

      const observer = new IntersectionObserver(
        (entries) => {
          if (
            entries.some((entry) => entry.isIntersecting) &&
            this.hasMore() &&
            !this.loadingMore()
          ) {
            this.loadMore.emit();
          }
        },
        { rootMargin: '200px' },
      );

      observer.observe(target);
      onCleanup(() => observer.disconnect());
    });
  }

  protected onRowSpace(event: Event, tx: TransactionItem): void {
    event.preventDefault();
    this.selectTransaction.emit(tx);
  }

  protected isSortedBy(field: TransactionSortField): boolean {
    return this.sort().startsWith(`${field},`);
  }

  protected isSortAsc(field: TransactionSortField): boolean {
    return this.sort() === `${field},asc`;
  }

  protected ariaSort(field: TransactionSortField): 'ascending' | 'descending' | 'none' {
    if (!this.isSortedBy(field)) {
      return 'none';
    }
    return this.isSortAsc(field) ? 'ascending' : 'descending';
  }

  protected primaryTitle(tx: TransactionItem): string {
    return tx.description?.trim() || tx.category?.name || transactionTypeLabel(tx.type);
  }

  protected typeLabel(tx: TransactionItem): string {
    return transactionTypeLabel(tx.type);
  }

  protected fallbackIcon(tx: TransactionItem): string {
    return fallbackTransactionIcon(tx.type);
  }

  protected formatDate(isoDate: string): string {
    return formatShortDate(isoDate);
  }

  protected amountTone(tx: TransactionItem): 'positive' | 'negative' | 'neutral' {
    return transactionAmountTone(tx.type);
  }

  protected signedAmount(tx: TransactionItem): string {
    return formatSignedAmount(tx.amount, tx.currencyCode, tx.type);
  }

  protected showLensSubtitle(tx: TransactionItem): boolean {
    return this.lens() !== 'NOMINAL' && tx.lensAmount !== null;
  }

  protected lensUnit(tx: TransactionItem): string {
    return resolveLensDisplayUnit(tx.lensUnit, this.lens(), tx.currencyCode);
  }

  protected iconBg(tx: TransactionItem): string {
    const color = tx.category?.colorHex;
    return color ? `color-mix(in srgb, ${color} 14%, transparent)` : 'var(--fo-surface-2)';
  }

  protected iconColor(tx: TransactionItem): string {
    return tx.category?.colorHex || 'var(--fo-text-muted)';
  }
}
