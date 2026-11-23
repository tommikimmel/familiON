import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
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
  imports: [CardComponent, IconComponent, MoneyPipe, RouterLink],
  template: `
    <fo-card padding="none">
      <div
        class="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-3.5"
      >
        <div>
          <h2 class="text-base font-semibold text-text">Últimos movimientos</h2>
          <p class="text-xs text-muted">
            Mostrando {{ filteredTransactions().length }} de {{ limitedTransactions().length }}
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <div class="relative w-72">
            <label for="home-movements-search" class="sr-only">
              Buscar en los últimos movimientos
            </label>
            <input
              id="home-movements-search"
              data-testid="movements-search-input"
              type="search"
              [value]="searchQuery()"
              (input)="onSearchInput($event)"
              placeholder="Buscar movimiento, categoría o cuenta..."
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 py-1.5 pr-8 pl-3 text-sm text-text placeholder:text-muted"
            />
            @if (searchQuery().length > 0) {
              <button
                type="button"
                aria-label="Limpiar búsqueda"
                (click)="clearSearch()"
                class="fo-focus-ring absolute top-1/2 right-2 -translate-y-1/2 cursor-pointer rounded-md p-0.5 text-muted hover:text-text"
              >
                <fo-icon name="x" [size]="14" />
              </button>
            }
          </div>

          <a
            routerLink="/transactions"
            class="fo-focus-ring inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-accent transition-colors hover:bg-accent-soft"
          >
            <span>Ver todos</span>
            <fo-icon name="chevron-right" [size]="14" />
          </a>
        </div>
      </div>

      @if (filteredTransactions().length === 0) {
        <div class="px-5 py-10 text-center">
          <p class="text-sm text-muted">
            No encontramos movimientos que coincidan con «{{ searchQuery() }}».
          </p>
          <button
            type="button"
            (click)="clearSearch()"
            class="fo-focus-ring mt-2 cursor-pointer rounded-lg px-3 py-1.5 text-xs font-semibold text-accent hover:bg-accent-soft"
          >
            Limpiar búsqueda
          </button>
        </div>
      } @else {
        <div class="overflow-x-auto">
          <table class="w-full border-collapse text-left text-sm">
            <thead>
              <tr class="border-b border-border bg-surface-2/50 text-xs font-medium text-muted">
                <th scope="col" class="px-5 py-2.5">Movimiento</th>
                <th scope="col" class="px-3 py-2.5">Categoría</th>
                <th scope="col" class="px-3 py-2.5">Atribuido a / pagó</th>
                <th scope="col" class="px-3 py-2.5">Cuenta / Cuota</th>
                <th scope="col" class="px-3 py-2.5">Fecha</th>
                <th scope="col" class="px-5 py-2.5 text-right">Monto</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border/70">
              @for (tx of filteredTransactions(); track tx.id) {
                <tr class="transition-colors hover:bg-surface-2/40">
                  <td class="px-5 py-3">
                    <div class="flex items-center gap-3">
                      <span
                        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                        [style.background-color]="iconBg(tx)"
                        [style.color]="iconColor(tx)"
                      >
                        <fo-icon [name]="tx.category?.icon ?? fallbackIcon(tx.type)" [size]="16" />
                      </span>
                      <div class="min-w-0">
                        <p class="truncate font-medium text-text">{{ primaryTitle(tx) }}</p>
                        @if (tx.notes) {
                          <p class="max-w-52 truncate text-xs text-muted">{{ tx.notes }}</p>
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
                      <span class="text-muted">Sin categoría</span>
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
                      [class.text-success]="tx.type === 'INCOME'"
                      [class.text-danger]="tx.type === 'EXPENSE'"
                      [class.text-muted]="tx.type === 'TRANSFER'"
                    >
                      {{ signedAmount(tx) }}
                    </p>
                    @if (showLensSubtitle(tx)) {
                      <p class="text-xs text-muted tabular-nums">
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
