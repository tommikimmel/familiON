import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { SessionStore } from '../../../core/state/session.store';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { ButtonComponent, CardComponent, IconComponent, SkeletonComponent } from '../../../shared/ui';
import { AllowancesSummaryCardComponent } from './components/allowances-summary-card.component';
import { ExchangesSummaryCardComponent } from './components/exchanges-summary-card.component';
import { GoalsSummaryCardComponent } from './components/goals-summary-card.component';
import { InstallmentsSummaryCardComponent } from './components/installments-summary-card.component';
import { RecurringSummaryCardComponent } from './components/recurring-summary-card.component';
import { PlanOverviewStore } from './data-access/plan-overview.store';
import { formatYearMonthLong } from './utils/plan-overview.formatters';

@Component({
  selector: 'fo-plan-overview-page',
  standalone: true,
  providers: [PlanOverviewStore],
  imports: [
    AllowancesSummaryCardComponent,
    ButtonComponent,
    CardComponent,
    ExchangesSummaryCardComponent,
    GoalsSummaryCardComponent,
    IconComponent,
    InstallmentsSummaryCardComponent,
    RecurringSummaryCardComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="pl-stack">
      <header class="sr-only">
        <h1>Planificación</h1>
        <p>{{ monthLabel() }} · Vista {{ viewLabel() }}</p>
      </header>

      @if (store.loading() && !store.payload()) {
        <div
          data-testid="plan-overview-skeleton"
          aria-busy="true"
          aria-label="Cargando planificación"
          class="pl-two-col"
        >
          @for (item of [1, 2, 3, 4, 5]; track item) {
            <fo-card padding="md">
              <div class="flex items-center justify-between">
                <fo-skeleton width="42%" height="1.25rem" />
                <fo-skeleton width="4.5rem" height="1rem" />
              </div>
              <div class="mt-4 space-y-3">
                <fo-skeleton width="70%" height="1.5rem" />
                <fo-skeleton height="3.5rem" rounded="lg" />
                <fo-skeleton width="55%" height="0.875rem" />
              </div>
            </fo-card>
          }
        </div>
      } @else if (store.error(); as errorMessage) {
        <fo-card padding="lg" data-testid="plan-overview-error">
          <div class="flex flex-col items-center py-6 text-center">
            <div
              class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-danger/12 text-danger"
              aria-hidden="true"
            >
              <fo-icon name="alert-circle" [size]="24" />
            </div>
            <h2 class="text-base font-semibold text-text">No pudimos cargar la planificación</h2>
            <p class="mt-1 max-w-sm text-sm text-muted">{{ errorMessage }}</p>
            <div class="mt-4">
              <fo-button data-testid="plan-overview-retry-btn" (click)="store.reload()"> Reintentar </fo-button>
            </div>
          </div>
        </fo-card>
      } @else if (store.payload(); as payload) {
        <div data-testid="plan-overview-grid" class="pl-two-col">
          <fo-exchanges-summary-card
            class="lg:order-1"
            [positions]="payload.positions"
            [retrying]="store.loading()"
            (retry)="store.reload()"
          />

          <fo-recurring-summary-card
            class="lg:order-3"
            [items]="payload.recurring"
            [baseCurrency]="baseCurrency()"
            [retrying]="store.loading()"
            (retry)="store.reload()"
          />

          <fo-goals-summary-card
            class="lg:order-2"
            [goals]="payload.goals"
            [retrying]="store.loading()"
            (retry)="store.reload()"
          />

          <fo-installments-summary-card
            class="lg:order-4"
            [installments]="payload.installments"
            [commitment]="payload.commitment"
            [baseCurrency]="baseCurrency()"
            [retrying]="store.loading()"
            (retry)="store.reload()"
          />

          @if (showAllowancesCard()) {
            <fo-allowances-summary-card
              class="lg:order-5"
              [allowances]="payload.allowances"
              [retrying]="store.loading()"
              (retry)="store.reload()"
            />
          }
        </div>
      }
    </section>
  `,
  styleUrl: '../plan-ui.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanOverviewPage {
  protected readonly store = inject(PlanOverviewStore);
  private readonly session = inject(SessionStore);
  private readonly viewContext = inject(ViewContextStore);

  protected readonly baseCurrency = computed(() => this.session.me()?.family.baseCurrency ?? 'ARS');

  protected readonly monthLabel = computed(() => formatYearMonthLong(this.store.month()));

  protected readonly viewLabel = computed(() => (this.viewContext.view() === 'FAMILY' ? 'Familiar' : 'Personal'));

  protected readonly showAllowancesCard = computed(() => {
    const allowances = this.store.payload()?.allowances;
    return allowances === null || (allowances !== undefined && allowances.length > 0);
  });
}
