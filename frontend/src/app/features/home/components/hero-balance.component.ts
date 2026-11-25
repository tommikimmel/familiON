import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { View } from '../../../core/state/view-context.store';
import { CountUpDirective } from '../../../shared/directives/count-up.directive';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PillTagComponent } from '../../../shared/ui/pill-tag/pill-tag.component';
import { ProgressBarComponent } from '../../../shared/ui/progress-bar/progress-bar.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  formatPercentValue,
  formatSignedRealVariation,
  formatWholeMoney,
  resolveDisplayUnit,
} from '../data-access/home.formatters';
import { AnalyticsSummary } from '../data-access/home.models';

/** Mobile hero balance card (mock `.hero-card`). */
@Component({
  selector: 'fo-hero-balance',
  standalone: true,
  imports: [CardComponent, CountUpDirective, IconComponent, PillTagComponent, ProgressBarComponent],
  template: `
    <fo-card [glow]="true">
      <section aria-label="Balance del período">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <p class="hero-label">
              {{ title() }}
              @if (summary().lens === 'CONSTANT_BASE') {
                <fo-pill-tag [accent]="true">A plata de hoy</fo-pill-tag>
              }
            </p>
            <p
              data-testid="hero-balance-amount"
              class="hero-amount"
              [class.text-danger]="isNegativeBalance()"
              [foCountUp]="balanceValue()"
              [foCountUpDuration]="countUpDuration()"
              [foCountUpFormatter]="balanceFormatter()"
            ></p>
          </div>
          @if (badgeLabel(); as badge) {
            <fo-pill-tag data-testid="hero-savings-chip" class="shrink-0 tabular-nums">
              {{ badge }}
            </fo-pill-tag>
          }
        </div>

        <fo-progress-bar class="mt-2.5" [value]="expenseRatio()" ariaLabel="Gastos sobre ingresos" />

        <div class="hero-metrics">
          <div class="min-w-0">
            <p class="mini-stat-label">Ingresos</p>
            <p data-testid="hero-income-amount" class="mini-stat-val">{{ incomeLabel() }}</p>
          </div>
          <div class="min-w-0">
            <p class="mini-stat-label">Gastos ({{ expenseRatio() }}% ingr.)</p>
            <p data-testid="hero-expense-amount" class="mini-stat-val">{{ expenseLabel() }}</p>
          </div>
        </div>

        @if (summary().lensApproximated) {
          <p
            data-testid="hero-approximated-note"
            class="mt-2.5 flex items-center gap-1.5 rounded-[10px] bg-warning/10 px-2.5 py-1.5 text-[11px] text-warning"
          >
            <fo-icon name="alert-circle" [size]="13" />
            <span>Valores aproximados: falta el índice de inflación más reciente</span>
          </p>
        }
      </section>
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }
    .hero-label {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 11px;
      font-weight: 500;
      color: var(--fo-text-muted);
    }
    .hero-amount {
      margin-top: 2px;
      font-size: 28px;
      font-weight: 700;
      line-height: 1.12;
      letter-spacing: -0.03em;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .hero-metrics {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
      margin-top: 11px;
      padding-top: 10px;
      border-top: 1px solid var(--fo-border);
    }
    .mini-stat-label {
      font-size: 10.5px;
      color: var(--fo-text-muted);
    }
    .mini-stat-val {
      margin-top: 1px;
      overflow: hidden;
      font-size: 13.5px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeroBalanceComponent {
  readonly summary = input.required<AnalyticsSummary>();
  readonly view = input<View>('PERSONAL');
  readonly countUpDuration = input<number>(280);

  protected readonly title = computed(() =>
    this.view() === 'FAMILY' ? 'Balance familiar' : 'Mi balance',
  );

  protected readonly displayUnit = computed(() =>
    resolveDisplayUnit(this.summary().unit, this.summary().lens),
  );

  protected readonly balanceValue = computed(() => toNumber(this.summary().balance));

  protected readonly isNegativeBalance = computed(() => this.balanceValue() < 0);

  protected readonly incomeLabel = computed(() =>
    formatWholeMoney(this.summary().income, this.displayUnit()),
  );

  protected readonly expenseLabel = computed(() =>
    formatWholeMoney(this.summary().expense, this.displayUnit()),
  );

  /** Expense as a share of income, capped at 100 (mock `ratio`). */
  protected readonly expenseRatio = computed(() => {
    const income = toNumber(this.summary().income);
    const expense = toNumber(this.summary().expense);
    if (income <= 0) {
      return expense > 0 ? 100 : 0;
    }
    return Math.min(100, Math.round((expense / income) * 100));
  });

  /** Real variation (mock `-5% real`), falling back to the savings rate. */
  protected readonly badgeLabel = computed<string | null>(() => {
    const real = formatSignedRealVariation(this.summary().realVariationPct);
    if (real) {
      return real;
    }
    const rate = this.summary().savingsRatePct;
    if (rate === null || rate.trim() === '') {
      return null;
    }
    return `Ahorro ${formatPercentValue(rate)}`;
  });

  protected readonly balanceFormatter = computed<(value: number) => string>(() => {
    const unit = this.displayUnit();
    return (value: number) => formatWholeMoney(value, unit);
  });
}
