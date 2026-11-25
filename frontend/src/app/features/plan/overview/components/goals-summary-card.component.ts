import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { IconComponent, ProgressBarComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { PlanGoalItem } from '../data-access/plan-overview.models';
import { resolveGoalDisplayUnit } from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

interface GoalRingViewModel {
  id: string;
  name: string;
  progressClamped: number;
  progressRounded: number;
  completed: boolean;
  accumulatedLabel: string;
  targetLabel: string;
  adjusted: boolean;
}

@Component({
  selector: 'fo-goals-summary-card',
  standalone: true,
  imports: [IconComponent, PlanSectionCardComponent, ProgressBarComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Metas en valor real"
      linkRoute="/plan/goals"
      testId="plan-goals-card"
      [degraded]="goals() === null"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (goals(); as list) {
        @if (list.length === 0) {
          <div data-testid="goals-empty-state" class="pl-empty">
            <p class="fo-tx-title">Sin metas activas</p>
            <p class="pl-sub">Definí un objetivo en pesos, dólares o UVA y seguí tu avance mes a mes.</p>
            <a
              routerLink="/plan/goals"
              data-testid="goals-empty-cta"
              class="fo-btn-primary fo-focus-ring mt-2 inline-flex items-center gap-1.5"
            >
              <fo-icon name="plus" [size]="14" />
              <span>Crear meta</span>
            </a>
          </div>
        } @else {
          <div class="flex flex-1 flex-col justify-between gap-3">
            <ul class="flex flex-col gap-3.5" aria-label="Primeras metas de ahorro">
              @for (goal of visibleGoals(); track goal.id) {
                <li data-testid="goal-item">
                  <div class="pl-head">
                    <strong class="fo-tx-title min-w-0 truncate">{{ goal.name }}</strong>
                    <span class="fo-pill-tag fo-pill-accent fo-num"> {{ goal.progressRounded }}% </span>
                  </div>
                  <fo-progress-bar
                    class="mt-2.5"
                    [value]="goal.progressClamped"
                    [color]="goal.completed ? 'var(--fo-success)' : null"
                    [ariaLabel]="'Progreso de ' + goal.name + ': ' + goal.progressRounded + '%'"
                  />
                  <div class="pl-sub fo-num mt-1.5 flex justify-between gap-2">
                    <span>
                      {{ goal.accumulatedLabel }}
                      @if (goal.adjusted) {
                        · a plata de hoy
                      }
                    </span>
                    <span>Meta: {{ goal.targetLabel }}</span>
                  </div>
                </li>
              }
            </ul>

            @if (extraCount() > 0) {
              <p class="pl-sub">+{{ extraCount() }} {{ extraCount() === 1 ? 'meta más' : 'metas más' }}</p>
            }
          </div>
        }
      }
    </fo-plan-section-card>
  `,
  styleUrl: '../../plan-ui.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsSummaryCardComponent {
  readonly goals = input<PlanGoalItem[] | null>(null);
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  private readonly moneyPipe = new MoneyPipe();

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
        completed: rawPct >= 100,
        accumulatedLabel: this.moneyPipe.transform(goal.accumulatedInUnit, unit),
        targetLabel: this.moneyPipe.transform(goal.targetAmount, unit),
        adjusted,
      };
    }),
  );

  protected readonly extraCount = computed(() => Math.max(0, (this.goals()?.length ?? 0) - 3));
}
