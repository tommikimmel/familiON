import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Lens, View } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { toNumber } from '../../../shared/utils/decimal';
import {
  formatRelativeIsoDate,
  formatWholeMoney,
  initialsOf,
  resolveDisplayUnit,
  transactionTypeLabel,
} from '../data-access/home.formatters';
import { TransactionItem } from '../data-access/home.models';

/** Mobile recent movements (mock `.sec-head` + `.tx-list` / `.tx-row`). */
@Component({
  selector: 'fo-recent-list',
  standalone: true,
  imports: [MoneyPipe, RouterLink],
  template: `
    <section class="flex flex-col gap-[11px]" aria-labelledby="recent-list-title">
      <div class="fo-sec-head">
        <h2 id="recent-list-title" class="fo-sec-title">{{ title() }}</h2>
        <a routerLink="/transactions" class="fo-sec-link fo-focus-ring rounded-md">Ver todos</a>
      </div>

      <ul class="tx-list" aria-label="Lista de últimos movimientos">
        @for (tx of visibleTransactions(); track tx.id) {
          <li class="tx-row" [class.fo-tx-new]="newIds().has(tx.id)">
            <div class="tx-icon" aria-hidden="true">{{ initials(tx) }}</div>
            <div class="min-w-0 flex-1">
              <p class="fo-tx-title truncate">{{ primaryTitle(tx) }}</p>
              <p class="fo-tx-meta mt-px truncate">
                {{ meta(tx) }}
                @if (tx.paidBy.id !== tx.attributedTo.id) {
                  <span data-testid="paid-by-label" class="tx-tag">
                    Pagado por {{ tx.paidBy.fullName }}
                  </span>
                }
                @if (tx.installment; as inst) {
                  <span data-testid="installment-badge" class="tx-tag tx-tag--accent">
                    Cuota {{ inst.number }}/{{ inst.total }}
                  </span>
                }
              </p>
            </div>
            <div class="shrink-0 text-right">
              <p
                data-testid="transaction-amount"
                class="fo-tx-amt"
                [class.text-accent]="tx.type === 'INCOME'"
                [class.text-muted]="tx.type === 'TRANSFER'"
              >
                {{ signedAmount(tx) }}
              </p>
              @if (showLensSubtitle(tx)) {
                <p data-testid="transaction-lens-amount" class="tx-amt-sub">
                  {{ tx.lensApproximated ? '~' : '' }}{{ tx.lensAmount | money: lensUnit(tx) }}
                </p>
              }
            </div>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .tx-list {
      display: flex;
      flex-direction: column;
      gap: 7px;
    }
    .tx-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      background: var(--fo-surface);
      border: 1px solid var(--fo-border);
      border-radius: 14px;
      transition:
        transform 0.16s var(--fo-ease-spring),
        border-color 0.18s ease,
        background 0.2s ease;
    }
    .tx-row:hover {
      border-color: var(--fo-accent);
      transform: translateY(-1px);
    }
    .tx-icon {
      display: grid;
      flex-shrink: 0;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 10px;
      background: var(--fo-surface-2);
      color: var(--fo-text-2);
      font-size: 11px;
      font-weight: 700;
    }
    .tx-tag {
      margin-left: 4px;
      padding: 1px 5px;
      border-radius: 999px;
      background: var(--fo-surface-2);
      color: var(--fo-text-2);
      font-size: 9.5px;
      font-weight: 600;
    }
    .tx-tag--accent {
      background: var(--fo-accent-soft);
      color: var(--fo-accent);
    }
    .tx-amt-sub {
      margin-top: 1px;
      font-size: 10px;
      color: var(--fo-text-muted);
      font-variant-numeric: tabular-nums;
    }
    @keyframes fo-new-tx-highlight {
      0% {
        background: var(--fo-accent-soft);
        border-color: var(--fo-accent);
        transform: translateY(-6px) scale(0.98);
        opacity: 0;
      }
      35% {
        background: var(--fo-accent-soft);
        border-color: var(--fo-accent);
        transform: translateY(0) scale(1);
        opacity: 1;
      }
      100% {
        background: var(--fo-surface);
        border-color: var(--fo-border);
      }
    }
    .fo-tx-new {
      animation: fo-new-tx-highlight 1.35s var(--fo-ease-smooth) forwards;
    }
    @media (prefers-reduced-motion: reduce) {
      .tx-row,
      .fo-tx-new {
        animation: none;
        transition: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecentListComponent {
  readonly transactions = input.required<readonly TransactionItem[]>();
  readonly lens = input<Lens>('NOMINAL');
  readonly view = input<View>('PERSONAL');

  protected readonly visibleTransactions = computed(() => this.transactions().slice(0, 10));

  protected readonly title = computed(() =>
    this.view() === 'FAMILY' ? 'Movimientos de la familia' : 'Mis movimientos',
  );

  /** Ids that appeared after the first render, highlighted with the mock `tx-new` animation. */
  protected readonly newIds = signal<ReadonlySet<string>>(new Set());
  private knownIds: Set<string> | null = null;

  constructor() {
    effect(() => {
      const ids = this.transactions().map((tx) => tx.id);
      const known = this.knownIds;
      this.newIds.set(known ? new Set(ids.filter((id) => !known.has(id))) : new Set());
      this.knownIds = new Set(ids);
    });
  }

  protected primaryTitle(tx: TransactionItem): string {
    return tx.description?.trim() || tx.category?.name || transactionTypeLabel(tx.type);
  }

  protected initials(tx: TransactionItem): string {
    return initialsOf(tx.attributedTo.fullName);
  }

  /** Mock meta line: date · category · person (family view) · notes. */
  protected meta(tx: TransactionItem): string {
    const parts = [
      formatRelativeIsoDate(tx.transactionDate),
      tx.category?.name ?? null,
      this.view() === 'FAMILY' ? tx.attributedTo.fullName.split(' ')[0] : null,
      tx.notes?.trim() || null,
    ];
    return parts.filter((part): part is string => !!part).join(' · ');
  }

  protected signedAmount(tx: TransactionItem): string {
    const formatted = formatWholeMoney(Math.abs(toNumber(tx.amount)), tx.currencyCode);
    if (tx.type === 'INCOME') {
      return `+${formatted}`;
    }
    return tx.type === 'EXPENSE' ? `-${formatted}` : formatted;
  }

  protected showLensSubtitle(tx: TransactionItem): boolean {
    return this.lens() !== 'NOMINAL' && tx.lensAmount !== null;
  }

  protected lensUnit(tx: TransactionItem): string {
    return resolveDisplayUnit(tx.lensUnit ?? tx.currencyCode, this.lens());
  }
}
