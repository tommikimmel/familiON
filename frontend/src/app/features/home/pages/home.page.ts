import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { QuickAddLauncher } from '../../../core/layout/quick-add-launcher.service';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { CountUpDirective } from '../../../shared/directives/count-up.directive';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { PeriodLabelPipe } from '../../../shared/pipes/period-label.pipe';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { toNumber } from '../../../shared/utils/decimal';
import { BreakdownCardComponent } from '../components/breakdown-card.component';
import { EvolutionChartComponent } from '../components/evolution-chart.component';
import { HeroBalanceComponent } from '../components/hero-balance.component';
import { KpiBadgeTone, KpiCardComponent } from '../components/kpi-card.component';
import { MovementsTableComponent } from '../components/movements-table.component';
import { RecentListComponent } from '../components/recent-list.component';
import {
  formatMoneyValue,
  formatPercentValue,
  formatRateLabel,
  formatRatePrice,
  pickPrimaryRate,
  resolveDisplayUnit,
} from '../data-access/home.formatters';
import { IndexValue } from '../data-access/home.models';
import { HomeStore } from '../data-access/home.store';

@Component({
  selector: 'fo-home-page',
  standalone: true,
  providers: [HomeStore],
  imports: [
    BreakdownCardComponent,
    ButtonComponent,
    CardComponent,
    CountUpDirective,
    EmptyStateComponent,
    EvolutionChartComponent,
    HeroBalanceComponent,
    IconComponent,
    KpiCardComponent,
    MoneyPipe,
    MovementsTableComponent,
    PeriodLabelPipe,
    RecentListComponent,
    SkeletonComponent,
  ],
  template: `
    <div class="mx-auto max-w-7xl space-y-5">
      <h1 class="sr-only">Inicio</h1>

      @if (store.demoMode()) {
        <div
          data-testid="home-demo-banner"
          class="flex items-center justify-between gap-2 rounded-xl border border-accent/30 bg-accent-soft px-3.5 py-2 text-xs font-medium text-accent"
        >
          <span class="flex items-center gap-2">
            <fo-icon name="sparkles" [size]="15" />
            <span>Modo demo activo · Visualizando datos de ejemplo</span>
          </span>
        </div>
      }

      @if (store.loading()) {
        @if (store.isDesktop()) {
          <div data-testid="home-desktop-skeleton" class="space-y-5" aria-busy="true">
            <div class="grid grid-cols-3 gap-5">
              @for (item of [1, 2, 3]; track item) {
                <fo-card padding="md">
                  <fo-skeleton width="45%" height="0.875rem" />
                  <div class="mt-4">
                    <fo-skeleton width="70%" height="2rem" />
                  </div>
                  <div class="mt-4 space-y-2">
                    <fo-skeleton width="100%" height="0.75rem" />
                    <fo-skeleton width="80%" height="0.75rem" />
                  </div>
                </fo-card>
              }
            </div>

            <div class="grid grid-cols-12 gap-5">
              <div class="col-span-7">
                <fo-card padding="md">
                  <fo-skeleton width="35%" height="1rem" />
                  <div class="mt-4">
                    <fo-skeleton height="230px" rounded="lg" />
                  </div>
                </fo-card>
              </div>
              <div class="col-span-5">
                <fo-card padding="md">
                  <fo-skeleton width="45%" height="1rem" />
                  <div class="mt-4 flex justify-center">
                    <fo-skeleton width="180px" height="180px" rounded="full" />
                  </div>
                </fo-card>
              </div>
            </div>

            <fo-card padding="md">
              <fo-skeleton width="30%" height="1rem" />
              <div class="mt-4 space-y-3">
                @for (row of [1, 2, 3, 4]; track row) {
                  <fo-skeleton height="2.5rem" rounded="md" />
                }
              </div>
            </fo-card>
          </div>
        } @else {
          <div data-testid="home-mobile-skeleton" class="space-y-4" aria-busy="true">
            <fo-card padding="md">
              <fo-skeleton width="40%" height="0.75rem" />
              <div class="mt-3">
                <fo-skeleton width="65%" height="2.25rem" />
              </div>
              <div class="mt-4 grid grid-cols-2 gap-3">
                <fo-skeleton height="2.5rem" />
                <fo-skeleton height="2.5rem" />
              </div>
            </fo-card>

            <fo-skeleton width="11rem" height="2.25rem" rounded="full" />

            <fo-card padding="md">
              <fo-skeleton width="50%" height="1rem" />
              <div class="mt-4 flex justify-center">
                <fo-skeleton width="176px" height="176px" rounded="full" />
              </div>
            </fo-card>

            <fo-card padding="md">
              <fo-skeleton width="45%" height="1rem" />
              <div class="mt-4 space-y-3">
                @for (row of [1, 2, 3, 4]; track row) {
                  <fo-skeleton height="2.75rem" />
                }
              </div>
            </fo-card>
          </div>
        }
      } @else if (store.error(); as errorMessage) {
        <fo-card padding="lg" data-testid="home-error-state">
          <div class="flex flex-col items-center py-6 text-center">
            <div
              class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-danger/12 text-danger"
            >
              <fo-icon name="alert-circle" [size]="24" />
            </div>
            <h2 class="text-base font-semibold text-text">No pudimos cargar el inicio</h2>
            <p class="mt-1 max-w-sm text-sm text-muted">{{ errorMessage }}</p>
            <div class="mt-4">
              <fo-button data-testid="home-retry-btn" (click)="store.reload()">
                Reintentar
              </fo-button>
            </div>
          </div>
        </fo-card>
      } @else if (store.payload(); as payload) {
        @if (isCompletelyUnavailable()) {
          <fo-card padding="lg" data-testid="home-partial-error">
            <div class="flex flex-col items-center py-6 text-center">
              <div
                class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-warning/12 text-warning"
              >
                <fo-icon name="alert-circle" [size]="24" />
              </div>
              <h2 class="text-base font-semibold text-text">Datos temporalmente no disponibles</h2>
              <p class="mt-1 max-w-sm text-sm text-muted">
                No pudimos obtener las secciones del inicio en este momento.
              </p>
              <div class="mt-4">
                <fo-button (click)="store.reload()">Reintentar</fo-button>
              </div>
            </div>
          </fo-card>
        } @else if (isPeriodEmpty()) {
          <div class="space-y-4">
            @if (!store.isDesktop()) {
              <div class="flex items-center justify-between rounded-full border border-border bg-surface px-2 py-1">
                <button
                  type="button"
                  aria-label="Período anterior"
                  (click)="viewContext.shiftPeriod(-1)"
                  class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted hover:bg-surface-2 hover:text-text"
                >
                  <fo-icon name="chevron-left" [size]="16" />
                </button>
                <button
                  type="button"
                  title="Volver a hoy"
                  (click)="viewContext.resetToToday()"
                  class="fo-focus-ring cursor-pointer rounded-full px-3 py-1 text-xs font-semibold text-text tabular-nums hover:bg-surface-2"
                >
                  {{ viewContext.anchorDate() | periodLabel: viewContext.granularity() }}
                </button>
                <button
                  type="button"
                  aria-label="Período siguiente"
                  (click)="viewContext.shiftPeriod(1)"
                  class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted hover:bg-surface-2 hover:text-text"
                >
                  <fo-icon name="chevron-right" [size]="16" />
                </button>
              </div>
            }

            <fo-empty-state
              data-testid="home-empty-state"
              icon="wallet"
              title="Sin movimientos en este período"
              description="Todavía no hay movimientos en este período. Toca + para cargar el primero."
            >
              <fo-button (click)="quickAdd.open()">Nuevo movimiento</fo-button>
            </fo-empty-state>
          </div>
        } @else if (store.isDesktop()) {
          <div data-testid="home-desktop-layout" class="space-y-5">
            <!-- Row 1: 3 KPI cards -->
            <div class="grid grid-cols-3 gap-5">
              <!-- KPI 1: Balance (with glow) -->
              <fo-kpi-card
                title="Balance"
                [subtitle]="payload.summary?.period?.label ?? periodFallbackLabel()"
                [badge]="savingsChip()"
                [badgeTone]="savingsTone()"
                icon="wallet"
                [glow]="true"
              >
                @if (payload.summary; as summary) {
                  <div>
                    <p
                      data-testid="desktop-kpi-balance"
                      class="text-3xl font-bold tracking-tight tabular-nums"
                      [class.text-danger]="isNegativeBalance()"
                      [class.text-text]="!isNegativeBalance()"
                      [foCountUp]="balanceNumeric()"
                      [foCountUpFormatter]="balanceFormatter()"
                    ></p>

                    @if (summary.lensApproximated) {
                      <p class="mt-3 flex items-center gap-1.5 text-xs text-warning">
                        <fo-icon name="alert-circle" [size]="14" />
                        <span>Valores aproximados: falta el índice de inflación más reciente</span>
                      </p>
                    } @else {
                      <p class="mt-3 text-xs text-muted">
                        Resultado neto del período seleccionado
                      </p>
                    }
                  </div>
                } @else {
                  <p class="text-sm text-muted">Resumen no disponible.</p>
                }
              </fo-kpi-card>

              <!-- KPI 2: Gastos vs Ingresos (comparative bar) -->
              <fo-kpi-card
                title="Gastos vs Ingresos"
                [subtitle]="expenseRatioSubtitle()"
                icon="arrow-left-right"
              >
                @if (payload.summary; as summary) {
                  <div class="space-y-3">
                    <div class="space-y-1">
                      <div class="flex items-center justify-between text-xs">
                        <span class="flex items-center gap-1.5 font-medium text-muted">
                          <span class="font-bold text-success" aria-hidden="true">↑</span>
                          Ingresos
                        </span>
                        <span class="font-semibold text-text tabular-nums">
                          {{ summary.income | money: summaryDisplayUnit() }}
                        </span>
                      </div>
                      <div class="h-2 w-full overflow-hidden rounded-full bg-surface-2">
                        <div
                          class="h-full rounded-full bg-success/80 transition-all duration-300"
                          [style.width.%]="incomeBarPct()"
                        ></div>
                      </div>
                    </div>

                    <div class="space-y-1">
                      <div class="flex items-center justify-between text-xs">
                        <span class="flex items-center gap-1.5 font-medium text-muted">
                          <span class="font-bold text-danger" aria-hidden="true">↓</span>
                          Gastos
                        </span>
                        <span class="font-semibold text-text tabular-nums">
                          {{ summary.expense | money: summaryDisplayUnit() }}
                        </span>
                      </div>
                      <div class="h-2 w-full overflow-hidden rounded-full bg-surface-2">
                        <div
                          class="h-full rounded-full bg-danger/80 transition-all duration-300"
                          [style.width.%]="expenseBarPct()"
                        ></div>
                      </div>
                    </div>
                  </div>
                } @else {
                  <p class="text-sm text-muted">Comparativa no disponible.</p>
                }
              </fo-kpi-card>

              <!-- KPI 3: Divisas + Cuotas -->
              <fo-kpi-card
                title="Divisas y cuotas"
                [subtitle]="primaryRateDateLabel()"
                icon="credit-card"
              >
                <div class="space-y-3">
                  @if (primaryRate(); as rate) {
                    <div
                      data-testid="desktop-primary-rate"
                      class="flex items-baseline justify-between gap-2"
                    >
                      <span class="text-sm font-medium text-muted">{{ rateLabel(rate) }}</span>
                      <span class="text-xl font-bold text-text tabular-nums">
                        {{ ratePrice(rate) }}
                      </span>
                    </div>
                  } @else {
                    <p class="text-xs text-muted">Sin cotizaciones disponibles.</p>
                  }

                  <div class="border-t border-border/80 pt-3">
                    @if (installmentPctLabel(); as commitmentPct) {
                      <p
                        data-testid="desktop-installment-commitment"
                        class="text-xs font-medium text-text tabular-nums"
                      >
                        Comprometido en cuotas:
                        <strong class="font-semibold">{{ commitmentPct }}</strong> del ingreso
                      </p>
                      <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-surface-2">
                        <div
                          class="h-full rounded-full transition-all duration-300"
                          [class.bg-accent]="installmentNumeric() <= 25"
                          [class.bg-warning]="
                            installmentNumeric() > 25 && installmentNumeric() <= 40
                          "
                          [class.bg-danger]="installmentNumeric() > 40"
                          [style.width.%]="installmentBarWidth()"
                        ></div>
                      </div>
                    } @else {
                      <p class="text-xs text-muted">
                        Sin compromisos en cuotas en este período.
                      </p>
                    }
                  </div>
                </div>
              </fo-kpi-card>
            </div>

            <!-- Row 2: 6-month evolution + Breakdown donut -->
            <div class="grid grid-cols-12 gap-5">
              <div class="col-span-7">
                <fo-evolution-chart
                  [series]="store.series()"
                  [loading]="store.seriesLoading()"
                />
              </div>
              <div class="col-span-5">
                <fo-breakdown-card
                  [byCategory]="payload.byCategory"
                  [byPerson]="payload.byPerson"
                  [view]="viewContext.view()"
                  [lens]="viewContext.lens()"
                />
              </div>
            </div>

            <!-- Row 3: Movements table with instant search -->
            @if (payload.recentTransactions; as txs) {
              @if (txs.length > 0) {
                <fo-movements-table [transactions]="txs" [lens]="viewContext.lens()" />
              } @else {
                <fo-empty-state
                  icon="wallet"
                  title="Sin movimientos en este período"
                  description="Todavía no hay movimientos en este período. Toca + para cargar el primero."
                />
              }
            } @else {
              <fo-card padding="md">
                <p class="text-center text-sm text-muted">
                  No pudimos cargar los últimos movimientos.
                </p>
              </fo-card>
            }
          </div>
        } @else {
          <!-- Mobile layout -->
          <div data-testid="home-mobile-layout" class="space-y-4">
            <div
              class="flex items-center justify-between rounded-full border border-border bg-surface px-2 py-1"
            >
              <button
                type="button"
                aria-label="Período anterior"
                (click)="viewContext.shiftPeriod(-1)"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted hover:bg-surface-2 hover:text-text"
              >
                <fo-icon name="chevron-left" [size]="16" />
              </button>
              <button
                type="button"
                title="Volver a hoy"
                (click)="viewContext.resetToToday()"
                class="fo-focus-ring cursor-pointer rounded-full px-3 py-1 text-xs font-semibold text-text tabular-nums hover:bg-surface-2"
              >
                {{ viewContext.anchorDate() | periodLabel: viewContext.granularity() }}
              </button>
              <button
                type="button"
                aria-label="Período siguiente"
                (click)="viewContext.shiftPeriod(1)"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted hover:bg-surface-2 hover:text-text"
              >
                <fo-icon name="chevron-right" [size]="16" />
              </button>
            </div>

            @if (payload.summary; as summary) {
              <fo-hero-balance [summary]="summary" />
            } @else {
              <fo-card padding="md">
                <p class="text-center text-sm text-muted">
                  No pudimos cargar el balance del período.
                </p>
              </fo-card>
            }

            @if (primaryRate(); as rate) {
              <div class="flex flex-wrap items-center gap-2">
                <span
                  data-testid="home-rate-pill"
                  class="inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3.5 py-1.5 text-xs font-medium text-text shadow-2xs tabular-nums"
                >
                  <fo-icon name="trending-up" [size]="14" class="text-accent" />
                  <span>{{ rateLabel(rate) }} {{ ratePrice(rate) }}</span>
                </span>
              </div>
            }

            <fo-breakdown-card
              [byCategory]="payload.byCategory"
              [byPerson]="payload.byPerson"
              [view]="viewContext.view()"
              [lens]="viewContext.lens()"
            />

            @if (payload.recentTransactions; as txs) {
              @if (txs.length > 0) {
                <fo-recent-list [transactions]="txs" [lens]="viewContext.lens()" />
              } @else {
                <fo-empty-state
                  icon="wallet"
                  title="Sin movimientos en este período"
                  description="Todavía no hay movimientos en este período. Toca + para cargar el primero."
                />
              }
            } @else {
              <fo-card padding="md">
                <p class="text-center text-sm text-muted">
                  No pudimos cargar los últimos movimientos.
                </p>
              </fo-card>
            }
          </div>
        }
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  protected readonly store = inject(HomeStore);
  protected readonly viewContext = inject(ViewContextStore);
  protected readonly quickAdd = inject(QuickAddLauncher);
  private readonly session = inject(SessionStore);

  private readonly periodPipe = new PeriodLabelPipe();

  protected readonly baseCurrency = computed(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

  protected readonly preferredRateSource = computed(
    () => this.session.me()?.family.referenceRateSource ?? 'USD_MEP',
  );

  protected readonly primaryRate = computed<IndexValue | null>(() =>
    pickPrimaryRate(this.store.payload()?.latestRates, this.preferredRateSource()),
  );

  protected readonly summaryDisplayUnit = computed(() => {
    const summary = this.store.payload()?.summary;
    if (!summary) {
      return this.baseCurrency();
    }
    return resolveDisplayUnit(summary.unit, summary.lens);
  });

  protected readonly balanceNumeric = computed(() =>
    toNumber(this.store.payload()?.summary?.balance),
  );

  protected readonly isNegativeBalance = computed(() => this.balanceNumeric() < 0);

  protected readonly balanceFormatter = computed<(value: number) => string>(() => {
    const unit = this.summaryDisplayUnit();
    return (value: number) => formatMoneyValue(value, unit);
  });

  protected readonly savingsChip = computed<string | null>(() => {
    const rate = this.store.payload()?.summary?.savingsRatePct;
    if (rate === null || rate === undefined || rate.trim() === '') {
      return null;
    }
    return `Ahorro ${formatPercentValue(rate)}`;
  });

  protected readonly savingsTone = computed<KpiBadgeTone>(() => {
    const rate = this.store.payload()?.summary?.savingsRatePct;
    if (rate === null || rate === undefined) {
      return 'neutral';
    }
    return toNumber(rate) < 0 ? 'danger' : 'success';
  });

  protected readonly incomeBarPct = computed(() => {
    const summary = this.store.payload()?.summary;
    if (!summary) {
      return 0;
    }
    const income = Math.max(0, toNumber(summary.income));
    const expense = Math.max(0, toNumber(summary.expense));
    const maxVal = Math.max(income, expense);
    return maxVal > 0 ? Math.round((income / maxVal) * 100) : 0;
  });

  protected readonly expenseBarPct = computed(() => {
    const summary = this.store.payload()?.summary;
    if (!summary) {
      return 0;
    }
    const income = Math.max(0, toNumber(summary.income));
    const expense = Math.max(0, toNumber(summary.expense));
    const maxVal = Math.max(income, expense);
    return maxVal > 0 ? Math.round((expense / maxVal) * 100) : 0;
  });

  protected readonly expenseRatioSubtitle = computed<string | null>(() => {
    const summary = this.store.payload()?.summary;
    if (!summary) {
      return null;
    }
    const income = toNumber(summary.income);
    const expense = toNumber(summary.expense);
    if (income <= 0) {
      return 'Comparativa del período';
    }
    const pct = (expense / income) * 100;
    return `Gastos equivalen al ${formatPercentValue(pct)} de ingresos`;
  });

  protected readonly installmentNumeric = computed(() =>
    Math.max(0, toNumber(this.store.payload()?.summary?.installmentCommitmentPct)),
  );

  protected readonly installmentBarWidth = computed(() =>
    Math.min(100, this.installmentNumeric()),
  );

  protected readonly installmentPctLabel = computed<string | null>(() => {
    const pct = this.store.payload()?.summary?.installmentCommitmentPct;
    if (pct === null || pct === undefined || pct.trim() === '') {
      return null;
    }
    return formatPercentValue(pct);
  });

  protected readonly primaryRateDateLabel = computed<string | null>(() => {
    const rate = this.primaryRate();
    if (!rate) {
      return 'Referencia familiar';
    }
    return `Actualizado ${rate.effectiveDate}`;
  });

  protected readonly periodFallbackLabel = computed(() =>
    this.periodPipe.transform(this.viewContext.anchorDate(), this.viewContext.granularity()),
  );

  protected readonly isCompletelyUnavailable = computed(() => {
    const payload = this.store.payload();
    if (!payload) {
      return false;
    }
    return (
      payload.summary === null &&
      payload.byCategory === null &&
      payload.byPerson === null &&
      payload.recentTransactions === null &&
      payload.latestRates === null
    );
  });

  protected readonly isPeriodEmpty = computed(() => {
    const payload = this.store.payload();
    if (!payload || this.isCompletelyUnavailable()) {
      return false;
    }
    const noTransactions =
      payload.recentTransactions !== null && payload.recentTransactions.length === 0;
    const zeroActivity =
      payload.summary === null ||
      (toNumber(payload.summary.income) === 0 && toNumber(payload.summary.expense) === 0);
    return noTransactions && zeroActivity;
  });

  protected rateLabel(rate: IndexValue): string {
    return formatRateLabel(rate.indexCode);
  }

  protected ratePrice(rate: IndexValue): string {
    return formatRatePrice(rate, this.baseCurrency());
  }
}
