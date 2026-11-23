import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  fallbackTransactionIcon,
  formatShortIsoDate,
  formatSignedTransactionAmount,
  resolveDisplayUnit,
  transactionTypeLabel,
} from '../data-access/home.formatters';
import { TransactionItem } from '../data-access/home.models';

@Component({
  selector: 'fo-recent-list',
  standalone: true,
  imports: [CardComponent, IconComponent, MoneyPipe, RouterLink],
  template: `
    <fo-card padding="none">
      <div class="flex items-center justify-between border-b border-border px-4 py-3">
        <h2 class="text-base font-semibold text-text">Últimos movimientos</h2>
        <a
          routerLink="/transactions"
          class="fo-focus-ring inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-semibold text-accent transition-colors hover:bg-accent-soft"
        >
          <span>Ver todos</span>
          <fo-icon name="chevron-right" [size]="14" />
        </a>
      </div>

      <ul class="divide-y divide-border/70" aria-label="Lista de últimos movimientos">
        @for (tx of visibleTransactions(); track tx.id) {
          <li class="flex items-center justify-between gap-3 px-4 py-3">
            <div class="flex min-w-0 items-center gap-3">
              <div
                class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
                [style.background-color]="iconBg(tx)"
                [style.color]="iconColor(tx)"
              >
                <fo-icon [name]="tx.category?.icon ?? fallbackIcon(tx.type)" [size]="18" />
              </div>

              <div class="min-w-0">
                <p class="truncate text-sm font-medium text-text">
                  {{ primaryTitle(tx) }}
                </p>

                <div class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted">
                  @if (tx.description && tx.category?.name) {
                    <span class="truncate">{{ tx.category!.name }}</span>
                    <span aria-hidden="true">·</span>
                  }
                  <span class="tabular-nums">{{ formatDate(tx.transactionDate) }}</span>

                  @if (tx.paidBy.id !== tx.attributedTo.id) {
                    <span
                      data-testid="paid-by-label"
                      class="rounded-md bg-surface-2 px-1.5 py-0.5 text-[11px] font-medium text-text"
                    >
                      Pagado por {{ tx.paidBy.fullName }}
                    </span>
                  }

                  @if (tx.installment; as inst) {
                    <span
                      data-testid="installment-badge"
                      class="rounded-md bg-accent-soft px-1.5 py-0.5 text-[11px] font-semibold text-accent tabular-nums"
                    >
                      Cuota {{ inst.number }}/{{ inst.total }}
                    </span>
                  }
                </div>
              </div>
            </div>

            <div class="shrink-0 text-right">
              <p
                data-testid="transaction-amount"
                class="text-sm font-semibold tabular-nums"
                [class.text-success]="tx.type === 'INCOME'"
                [class.text-danger]="tx.type === 'EXPENSE'"
                [class.text-muted]="tx.type === 'TRANSFER'"
              >
                {{ signedAmount(tx) }}
              </p>

              @if (showLensSubtitle(tx)) {
                <p
                  data-testid="transaction-lens-amount"
                  class="text-xs text-muted tabular-nums"
                >
                  {{ tx.lensApproximated ? '~' : ''
                  }}{{ tx.lensAmount | money: lensUnit(tx) }}
                </p>
              }
            </div>
          </li>
        }
      </ul>
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecentListComponent {
  readonly transactions = input.required<readonly TransactionItem[]>();
  readonly lens = input<Lens>('NOMINAL');

  protected readonly visibleTransactions = computed(() => this.transactions().slice(0, 10));

  protected primaryTitle(tx: TransactionItem): string {
    return tx.description?.trim() || tx.category?.name || transactionTypeLabel(tx.type);
  }

  protected fallbackIcon(type: TransactionItem['type']): string {
    return fallbackTransactionIcon(type);
  }

  protected formatDate(isoDate: string): string {
    return formatShortIsoDate(isoDate);
  }

  protected signedAmount(tx: TransactionItem): string {
    return formatSignedTransactionAmount(tx.amount, tx.currencyCode, tx.type);
  }

  protected showLensSubtitle(tx: TransactionItem): boolean {
    return this.lens() !== 'NOMINAL' && tx.lensAmount !== null;
  }

  protected lensUnit(tx: TransactionItem): string {
    return resolveDisplayUnit(tx.lensUnit ?? tx.currencyCode, this.lens());
  }

  protected iconBg(tx: TransactionItem): string {
    const color = tx.category?.colorHex;
    return color ? `color-mix(in srgb, ${color} 14%, transparent)` : 'var(--fo-surface-2)';
  }

  protected iconColor(tx: TransactionItem): string {
    return tx.category?.colorHex || 'var(--fo-text-muted)';
  }
}
