import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { PlanGoalItem } from '../data-access/plan-overview.models';
import { resolveGoalDisplayUnit } from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

const RING_RADIUS = 18;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

interface GoalRingViewModel {
  id: string;
  name: string;
  progressClamped: number;
  progressRounded: number;
  strokeDashoffset: number;
  completed: boolean;
  accumulatedLabel: string;
  targetLabel: string;
  adjusted: boolean;
}

@Component({
  selector: 'fo-goals-summary-card',
  standalone: true,
  imports: [IconComponent, PlanSectionCardComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Metas"
      icon="piggy-bank"
      linkRoute="/plan/goals"
      testId="plan-goals-card"
      [degraded]="goals() === null"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (goals(); as list) {
        @if (list.length === 0) {
          <div
            data-testid="goals-empty-state"
            class="flex flex-1 flex-col items-center justify-center rounded-xl border border-dashed border-border bg-surface-2/40 px-4 py-6 text-center"
          >
            <p class="text-sm font-medium text-text">Sin metas activas</p>
            <p class="mt-1 text-xs text-muted">
              Definí un objetivo en pesos, dólares o UVA y seguí tu avance mes a mes.
            </p>
            <a
              routerLink="/plan/goals"
              data-testid="goals-empty-cta"
              class="fo-focus-ring mt-3 inline-flex items-center gap-1.5 rounded-xl bg-accent px-3.5 py-2 text-xs font-semibold text-accent-contrast transition-opacity duration-150 hover:opacity-92"
            >
              <fo-icon name="plus" [size]="14" />
              <span>Crear meta</span>
            </a>
          </div>
        } @else {
          <div class="flex flex-1 flex-col justify-between gap-3">
            <ul class="space-y-3" aria-label="Primeras metas de ahorro">
              @for (goal of visibleGoals(); track goal.id) {
                <li data-testid="goal-item" class="flex items-center gap-3">
                  <div
                    class="relative flex h-11 w-11 shrink-0 items-center justify-center"
                    role="progressbar"
                    [attr.aria-valuenow]="goal.progressRounded"
                    aria-valuemin="0"
                    aria-valuemax="100"
                    [attr.aria-label]="'Progreso de ' + goal.name + ': ' + goal.progressRounded + '%'"
                  >
                    <svg viewBox="0 0 44 44" class="h-11 w-11 -rotate-90" aria-hidden="true">
                      <circle
                        cx="22"
                        cy="22"
                        [attr.r]="ringRadius"
                        fill="none"
                        stroke="var(--fo-surface-2)"
                        stroke-width="4"
                      />
                      <circle
                        cx="22"
                        cy="22"
                        [attr.r]="ringRadius"
                        fill="none"
                        [attr.stroke]="goal.completed ? 'var(--fo-success)' : 'var(--fo-accent)'"
                        stroke-width="4"
                        stroke-linecap="round"
                        [attr.stroke-dasharray]="ringCircumference"
                        [attr.stroke-dashoffset]="goal.strokeDashoffset"
                      />
                    </svg>
                    <span
                      class="absolute text-[10px] font-bold text-text tabular-nums"
                      aria-hidden="true"
                    >
                      {{ goal.progressRounded }}%
                    </span>
                  </div>

                  <div class="min-w-0 flex-1">
                    <p class="truncate text-sm font-semibold text-text">{{ goal.name }}</p>
                    <p class="mt-0.5 text-xs text-muted tabular-nums">
                      {{ goal.accumulatedLabel }} / {{ goal.targetLabel }}
                      @if (goal.adjusted) {
                        <span> · a plata de hoy</span>
                      }
                    </p>
                  </div>
                </li>
              }
            </ul>

            @if (extraCount() > 0) {
              <p class="text-xs text-muted">
                +{{ extraCount() }} {{ extraCount() === 1 ? 'meta más' : 'metas más' }}
              </p>
            }
          </div>
        }
      }
    </fo-plan-section-card>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsSummaryCardComponent {
  readonly goals = input<PlanGoalItem[] | null>(null);
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  private readonly moneyPipe = new MoneyPipe();

  protected readonly ringRadius = RING_RADIUS;
  protected readonly ringCircumference = RING_CIRCUMFERENCE;

  protected readonly visibleGoals = computed<GoalRingViewModel[]>(() =>
    (this.goals() ?? []).slice(0, 3).map((goal) => {
      const rawPct = Math.max(0, toNumber(goal.progressPct));
      const progressClamped = Math.min(100, rawPct);
      const progressRounded = Math.round(rawPct);
      const { unit, adjusted } = resolveGoalDisplayUnit(goal.targetUnit);

      return {
        id: goal.id,
        name: goal.name,
        progressClamped,
        progressRounded,
        strokeDashoffset: RING_CIRCUMFERENCE * (1 - progressClamped / 100),
        completed: rawPct >= 100,
        accumulatedLabel: this.moneyPipe.transform(goal.accumulatedInUnit, unit),
        targetLabel: this.moneyPipe.transform(goal.targetAmount, unit),
        adjusted,
      };
    }),
  );

  protected readonly extraCount = computed(() => Math.max(0, (this.goals()?.length ?? 0) - 3));
}
