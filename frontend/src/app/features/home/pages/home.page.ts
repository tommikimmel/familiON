import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { QuickAddLauncher } from '../../../core/layout/quick-add-launcher.service';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { CountUpDirective } from '../../../shared/directives/count-up.directive';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PillTagComponent } from '../../../shared/ui/pill-tag/pill-tag.component';
import { ProgressBarComponent } from '../../../shared/ui/progress-bar/progress-bar.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { toNumber } from '../../../shared/utils/decimal';
import { BreakdownCardComponent } from '../components/breakdown-card.component';
import { EvolutionChartComponent } from '../components/evolution-chart.component';
import { HeroBalanceComponent } from '../components/hero-balance.component';
import { KpiCardComponent } from '../components/kpi-card.component';
import { MovementsTableComponent } from '../components/movements-table.component';
import { PeriodBarComponent } from '../components/period-bar.component';
import { RecentListComponent } from '../components/recent-list.component';
import {
  formatPercentValue,
  formatRateLabel,
  formatRatePrice,
  formatWholeMoney,
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
    MovementsTableComponent,
    PeriodBarComponent,
    PillTagComponent,
    ProgressBarComponent,
    RecentListComponent,
    SkeletonComponent,
  ],
  template: `
    <div class="mx-auto flex max-w-7xl flex-col gap-[11px] lg:gap-4">
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
              <fo-period-bar
                [granularity]="viewContext.granularity()"
                [anchorDate]="viewContext.anchorDate()"
                (granularityChange)="viewContext.setGranularity($event)"
                (shift)="viewContext.shiftPeriod($event)"
                (today)="viewContext.resetToToday()"
              />
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
          <div data-testid="home-desktop-layout" class="flex flex-col gap-4">
            <div class="kpi-grid fo-anim-in">
              <fo-kpi-card
                [title]="balanceTitle()"
                [badge]="lensBadge()"
                [badgeAccent]="viewContext.lens() === 'CONSTANT_BASE'"
                [glow]="true"
              >
                @if (payload.summary; as summary) {
                  <p
                    data-testid="desktop-kpi-balance"
                    class="kpi-val"
                    [class.text-danger]="isNegativeBalance()"
                    [foCountUp]="balanceNumeric()"
                    [foCountUpFormatter]="balanceFormatter()"
                  ></p>
                  @if (summary.lensApproximated) {
                    <p class="kpi-sub flex items-center gap-1 text-warning">
                      <fo-icon name="alert-circle" [size]="12" />
                      <span>Valores aproximados: falta el índice de inflación más reciente</span>
                    </p>
                  } @else if (savingsPctLabel(); as savings) {
                    <p class="kpi-sub">
                      Ahorro real del período: <strong>{{ savings }}</strong> del ingreso
                    </p>
                  }
                } @else {
                  <p class="kpi-sub">Resumen no disponible.</p>
                }
              </fo-kpi-card>

              <fo-kpi-card title="Gastos vs. Ingresos" [badge]="expenseRatioBadge()">
                @if (payload.summary; as summary) {
                  <p class="kpi-val">{{ wholeMoney(summary.expense) }}</p>
                  <fo-progress-bar
                    class="mt-2.5"
                    [value]="expenseRatioPct()"
                    ariaLabel="Gastos sobre ingresos"
                  />
                  <p class="kpi-sub">
                    Ingresos: <strong>{{ wholeMoney(summary.income) }}</strong>
                  </p>
                } @else {
                  <p class="kpi-sub">Comparativa no disponible.</p>
                }
              </fo-kpi-card>

              <fo-kpi-card
                title="Divisas y Cuotas"
                [badge]="primaryRateBadge()"
                [badgeAccent]="true"
              >
                @if (primaryRate(); as rate) {
                  <p data-testid="desktop-primary-rate" class="kpi-val">{{ ratePrice(rate) }}</p>
                } @else {
                  <p class="kpi-val text-muted">—</p>
                }
                @if (installmentPctLabel(); as commitmentPct) {
                  <p data-testid="desktop-installment-commitment" class="kpi-sub">
                    Comprometido en cuotas: <strong>{{ commitmentPct }}</strong> del ingreso
                  </p>
                } @else {
                  <p class="kpi-sub">Sin compromisos en cuotas en este período.</p>
                }
              </fo-kpi-card>
            </div>

            <div class="charts-grid fo-anim-in">
              <fo-evolution-chart
                [series]="store.series()"
                [loading]="store.seriesLoading()"
                [lens]="viewContext.lens()"
              />
              <fo-breakdown-card
                [byCategory]="payload.byCategory"
                [byPerson]="payload.byPerson"
                [view]="viewContext.view()"
                [lens]="viewContext.lens()"
              />
            </div>

            <!-- Row 3: Movements table with instant search -->
            @if (payload.recentTransactions; as txs) {
              @if (txs.length > 0) {
                <fo-movements-table
                  class="fo-anim-in"
                  [transactions]="txs"
                  [lens]="viewContext.lens()"
                />
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
          <div data-testid="home-mobile-layout" class="flex flex-col gap-[11px]">
            <fo-period-bar
              [granularity]="viewContext.granularity()"
              [anchorDate]="viewContext.anchorDate()"
              (granularityChange)="viewContext.setGranularity($event)"
              (shift)="viewContext.shiftPeriod($event)"
              (today)="viewContext.resetToToday()"
            />

            @if (payload.summary; as summary) {
              <fo-hero-balance
                class="fo-anim-in fo-stagger-1"
                [summary]="summary"
                [view]="viewContext.view()"
              />
            } @else {
              <fo-card padding="md">
                <p class="text-center text-sm text-muted">
                  No pudimos cargar el balance del período.
                </p>
              </fo-card>
            }

            <fo-breakdown-card
              class="fo-anim-in fo-stagger-2"
              [byCategory]="payload.byCategory"
              [byPerson]="payload.byPerson"
              [view]="viewContext.view()"
              [lens]="viewContext.lens()"
            />

            @if (payload.recentTransactions; as txs) {
              @if (txs.length > 0) {
                <fo-recent-list
                  class="fo-anim-in fo-stagger-3"
                  [transactions]="txs"
                  [lens]="viewContext.lens()"
                  [view]="viewContext.view()"
                />
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
            @if (primaryRate(); as rate) {
              <div class="flex justify-center">
                <fo-pill-tag data-testid="home-rate-pill" class="tabular-nums">
                  {{ rateLabel(rate) }} {{ ratePrice(rate) }}
                </fo-pill-tag>
              </div>
            }
          </div>
        }
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 14px;
    }
    .charts-grid {
      display: grid;
      grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
      gap: 14px;
    }
    .kpi-val {
      margin-top: 4px;
      font-size: 24px;
      font-weight: 700;
      letter-spacing: -0.03em;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .kpi-sub {
      margin-top: 4px;
      font-size: 11px;
      color: var(--fo-text-muted);
      font-variant-numeric: tabular-nums;
    }
    .kpi-sub strong {
      font-weight: 700;
      color: var(--fo-text);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  protected readonly store = inject(HomeStore);
  protected readonly viewContext = inject(ViewContextStore);
  protected readonly quickAdd = inject(QuickAddLauncher);
  private readonly session = inject(SessionStore);

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
    return (value: number) => formatWholeMoney(value, unit);
  });

  protected readonly balanceTitle = computed(() =>
    this.viewContext.view() === 'FAMILY' ? 'Balance familiar' : 'Mi balance',
  );

  protected readonly lensBadge = computed(() =>
    this.viewContext.lens() === 'CONSTANT_BASE' ? 'A plata de hoy' : 'Nominal',
  );

  protected readonly savingsPctLabel = computed<string | null>(() => {
    const rate = this.store.payload()?.summary?.savingsRatePct;
    if (rate === null || rate === undefined || rate.trim() === '') {
      return null;
    }
    return formatPercentValue(rate);
  });

  /** Expense as a share of income, capped at 100 (mock `ratio`). */
  protected readonly expenseRatioPct = computed(() => {
    const summary = this.store.payload()?.summary;
    if (!summary) {
      return 0;
    }
    const income = toNumber(summary.income);
    const expense = toNumber(summary.expense);
    if (income <= 0) {
      return expense > 0 ? 100 : 0;
    }
    return Math.min(100, Math.round((expense / income) * 100));
  });

  protected readonly expenseRatioBadge = computed(() =>
    this.store.payload()?.summary ? `${this.expenseRatioPct()}%` : null,
  );

  protected readonly primaryRateBadge = computed<string | null>(() => {
    const rate = this.primaryRate();
    return rate ? this.rateLabel(rate) : null;
  });

  protected readonly installmentPctLabel = computed<string | null>(() => {
    const pct = this.store.payload()?.summary?.installmentCommitmentPct;
    if (pct === null || pct === undefined || pct.trim() === '') {
      return null;
    }
    return formatPercentValue(pct);
  });

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

  protected wholeMoney(value: string): string {
    return formatWholeMoney(value, this.summaryDisplayUnit());
  }

  protected rateLabel(rate: IndexValue): string {
    return formatRateLabel(rate.indexCode);
  }

  protected ratePrice(rate: IndexValue): string {
    return formatRatePrice(rate, this.baseCurrency());
  }
}
