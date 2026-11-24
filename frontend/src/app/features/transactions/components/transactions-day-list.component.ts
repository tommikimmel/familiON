import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import { TransactionItem } from '../../../core/finance/transactions.models';
import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent, CardComponent, IconComponent, SkeletonComponent } from '../../../shared/ui';
import {
  TransactionDayGroup,
  fallbackTransactionIcon,
  formatSignedAmount,
  formatSignedDayTotal,
  groupTransactionsByDay,
  resolveLensDisplayUnit,
  transactionAmountTone,
  transactionTypeLabel,
} from '../data-access/transactions.grouping';

@Component({
  selector: 'fo-transactions-day-list',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe, SkeletonComponent],
  template: `
    <div data-testid="transactions-day-list" class="space-y-4">
      @for (group of dayGroups(); track group.date) {
        <section [attr.aria-label]="group.label" class="space-y-1.5">
          <header class="flex items-baseline justify-between gap-2 px-1">
            <h2 class="text-xs font-semibold tracking-wide text-muted uppercase">
              {{ group.label }}
            </h2>

            @if (group.totals.length > 0) {
              <div class="flex flex-wrap items-center justify-end gap-2 text-xs font-semibold tabular-nums">
                @for (entry of group.totals; track entry.unit) {
                  <span
                    [class.text-success]="entry.amount > 0"
                    [class.text-danger]="entry.amount < 0"
                    [class.text-muted]="entry.amount === 0"
                  >
                    {{ formatDayTotal(entry.amount, entry.unit, entry.approximated) }}
                  </span>
                }
              </div>
            }
          </header>

          <fo-card padding="none">
            <ul class="divide-y divide-border/70">
              @for (tx of group.items; track tx.id) {
                <li>
                  <button
                    type="button"
                    [attr.data-testid]="'tx-row-' + tx.id"
                    (click)="selectTransaction.emit(tx)"
                    class="fo-focus-ring flex w-full cursor-pointer items-center justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-2/60"
                  >
                    <div class="flex min-w-0 items-center gap-3">
                      <span
                        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
                        [style.background-color]="iconBg(tx)"
                        [style.color]="iconColor(tx)"
                      >
                        <fo-icon [name]="tx.category?.icon ?? fallbackIcon(tx)" [size]="18" />
                      </span>

                      <div class="min-w-0">
                        <p class="truncate text-sm font-medium text-text">
                          {{ primaryTitle(tx) }}
                        </p>

                        <div class="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-muted">
                          <span class="truncate">{{ tx.account.name }}</span>

                          @if (tx.paidBy.id !== tx.attributedTo.id) {
                            <span aria-hidden="true">·</span>
                            <span data-testid="paid-by-label">
                              Pagado por {{ tx.paidBy.fullName }}
                            </span>
                          }

                          @if (tx.installment; as inst) {
                            <span aria-hidden="true">·</span>
                            <span
                              data-testid="installment-badge"
                              class="rounded-md bg-accent-soft px-1.5 py-0.5 text-[11px] font-semibold text-accent tabular-nums"
                            >
                              Cuota {{ inst.number }}/{{ inst.total }}
                            </span>
                          }

                          @if (tx.status === 'PROJECTED') {
                            <span aria-hidden="true">·</span>
                            <span
                              data-testid="projected-badge"
                              class="rounded-md bg-warning/15 px-1.5 py-0.5 text-[11px] font-semibold text-warning"
                            >
                              Proyectada
                            </span>
                          } @else if (tx.status === 'CANCELLED') {
                            <span aria-hidden="true">·</span>
                            <span
                              class="rounded-md bg-surface-2 px-1.5 py-0.5 text-[11px] font-medium text-muted"
                            >
                              Cancelada
                            </span>
                          }
                        </div>
                      </div>
                    </div>

                    <div class="shrink-0 text-right">
                      <p
                        class="text-sm font-semibold tabular-nums"
                        [class.text-success]="amountTone(tx) === 'positive'"
                        [class.text-danger]="amountTone(tx) === 'negative'"
                        [class.text-muted]="amountTone(tx) === 'neutral'"
                      >
                        {{ signedAmount(tx) }}
                      </p>

                      @if (showLensSubtitle(tx)) {
                        <p
                          data-testid="row-lens-amount"
                          class="text-xs text-muted tabular-nums"
                        >
                          {{ tx.lensApproximated ? '~' : ''
                          }}{{ tx.lensAmount | money: lensUnit(tx) }}
                        </p>
                      }
                    </div>
                  </button>
                </li>
              }
            </ul>
          </fo-card>
        </section>
      }

      @if (loadingMore()) {
        <div class="space-y-2 py-2" aria-busy="true">
          <fo-skeleton height="3.25rem" rounded="lg" />
          <fo-skeleton height="3.25rem" rounded="lg" />
        </div>
      }

      @if (loadMoreError(); as errMsg) {
        <div
          role="alert"
          class="flex items-center justify-between gap-3 rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-xs text-danger"
        >
          <span>{{ errMsg }}</span>
          <fo-button size="sm" variant="ghost" (click)="loadMore.emit()">Reintentar</fo-button>
        </div>
      }

      @if (hasMore()) {
        <div #sentinel class="flex justify-center py-2">
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
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionsDayListComponent {
  readonly items = input.required<readonly TransactionItem[]>();
  readonly lens = input.required<Lens>();
  readonly hasMore = input<boolean>(false);
  readonly loadingMore = input<boolean>(false);
  readonly loadMoreError = input<string | null>(null);
  readonly referenceTodayIso = input<string | undefined>(undefined);

  readonly selectTransaction = output<TransactionItem>();
  readonly loadMore = output<void>();

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');

  protected readonly dayGroups = computed<TransactionDayGroup[]>(() =>
    groupTransactionsByDay(this.items(), this.lens(), this.referenceTodayIso()),
  );

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

  protected primaryTitle(tx: TransactionItem): string {
    return tx.description?.trim() || tx.category?.name || transactionTypeLabel(tx.type);
  }

  protected fallbackIcon(tx: TransactionItem): string {
    return fallbackTransactionIcon(tx.type);
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

  protected formatDayTotal(amount: number, unit: string, approximated: boolean): string {
    return formatSignedDayTotal(amount, unit, approximated);
  }

  protected iconBg(tx: TransactionItem): string {
    const color = tx.category?.colorHex;
    return color ? `color-mix(in srgb, ${color} 14%, transparent)` : 'var(--fo-surface-2)';
  }

  protected iconColor(tx: TransactionItem): string {
    return tx.category?.colorHex || 'var(--fo-text-muted)';
  }
}
