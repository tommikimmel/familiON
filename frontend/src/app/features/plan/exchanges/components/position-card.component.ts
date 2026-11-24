import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { CurrencyPosition } from '../data-access/exchanges.models';
import { formatBaseUnitPrice, formatCurrencyUnits } from '../utils/exchange-math';

const PERCENT_FORMATTER = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

type ResultTone = 'positive' | 'negative' | 'neutral';

@Component({
  selector: 'fo-position-card',
  standalone: true,
  imports: [CardComponent, MoneyPipe],
  template: `
    <fo-card padding="md" data-testid="position-card">
      <div class="flex items-start justify-between gap-3">
        <div>
          <span
            class="inline-flex items-center rounded-full border border-border bg-surface-2 px-2.5 py-0.5 text-xs font-semibold tracking-wide text-text"
          >
            {{ position().currencyCode }}
          </span>
          <p
            data-testid="position-units"
            class="mt-2 text-2xl font-bold tracking-tight text-text tabular-nums"
          >
            {{ formattedUnits() }}
          </p>
        </div>

        @if (formattedResultPct(); as pctLabel) {
          <span
            data-testid="position-result-badge"
            class="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold tabular-nums"
            [class.bg-success/12]="resultTone() === 'positive'"
            [class.text-success]="resultTone() === 'positive'"
            [class.bg-danger/12]="resultTone() === 'negative'"
            [class.text-danger]="resultTone() === 'negative'"
            [class.bg-surface-2]="resultTone() === 'neutral'"
            [class.text-muted]="resultTone() === 'neutral'"
          >
            {{ pctLabel }}
          </span>
        }
      </div>

      <p data-testid="position-avg-rate" class="mt-1 text-sm text-muted tabular-nums">
        @if (avgRateLabel(); as avg) {
          Compraste a <strong class="font-medium text-text">{{ avg }}</strong> promedio
        } @else {
          Sin tenencia activa
        }
      </p>

      <dl class="mt-4 grid grid-cols-3 gap-2 border-t border-border/80 pt-3">
        <div>
          <dt class="text-[11px] font-medium text-muted">Cotización</dt>
          <dd data-testid="position-current-rate" class="mt-0.5 text-xs font-semibold text-text tabular-nums">
            {{ currentRateLabel() ?? 'Sin cotización' }}
          </dd>
        </div>

        <div>
          <dt class="text-[11px] font-medium text-muted">Valor actual</dt>
          <dd data-testid="position-current-value" class="mt-0.5 text-xs font-semibold text-text tabular-nums">
            @if (position().currentValueBase !== null) {
              {{ position().currentValueBase | money: baseCurrency() }}
            } @else {
              —
            }
          </dd>
        </div>

        <div class="text-right">
          <dt class="text-[11px] font-medium text-muted">Resultado</dt>
          <dd
            data-testid="position-result-amount"
            class="mt-0.5 text-xs font-semibold tabular-nums"
            [class.text-success]="resultTone() === 'positive'"
            [class.text-danger]="resultTone() === 'negative'"
            [class.text-muted]="resultTone() === 'neutral'"
          >
            @if (formattedResultAmount(); as resultAmount) {
              {{ resultAmount }}
            } @else {
              —
            }
          </dd>
        </div>
      </dl>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PositionCardComponent {
  readonly position = input.required<CurrencyPosition>();
  readonly baseCurrency = input<string>('ARS');

  private readonly moneyPipe = new MoneyPipe();

  protected readonly formattedUnits = computed(() =>
    formatCurrencyUnits(this.position().units, this.position().currencyCode),
  );

  protected readonly avgRateLabel = computed<string | null>(() => {
    const pos = this.position();
    if (pos.weightedAvgRate === null || toNumber(pos.units) <= 0) {
      return null;
    }
    const avg = toNumber(pos.weightedAvgRate);
    if (avg <= 0) {
      return null;
    }
    return formatBaseUnitPrice(avg, this.baseCurrency(), { compactInteger: true });
  });

  protected readonly currentRateLabel = computed<string | null>(() => {
    const raw = this.position().currentRate;
    if (raw === null) {
      return null;
    }
    const rate = toNumber(raw);
    if (rate <= 0) {
      return null;
    }
    return formatBaseUnitPrice(rate, this.baseCurrency(), { compactInteger: true });
  });

  protected readonly resultNumeric = computed<number | null>(() => {
    const raw = this.position().unrealizedResultBase;
    if (raw === null) {
      return null;
    }
    return toNumber(raw);
  });

  protected readonly resultTone = computed<ResultTone>(() => {
    const value = this.resultNumeric();
    if (value === null || Math.abs(value) < 0.005) {
      return 'neutral';
    }
    return value > 0 ? 'positive' : 'negative';
  });

  protected readonly formattedResultAmount = computed<string | null>(() => {
    const value = this.resultNumeric();
    if (value === null) {
      return null;
    }
    const formattedAbs = this.moneyPipe.transform(Math.abs(value), this.baseCurrency());
    if (value > 0.004) {
      return `+${formattedAbs}`;
    }
    if (value < -0.004) {
      return `-${formattedAbs}`;
    }
    return formattedAbs;
  });

  protected readonly formattedResultPct = computed<string | null>(() => {
    const raw = this.position().unrealizedResultPct;
    if (raw === null) {
      return null;
    }
    const pct = toNumber(raw);
    const sign = pct > 0.04 ? '+' : pct < -0.04 ? '-' : '';
    return `${sign}${PERCENT_FORMATTER.format(Math.abs(pct))}%`;
  });
}
