import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent, CardComponent, IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { Exchange, ExchangeOperation, PageMetadata } from '../data-access/exchanges.models';
import { formatRateExpression } from '../utils/exchange-math';

const DATE_FORMATTER = new Intl.DateTimeFormat('es-AR', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

@Component({
  selector: 'fo-exchange-history-list',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card padding="none">
      <ul class="divide-y divide-border" aria-label="Historial de operaciones">
        @for (item of exchanges(); track item.id) {
          <li
            data-testid="exchange-history-row"
            class="flex flex-col gap-3 px-4 py-3.5 transition-colors hover:bg-surface-2/45 sm:flex-row sm:items-center sm:justify-between"
          >
            <div class="min-w-0 flex-1 space-y-1">
              <div class="flex flex-wrap items-center gap-2">
                <span
                  data-testid="exchange-operation-badge"
                  class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold"
                  [class.bg-success/12]="item.operation === 'BUY'"
                  [class.text-success]="item.operation === 'BUY'"
                  [class.bg-warning/12]="item.operation === 'SELL'"
                  [class.text-warning]="item.operation === 'SELL'"
                  [class.bg-surface-2]="item.operation === 'SWAP'"
                  [class.text-muted]="item.operation === 'SWAP'"
                >
                  {{ operationLabel(item.operation) }}
                </span>

                <time [attr.datetime]="item.exchangeDate" class="text-xs text-muted tabular-nums">
                  {{ formatDate(item.exchangeDate) }}
                </time>

                <span class="text-xs text-muted">·</span>

                <span class="truncate text-xs text-muted">
                  {{ item.createdBy.fullName }}
                </span>
              </div>

              <div class="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-sm font-semibold text-text tabular-nums">
                <span>{{ item.sourceAmount | money: item.sourceCurrency }}</span>
                <span class="text-xs font-normal text-muted" aria-hidden="true">→</span>
                <span class="sr-only">por</span>
                <span>{{ item.targetAmount | money: item.targetCurrency }}</span>
              </div>

              <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                <span class="truncate">
                  {{ item.sourceAccount.name }} → {{ item.targetAccount.name }}
                </span>
                <span class="font-medium text-text/80 tabular-nums">
                  {{ rateLabel(item) }}
                </span>
                @if (hasFee(item)) {
                  <span class="tabular-nums">
                    Comisión: {{ item.feeAmount | money: feeCurrencyFor(item) }}
                  </span>
                }
              </div>

              @if (item.notes) {
                <p class="truncate text-xs text-muted italic">
                  {{ item.notes }}
                </p>
              }
            </div>

            @if (canDelete(item)) {
              <div class="flex shrink-0 items-center justify-end">
                <button
                  type="button"
                  data-testid="exchange-delete-btn"
                  [attr.aria-label]="'Eliminar operación del ' + formatDate(item.exchangeDate)"
                  (click)="deleteRequested.emit(item)"
                  class="fo-focus-ring inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-xl text-muted transition-colors hover:bg-danger/10 hover:text-danger"
                >
                  <fo-icon name="trash-2" [size]="16" />
                </button>
              </div>
            }
          </li>
        }
      </ul>

      @if (page().totalPages > 1) {
        <footer
          class="flex flex-wrap items-center justify-between gap-2 border-t border-border px-4 py-3"
        >
          <p class="text-xs text-muted tabular-nums">
            Página {{ page().number + 1 }} de {{ page().totalPages }} ·
            {{ page().totalElements }}
            {{ page().totalElements === 1 ? 'operación' : 'operaciones' }}
          </p>

          <div class="flex items-center gap-1.5">
            <fo-button
              variant="ghost"
              size="sm"
              data-testid="exchange-prev-page"
              [disabled]="loading() || page().number <= 0"
              (click)="pageChange.emit(page().number - 1)"
            >
              <fo-icon name="chevron-left" [size]="15" />
              <span>Anterior</span>
            </fo-button>

            <fo-button
              variant="ghost"
              size="sm"
              data-testid="exchange-next-page"
              [disabled]="loading() || page().number + 1 >= page().totalPages"
              (click)="pageChange.emit(page().number + 1)"
            >
              <span>Siguiente</span>
              <fo-icon name="chevron-right" [size]="15" />
            </fo-button>
          </div>
        </footer>
      }
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExchangeHistoryListComponent {
  readonly exchanges = input.required<readonly Exchange[]>();
  readonly page = input.required<PageMetadata>();
  readonly baseCurrency = input<string>('ARS');
  readonly currentUserId = input<string | null>(null);
  readonly isAdult = input<boolean>(false);
  readonly loading = input<boolean>(false);

  readonly deleteRequested = output<Exchange>();
  readonly pageChange = output<number>();

  protected operationLabel(operation: ExchangeOperation): string {
    switch (operation) {
      case 'BUY':
        return 'Compra';
      case 'SELL':
        return 'Venta';
      case 'SWAP':
        return 'Cambio';
    }
  }

  protected formatDate(isoDate: string): string {
    const parts = isoDate.split('-').map(Number);
    if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
      return isoDate;
    }
    return DATE_FORMATTER.format(new Date(Date.UTC(parts[0], parts[1] - 1, parts[2])));
  }

  protected rateLabel(exchange: Exchange): string {
    const rate = toNumber(exchange.appliedExchangeRate);
    if (exchange.operation === 'SELL') {
      return formatRateExpression(exchange.sourceCurrency, exchange.targetCurrency, rate);
    }
    return formatRateExpression(exchange.targetCurrency, exchange.sourceCurrency, rate);
  }

  protected hasFee(exchange: Exchange): boolean {
    return toNumber(exchange.feeAmount) > 0;
  }

  protected feeCurrencyFor(exchange: Exchange): string {
    return exchange.feeCurrency ?? exchange.sourceCurrency;
  }

  protected canDelete(exchange: Exchange): boolean {
    if (this.isAdult()) {
      return true;
    }
    const uid = this.currentUserId();
    return uid !== null && exchange.createdBy.id === uid;
  }
}
