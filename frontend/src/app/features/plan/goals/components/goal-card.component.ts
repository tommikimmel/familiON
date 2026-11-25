import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { CardComponent } from '../../../../shared/ui/card/card.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { SavingsGoal } from '../data-access/goals.models';
import { formatSuggestedSavings, formatUnitAmount } from '../utils/goal-formatters';
import { GoalProgressRingComponent } from './goal-progress-ring.component';

@Component({
  selector: 'fo-goal-card',
  standalone: true,
  imports: [
    CardComponent,
    ButtonComponent,
    IconComponent,
    MoneyPipe,
    GoalProgressRingComponent,
  ],
  template: `
    <fo-card
      [class.border-success]="isCompleted()"
      [class.shadow-md]="isCompleted()"
      class="relative overflow-hidden transition-all duration-200"
    >
      @if (isCompleted()) {
        <!-- Subtle celebratory ribbon / banner -->
        <div
          class="mb-3 flex items-center gap-1.5 rounded-lg bg-success-soft px-2.5 py-1 text-xs font-medium text-success motion-safe:animate-[fo-fade-in_300ms_ease]"
        >
          <fo-icon name="party-popper" [size]="14" />
          <span>¡Meta cumplida! Felicitaciones</span>
        </div>
      }

      <div class="flex items-start justify-between gap-3">
        <!-- Goal Progress Ring -->
        <fo-goal-progress-ring [progressPct]="goal().progressPct" [size]="64" />

        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span
              class="inline-flex items-center rounded-full px-2 py-0.5 text-2xs font-semibold uppercase tracking-wider"
              [class.bg-accent-soft]="goal().scope === 'FAMILY'"
              [class.text-accent]="goal().scope === 'FAMILY'"
              [class.bg-surface-2]="goal().scope === 'PERSONAL'"
              [class.text-muted]="goal().scope === 'PERSONAL'"
            >
              {{ goal().scope === 'FAMILY' ? 'Familiar' : 'Personal' }}
            </span>

            @if (goal().status === 'PAUSED') {
              <span class="inline-flex items-center rounded-full bg-surface-2 px-2 py-0.5 text-2xs font-medium text-muted">
                Pausada
              </span>
            }

            @if (goal().status === 'CANCELLED') {
              <span class="inline-flex items-center rounded-full bg-danger-soft px-2 py-0.5 text-2xs font-medium text-danger">
                Cancelada
              </span>
            }
          </div>

          <h3 class="mt-1 truncate text-base font-semibold text-text" [title]="goal().name">
            {{ goal().name }}
          </h3>

          @if (goal().owner && goal().scope === 'FAMILY') {
            <p class="text-xs text-muted">
              Creada por {{ goal().owner?.fullName }}
            </p>
          }
        </div>

        <!-- Action / options button -->
        <button
          type="button"
          aria-label="Editar meta"
          (click)="edit.emit(goal())"
          class="fo-focus-ring -mr-1 -mt-1 inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
        >
          <fo-icon name="pencil" [size]="16" />
        </button>
      </div>

      <!-- Amounts section -->
      <div class="mt-4 rounded-xl border border-border bg-surface-2/60 p-3">
        <div class="flex items-baseline justify-between gap-2">
          <span class="text-xs text-muted">Acumulado / Meta</span>
          <span class="text-xs font-medium text-muted">
            {{ goal().targetUnit === 'ARS_IPC' ? '$ de hoy' : goal().targetUnit }}
          </span>
        </div>

        <div class="mt-1 flex items-baseline gap-1.5 truncate">
          <strong class="text-lg font-bold text-text tabular-nums">
            {{ accumulatedFormatted() }}
          </strong>
          <span class="text-sm text-muted">/</span>
          <span class="text-sm font-semibold text-muted tabular-nums">
            {{ targetFormatted() }}
          </span>
        </div>

        @if (goal().accumulatedInBaseToday !== null) {
          <div class="mt-1.5 flex items-center gap-1 text-xs text-muted">
            <span>Equivalente hoy:</span>
            <strong class="font-medium text-text tabular-nums">
              {{ goal().accumulatedInBaseToday | money: baseCurrency() }}
            </strong>
          </div>
        }
      </div>

      <!-- Suggested savings hint -->
      @if (suggestedSavingsText()) {
        <div class="mt-3 flex items-center gap-1.5 text-xs text-muted">
          <fo-icon name="trending-up" [size]="14" class="text-accent" />
          <span class="truncate">{{ suggestedSavingsText() }}</span>
        </div>
      }

      <!-- Bottom Card Actions -->
      <div class="mt-4 flex items-center justify-between gap-2 border-t border-border pt-3">
        <fo-button
          variant="ghost"
          size="sm"
          type="button"
          (click)="viewHistory.emit(goal())"
          ariaLabel="Ver historial de aportes"
        >
          <fo-icon name="file-text" [size]="14" />
          <span>{{ goal().contributionsCount }} {{ goal().contributionsCount === 1 ? 'aporte' : 'aportes' }}</span>
        </fo-button>

        @if (goal().status !== 'CANCELLED' && goal().status !== 'COMPLETED') {
          <fo-button
            variant="primary"
            size="sm"
            type="button"
            (click)="contribute.emit(goal())"
            ariaLabel="Aportar a la meta"
          >
            <fo-icon name="plus" [size]="14" />
            <span>Aportar</span>
          </fo-button>
        }
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalCardComponent {
  readonly goal = input.required<SavingsGoal>();
  readonly baseCurrency = input<string>('ARS');

  readonly contribute = output<SavingsGoal>();
  readonly viewHistory = output<SavingsGoal>();
  readonly edit = output<SavingsGoal>();

  protected readonly isCompleted = computed(
    () => this.goal().status === 'COMPLETED' || Number(this.goal().progressPct) >= 100,
  );

  protected readonly accumulatedFormatted = computed(() =>
    formatUnitAmount(this.goal().accumulatedInUnit, this.goal().targetUnit),
  );

  protected readonly targetFormatted = computed(() =>
    formatUnitAmount(this.goal().targetAmount, this.goal().targetUnit),
  );

  protected readonly suggestedSavingsText = computed(() =>
    formatSuggestedSavings(
      this.goal().monthsLeft,
      this.goal().suggestedMonthlyInUnit,
      this.goal().targetUnit,
    ),
  );
}
