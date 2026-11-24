import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { View } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  buildExpenseComparison,
  buildIncomeComparison,
  buildInstallmentCommitment,
  evaluateRealVariation,
  formatPercentValue,
  resolveDisplayUnit,
} from '../data-access/analytics.logic';
import { AnalyticsSummary } from '../data-access/analytics.models';

@Component({
  selector: 'fo-analytics-summary',
  standalone: true,
  imports: [CardComponent, IconComponent, MoneyPipe],
  template: `
    @if (summary(); as data) {
      <div class="space-y-4">
        @if (data.lensApproximated) {
          <div
            role="status"
            data-testid="lens-approximated-banner"
            class="flex items-start gap-2.5 rounded-xl border border-warning/35 bg-warning/10 px-3.5 py-2.5 text-xs text-text"
          >
            <fo-icon name="alert-circle" [size]="16" class="mt-0.5 shrink-0 text-warning" />
            <span>
              Algunos valores son aproximados porque todavía no está publicado el índice o
              cotización más reciente para este período.
            </span>
          </div>
        }

        <!-- 4 KPI summary cards -->
        <div class="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
          <!-- Income -->
          <fo-card padding="md" data-testid="summary-income-card">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs font-medium text-muted">Ingresos</span>
              <span
                class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-success/12 text-success"
                aria-hidden="true"
              >
                <fo-icon name="trending-up" [size]="14" />
              </span>
            </div>
            <p class="mt-2 truncate text-xl font-bold tracking-tight text-text tabular-nums sm:text-2xl">
              {{ data.income | money: displayUnit() }}
            </p>
            @if (incomeComparison(); as cmp) {
              <p
                data-testid="income-comparison"
                class="mt-1.5 text-xs font-medium tabular-nums"
                [class.text-success]="cmp.tone === 'success'"
                [class.text-danger]="cmp.tone === 'danger'"
                [class.text-muted]="cmp.tone === 'neutral'"
              >
                {{ cmp.label }}
              </p>
            } @else {
              <p class="mt-1.5 text-xs text-muted">Período seleccionado</p>
            }
          </fo-card>

          <!-- Expense -->
          <fo-card padding="md" data-testid="summary-expense-card">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs font-medium text-muted">Gastos</span>
              <span
                class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-danger/12 text-danger"
                aria-hidden="true"
              >
                <fo-icon name="arrow-left-right" [size]="14" />
              </span>
            </div>
            <p class="mt-2 truncate text-xl font-bold tracking-tight text-text tabular-nums sm:text-2xl">
              {{ data.expense | money: displayUnit() }}
            </p>
            @if (expenseComparison(); as cmp) {
              <p
                data-testid="expense-comparison"
                class="mt-1.5 text-xs font-medium tabular-nums"
                [class.text-success]="cmp.tone === 'success'"
                [class.text-danger]="cmp.tone === 'danger'"
                [class.text-muted]="cmp.tone === 'neutral'"
              >
                {{ cmp.label }}
              </p>
            } @else {
              <p class="mt-1.5 text-xs text-muted">Sin base previa para comparar</p>
            }
          </fo-card>

          <!-- Balance -->
          <fo-card padding="md" data-testid="summary-balance-card">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs font-medium text-muted">Balance</span>
              <span
                class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-accent-soft text-accent"
                aria-hidden="true"
              >
                <fo-icon name="wallet" [size]="14" />
              </span>
            </div>
            <p
              class="mt-2 truncate text-xl font-bold tracking-tight tabular-nums sm:text-2xl"
              [class.text-danger]="isNegativeBalance()"
              [class.text-text]="!isNegativeBalance()"
            >
              {{ data.balance | money: displayUnit() }}
            </p>
            <p class="mt-1.5 text-xs text-muted">Resultado neto del período</p>
          </fo-card>

          <!-- Savings rate -->
          <fo-card padding="md" data-testid="summary-savings-card">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs font-medium text-muted">Tasa de ahorro</span>
              <span
                class="inline-flex h-7 w-7 items-center justify-center rounded-full bg-surface-2 text-accent"
                aria-hidden="true"
              >
                <fo-icon name="piggy-bank" [size]="14" />
              </span>
            </div>
            @if (savingsRateLabel(); as savingsLabel) {
              <p
                class="mt-2 truncate text-xl font-bold tracking-tight tabular-nums sm:text-2xl"
                [class.text-success]="!isNegativeSavings()"
                [class.text-danger]="isNegativeSavings()"
              >
                {{ savingsLabel }}
              </p>
              <p class="mt-1.5 text-xs text-muted">Sobre ingresos del período</p>
            } @else {
              <p class="mt-2 text-lg font-semibold text-muted">Sin datos</p>
              <p class="mt-1.5 text-xs text-muted">Requiere ingresos registrados</p>
            }
          </fo-card>
        </div>

        <!-- Traffic light & Installment commitment -->
        @if (realVariation() || installmentCommitment()) {
          <div class="grid grid-cols-1 gap-3 sm:gap-4 md:grid-cols-2">
            @if (realVariation(); as variation) {
              <div
                data-testid="real-variation-card"
                [attr.data-tone]="variation.tone"
                class="flex items-start gap-3 rounded-[var(--fo-radius)] border p-4"
                [class.border-success/35]="variation.tone === 'green'"
                [class.bg-success/8]="variation.tone === 'green'"
                [class.border-warning/35]="variation.tone === 'amber'"
                [class.bg-warning/8]="variation.tone === 'amber'"
                [class.border-danger/35]="variation.tone === 'red'"
                [class.bg-danger/8]="variation.tone === 'red'"
              >
                <span
                  aria-hidden="true"
                  class="mt-1 h-3 w-3 shrink-0 rounded-full"
                  [class.bg-success]="variation.tone === 'green'"
                  [class.bg-warning]="variation.tone === 'amber'"
                  [class.bg-danger]="variation.tone === 'red'"
                ></span>

                <div class="min-w-0 space-y-1">
                  <span
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold"
                    [class.bg-success/15]="variation.tone === 'green'"
                    [class.text-success]="variation.tone === 'green'"
                    [class.bg-warning/15]="variation.tone === 'amber'"
                    [class.text-warning]="variation.tone === 'amber'"
                    [class.bg-danger/15]="variation.tone === 'red'"
                    [class.text-danger]="variation.tone === 'red'"
                  >
                    {{ variation.badge }}
                  </span>
                  <p class="text-sm font-medium text-text tabular-nums">
                    {{ variation.message }}
                  </p>
                </div>
              </div>
            }

            @if (installmentCommitment(); as commitment) {
              <fo-card padding="md" data-testid="installment-commitment-card">
                <div class="flex items-start gap-3">
                  <span
                    aria-hidden="true"
                    class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-2 text-accent"
                  >
                    <fo-icon name="credit-card" [size]="16" />
                  </span>
                  <div class="min-w-0 flex-1 space-y-2">
                    <p class="text-sm font-medium text-text tabular-nums">
                      {{ commitment.message }}
                    </p>
                    <div
                      role="progressbar"
                      aria-label="Porcentaje del ingreso comprometido en cuotas"
                      [attr.aria-valuenow]="commitment.pct"
                      aria-valuemin="0"
                      aria-valuemax="100"
                      class="h-2 w-full overflow-hidden rounded-full bg-surface-2"
                    >
                      <div
                        class="h-full rounded-full transition-all duration-300"
                        [class.bg-accent]="commitment.tone === 'accent'"
                        [class.bg-warning]="commitment.tone === 'warning'"
                        [class.bg-danger]="commitment.tone === 'danger'"
                        [style.width.%]="commitment.barWidth"
                      ></div>
                    </div>
                  </div>
                </div>
              </fo-card>
            }
          </div>
        }
      </div>
    } @else {
      <fo-card padding="md">
        <p class="py-4 text-center text-sm text-muted">
          No pudimos cargar el resumen para este período.
        </p>
      </fo-card>
    }
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsSummaryComponent {
  readonly summary = input<AnalyticsSummary | null>(null);
  readonly view = input<View>('PERSONAL');

  protected readonly displayUnit = computed(() => {
    const current = this.summary();
    return current ? resolveDisplayUnit(current.unit, current.lens) : 'ARS';
  });

  protected readonly isNegativeBalance = computed(() => toNumber(this.summary()?.balance) < 0);

  protected readonly savingsRateLabel = computed<string | null>(() => {
    const raw = this.summary()?.savingsRatePct;
    if (raw === null || raw === undefined || raw.trim() === '') {
      return null;
    }
    return formatPercentValue(raw);
  });

  protected readonly isNegativeSavings = computed(
    () => toNumber(this.summary()?.savingsRatePct) < 0,
  );

  protected readonly expenseComparison = computed(() => {
    const data = this.summary();
    if (!data) {
      return null;
    }
    return buildExpenseComparison(
      data.expense,
      data.previous.expense,
      data.period.granularity,
      data.period.from,
    );
  });

  protected readonly incomeComparison = computed(() => {
    const data = this.summary();
    if (!data) {
      return null;
    }
    return buildIncomeComparison(
      data.income,
      data.previous.income,
      data.period.granularity,
      data.period.from,
    );
  });

  protected readonly realVariation = computed(() => {
    const data = this.summary();
    if (!data) {
      return null;
    }
    return evaluateRealVariation(data.realVariationPct, data.period.granularity, this.view());
  });

  protected readonly installmentCommitment = computed(() =>
    buildInstallmentCommitment(this.summary()?.installmentCommitmentPct),
  );
}
