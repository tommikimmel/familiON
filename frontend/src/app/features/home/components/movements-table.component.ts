import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PillTagComponent } from '../../../shared/ui/pill-tag/pill-tag.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  formatShortIsoDate,
  formatWholeMoney,
  resolveDisplayUnit,
  transactionTypeLabel,
} from '../data-access/home.formatters';
import { TransactionItem } from '../data-access/home.models';

function normalizeSearch(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

@Component({
  selector: 'fo-movements-table',
  standalone: true,
  imports: [CardComponent, IconComponent, MoneyPipe, PillTagComponent, RouterLink],
  template: `
    <fo-card>
      <div class="table-head-bar">
        <h2 class="table-title">Movimientos recientes ({{ filteredTransactions().length }})</h2>
        <div class="flex items-center gap-2.5">
          <div class="relative">
            <label for="home-movements-search" class="sr-only">
              Buscar en los últimos movimientos
            </label>
            <input
              id="home-movements-search"
              data-testid="movements-search-input"
              type="search"
              [value]="searchQuery()"
              (input)="onSearchInput($event)"
              placeholder="Buscar por nombre, categoría o persona..."
              class="search-input"
            />
            @if (searchQuery().length > 0) {
              <button
                type="button"
                aria-label="Limpiar búsqueda"
                (click)="clearSearch()"
                class="fo-focus-ring absolute top-1/2 right-1.5 -translate-y-1/2 cursor-pointer rounded-md p-0.5 text-muted hover:text-text"
              >
                <fo-icon name="x" [size]="12" />
              </button>
            }
          </div>
          <a routerLink="/transactions" class="fo-sec-link fo-focus-ring rounded-md">Ver todos</a>
        </div>
      </div>

      @if (filteredTransactions().length === 0) {
        <div class="px-2 py-8 text-center">
          <p class="text-xs text-muted">
            No encontramos movimientos que coincidan con «{{ searchQuery() }}».
          </p>
          <button type="button" (click)="clearSearch()" class="fo-sec-link fo-focus-ring mt-1.5">
            Limpiar búsqueda
          </button>
        </div>
      } @else {
        <div class="overflow-x-auto">
          <table class="tx-table">
            <thead>
              <tr>
                <th scope="col">Movimiento</th>
                <th scope="col">Categoría</th>
                <th scope="col">Atribuido a</th>
                <th scope="col">Cuenta</th>
                <th scope="col">Fecha</th>
                <th scope="col" class="text-right">Monto</th>
              </tr>
            </thead>
            <tbody>
              @for (tx of filteredTransactions(); track tx.id) {
                <tr>
                  <td>
                    <strong class="font-bold">{{ primaryTitle(tx) }}</strong>
                    @if (tx.notes) {
                      <span class="muted-note"> · {{ tx.notes }}</span>
                    }
                  </td>
                  <td>
                    @if (tx.category; as cat) {
                      <fo-pill-tag>{{ cat.name }}</fo-pill-tag>
                    } @else {
                      <span class="text-muted">Sin categoría</span>
                    }
                  </td>
                  <td>
                    {{ tx.attributedTo.fullName }}
                    @if (tx.paidBy.id !== tx.attributedTo.id) {
                      <span class="muted-note">(pagó {{ tx.paidBy.fullName }})</span>
                    }
                  </td>
                  <td class="text-muted">
                    {{ tx.account.name }}
                    @if (tx.installment; as inst) {
                      <span class="tabular-nums"> · {{ inst.number }}/{{ inst.total }}</span>
                    }
                  </td>
                  <td class="whitespace-nowrap text-muted tabular-nums">
                    {{ formatDate(tx.transactionDate) }}
                  </td>
                  <td class="text-right whitespace-nowrap">
                    <span
                      class="font-bold tabular-nums"
                      [class.text-accent]="tx.type === 'INCOME'"
                      [class.text-muted]="tx.type === 'TRANSFER'"
                    >
                      {{ signedAmount(tx) }}
                    </span>
                    @if (showLensSubtitle(tx)) {
                      <span class="muted-note block tabular-nums">
                        {{ tx.lensApproximated ? '~' : ''
                        }}{{ tx.lensAmount | money: lensUnit(tx) }}
                      </span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }
    .table-head-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px;
      margin-bottom: 10px;
    }
    .table-title {
      font-size: 13px;
      font-weight: 700;
    }
    .search-input {
      width: 210px;
      padding: 6px 11px;
      border: 1px solid var(--fo-border);
      border-radius: 9px;
      outline: none;
      background: var(--fo-bg);
      color: var(--fo-text);
      font-family: inherit;
      font-size: 12px;
    }
    .search-input::placeholder {
      color: var(--fo-text-muted);
    }
    .search-input:focus {
      border-color: var(--fo-accent);
    }
    .tx-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
      text-align: left;
    }
    .tx-table th {
      padding: 7px 10px;
      border-bottom: 1px solid var(--fo-border);
      color: var(--fo-text-muted);
      font-size: 10.5px;
      font-weight: 600;
    }
    .tx-table td {
      padding: 9px 10px;
      border-bottom: 1px solid var(--fo-border);
      color: var(--fo-text);
    }
    .tx-table td.text-muted {
      color: var(--fo-text-muted);
    }
    .tx-table tr:last-child td {
      border-bottom: none;
    }
    .tx-table tbody tr {
      transition: background 0.12s ease;
    }
    .tx-table tbody tr:hover {
      background: var(--fo-surface-2);
    }
    .muted-note {
      color: var(--fo-text-muted);
      font-size: 10.5px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementsTableComponent {
  readonly transactions = input.required<readonly TransactionItem[]>();
  readonly lens = input<Lens>('NOMINAL');

  protected readonly searchQuery = signal<string>('');

  protected readonly limitedTransactions = computed(() => this.transactions().slice(0, 10));

  protected readonly filteredTransactions = computed<readonly TransactionItem[]>(() => {
    const query = normalizeSearch(this.searchQuery());
    const items = this.limitedTransactions();
    if (!query) {
      return items;
    }

    return items.filter((tx) => {
      const installmentText = tx.installment
        ? `cuota ${tx.installment.number}/${tx.installment.total}`
        : '';
      const haystack = normalizeSearch(
        [
          tx.description ?? '',
          tx.notes ?? '',
          tx.category?.name ?? '',
          tx.attributedTo.fullName,
          tx.paidBy.fullName,
          tx.account.name,
          tx.currencyCode,
          installmentText,
        ].join(' '),
      );
      return haystack.includes(query);
    });
  });

  protected onSearchInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.searchQuery.set(target?.value ?? '');
  }

  protected clearSearch(): void {
    this.searchQuery.set('');
  }

  protected primaryTitle(tx: TransactionItem): string {
    return tx.description?.trim() || tx.category?.name || transactionTypeLabel(tx.type);
  }

  protected formatDate(isoDate: string): string {
    return formatShortIsoDate(isoDate);
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
