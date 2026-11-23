import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CountUpDirective } from '../../../shared/directives/count-up.directive';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  formatMoneyValue,
  formatPercentValue,
  resolveDisplayUnit,
} from '../data-access/home.formatters';
import { AnalyticsSummary } from '../data-access/home.models';

@Component({
  selector: 'fo-hero-balance',
  standalone: true,
  imports: [CountUpDirective, IconComponent, MoneyPipe],
  template: `
    <section
      class="fo-hero-card relative overflow-hidden rounded-[var(--fo-radius)] border border-border bg-surface p-5 shadow-2xs"
      aria-label="Balance del período"
    >
      <div class="flex items-start justify-between gap-3">
        <div>
          <p class="text-xs font-medium tracking-wide text-muted uppercase">
            {{ summary().period.label }}
          </p>
          <p class="mt-0.5 text-xs text-muted">Balance del período</p>
        </div>

        @if (savingsLabel(); as chipText) {
          <span
            data-testid="hero-savings-chip"
            class="inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold tabular-nums"
            [class.bg-success/12]="!isNegativeSavings()"
            [class.text-success]="!isNegativeSavings()"
            [class.bg-danger/12]="isNegativeSavings()"
            [class.text-danger]="isNegativeSavings()"
          >
            {{ chipText }}
          </span>
        }
      </div>

      <p
        data-testid="hero-balance-amount"
        class="mt-3 text-3xl font-bold tracking-tight tabular-nums sm:text-4xl"
        [class.text-danger]="isNegativeBalance()"
        [class.text-text]="!isNegativeBalance()"
        [foCountUp]="balanceValue()"
        [foCountUpDuration]="countUpDuration()"
        [foCountUpFormatter]="balanceFormatter()"
      ></p>

      <div class="mt-4 grid grid-cols-2 gap-3 border-t border-border/80 pt-4">
        <div class="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-success/12 text-sm font-bold text-success"
          >
            ↑
          </span>
          <div class="min-w-0">
            <p class="text-xs text-muted">Ingresos</p>
            <p
              data-testid="hero-income-amount"
              class="truncate text-sm font-semibold text-text tabular-nums"
            >
              {{ summary().income | money: displayUnit() }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-danger/12 text-sm font-bold text-danger"
          >
            ↓
          </span>
          <div class="min-w-0">
            <p class="text-xs text-muted">Gastos</p>
            <p
              data-testid="hero-expense-amount"
              class="truncate text-sm font-semibold text-text tabular-nums"
            >
              {{ summary().expense | money: displayUnit() }}
            </p>
          </div>
        </div>
      </div>

      @if (summary().lensApproximated) {
        <p
          data-testid="hero-approximated-note"
          class="mt-3 flex items-center gap-1.5 rounded-xl bg-warning/10 px-3 py-2 text-xs text-warning"
        >
          <fo-icon name="alert-circle" [size]="14" />
          <span>Valores aproximados: falta el índice de inflación más reciente</span>
        </p>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }

    .fo-hero-card {
      background-image: radial-gradient(
        circle at 88% -10%,
        var(--fo-accent-soft) 0%,
        transparent 64%
      );
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeroBalanceComponent {
  readonly summary = input.required<AnalyticsSummary>();
  readonly countUpDuration = input<number>(280);

  protected readonly displayUnit = computed(() =>
    resolveDisplayUnit(this.summary().unit, this.summary().lens),
  );

  protected readonly balanceValue = computed(() => toNumber(this.summary().balance));

  protected readonly isNegativeBalance = computed(() => this.balanceValue() < 0);

  protected readonly isNegativeSavings = computed(
    () => toNumber(this.summary().savingsRatePct) < 0,
  );

  protected readonly savingsLabel = computed<string | null>(() => {
    const rate = this.summary().savingsRatePct;
    if (rate === null || rate.trim() === '') {
      return null;
    }
    return `Ahorro ${formatPercentValue(rate)}`;
  });

  protected readonly balanceFormatter = computed<(value: number) => string>(() => {
    const unit = this.displayUnit();
    return (value: number) => formatMoneyValue(value, unit);
  });
}
