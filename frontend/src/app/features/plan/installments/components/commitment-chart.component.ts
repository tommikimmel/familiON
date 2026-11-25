import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../../shared/ui';
import {
  CurrencyPendingTotal,
  InstallmentCommitment,
} from '../data-access/installments.models';
import {
  CommitmentBarBucket,
  buildCommitmentBuckets,
} from '../utils/installments-format';

@Component({
  selector: 'fo-commitment-chart',
  standalone: true,
  imports: [CardComponent, MoneyPipe],
  template: `
    <fo-card padding="lg">
      <div class="space-y-4">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p class="fo-kpi-label">Total pendiente</p>
            @if (pendingTotals().length > 0) {
              <div class="mt-1 flex flex-wrap items-baseline gap-x-4 gap-y-1">
                @for (total of pendingTotals(); track total.currencyCode; let isFirst = $first) {
                  <span
                    data-testid="installments-total-pending"
                    class="tabular-nums"
                    [class.text-2xl]="isFirst"
                    [class.font-semibold]="isFirst"
                    [class.text-text]="isFirst"
                    [class.text-base]="!isFirst"
                    [class.font-medium]="!isFirst"
                    [class.text-muted]="!isFirst"
                  >
                    {{ total.amount | money: total.currencyCode }}
                  </span>
                }
              </div>
            } @else {
              <p
                data-testid="installments-total-pending"
                class="fo-kpi-val mt-1 text-text"
              >
                {{ 0 | money: baseCurrency() }}
              </p>
            }
          </div>

          <div class="flex flex-col items-end gap-2">
            <h2 class="text-[12.5px] font-bold text-text">Lo que ya está comprometido</h2>
            @if (currencies().length > 1) {
              <div class="flex items-center gap-1" role="group" aria-label="Divisa del gráfico">
                @for (code of currencies(); track code) {
                  <button
                    type="button"
                    (click)="selectCurrency(code)"
                    [attr.aria-pressed]="activeCurrency() === code"
                    class="fo-focus-ring cursor-pointer rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors"
                    [class.border-accent]="activeCurrency() === code"
                    [class.bg-accent-soft]="activeCurrency() === code"
                    [class.text-accent]="activeCurrency() === code"
                    [class.border-border]="activeCurrency() !== code"
                    [class.text-muted]="activeCurrency() !== code"
                  >
                    {{ code }}
                  </button>
                }
              </div>
            }
          </div>
        </div>

        @if (hasAnyCommitment()) {
          <div class="space-y-2">
            <div
              class="grid h-36 grid-cols-12 items-end gap-1.5 pt-2 sm:gap-2"
              role="img"
              [attr.aria-label]="'Compromiso mensual en cuotas para los próximos 12 meses en ' + activeCurrency()"
            >
              @for (bucket of buckets(); track bucket.month) {
                @let isSelected = selectedBucket()?.month === bucket.month;
                <button
                  type="button"
                  data-testid="commitment-bar"
                  (click)="selectMonth(bucket.month)"
                  [attr.aria-pressed]="isSelected"
                  [attr.aria-label]="bucket.fullLabel + ': ' + (bucket.amount | money: activeCurrency())"
                  class="fo-focus-ring group flex h-full cursor-pointer flex-col items-center justify-end gap-1.5 rounded-lg p-0.5"
                >
                  <div
                    class="flex h-full w-full items-end justify-center rounded-[7px_7px_3px_3px] bg-surface-2/70"
                  >
                    @if (bucket.amount > 0) {
                      <div
                        class="w-full rounded-[7px_7px_3px_3px] transition-all duration-200"
                        [style.height.%]="bucket.heightPct"
                        [class.bg-accent]="isSelected || !selectedMonth()"
                        [class.bg-accent/55]="selectedMonth() && !isSelected"
                        [class.group-hover:bg-accent]="true"
                      ></div>
                    } @else {
                      <div class="h-0.5 w-full rounded-full bg-border"></div>
                    }
                  </div>
                  <span
                    class="text-[10.5px] font-semibold leading-none capitalize"
                    [class.font-semibold]="isSelected"
                    [class.text-text]="isSelected"
                    [class.text-muted]="!isSelected"
                  >
                    {{ bucket.shortLabel }}
                  </span>
                </button>
              }
            </div>

            @if (selectedBucket(); as current) {
              <div
                data-testid="commitment-selected-detail"
                class="flex items-center justify-between rounded-xl bg-surface-2 px-3 py-2 text-xs"
              >
                <span class="font-medium text-muted">{{ current.fullLabel }}</span>
                <span class="font-semibold text-text tabular-nums">
                  {{ current.amount | money: activeCurrency() }}
                </span>
              </div>
            }
          </div>
        } @else {
          <p class="rounded-xl bg-surface-2 px-3.5 py-3 text-sm text-muted">
            No tenés cuotas comprometidas para los próximos 12 meses.
          </p>
        }
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommitmentChartComponent {
  readonly commitment = input.required<readonly InstallmentCommitment[]>();
  readonly pendingTotals = input<readonly CurrencyPendingTotal[]>([]);
  readonly baseCurrency = input<string>('ARS');

  private readonly _selectedCurrency = signal<string | null>(null);
  protected readonly selectedMonth = signal<string | null>(null);

  protected readonly currencies = computed<string[]>(() => {
    const seen = new Set<string>();
    for (const item of this.commitment()) {
      const code = item.currencyCode.trim().toUpperCase();
      if (code) {
        seen.add(code);
      }
    }
    return Array.from(seen);
  });

  protected readonly activeCurrency = computed<string>(() => {
    const explicit = this._selectedCurrency();
    const available = this.currencies();
    if (explicit && available.includes(explicit)) {
      return explicit;
    }
    const base = this.baseCurrency().trim().toUpperCase();
    if (available.includes(base)) {
      return base;
    }
    return available[0] ?? base;
  });

  protected readonly buckets = computed<CommitmentBarBucket[]>(() =>
    buildCommitmentBuckets(this.commitment(), this.activeCurrency(), 12),
  );

  protected readonly hasAnyCommitment = computed<boolean>(() =>
    this.buckets().some((bucket) => bucket.amount > 0),
  );

  protected readonly selectedBucket = computed<CommitmentBarBucket | null>(() => {
    const all = this.buckets();
    const month = this.selectedMonth();
    if (month) {
      return all.find((bucket) => bucket.month === month) ?? null;
    }
    return all.find((bucket) => bucket.amount > 0) ?? null;
  });

  protected selectCurrency(currencyCode: string): void {
    this._selectedCurrency.set(currencyCode);
    this.selectedMonth.set(null);
  }

  protected selectMonth(month: string): void {
    this.selectedMonth.update((current) => (current === month ? null : month));
  }
}
