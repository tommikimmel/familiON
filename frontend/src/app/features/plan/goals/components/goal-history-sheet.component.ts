import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../../shared/ui/skeleton/skeleton.component';
import { Contribution, SavingsGoal, TargetUnit } from '../data-access/goals.models';
import { formatTargetUnit, formatUnitAmount } from '../utils/goal-formatters';

@Component({
  selector: 'fo-goal-history-sheet',
  standalone: true,
  imports: [
    MoneyPipe,
    ButtonComponent,
    IconComponent,
    EmptyStateComponent,
    SkeletonComponent,
  ],
  template: `
    @if (goal(); as g) {
      <div class="mb-4 flex items-center justify-between gap-2 border-b border-border pb-3">
        <div>
          <span class="text-xs text-muted">Historial de aportes</span>
          <h3 class="text-base font-semibold text-text">{{ g.name }}</h3>
        </div>
        <div class="text-right">
          <span class="text-xs text-muted">Total acumulado</span>
          <p class="text-sm font-bold text-text tabular-nums">
            {{ formatUnit(g.accumulatedInUnit, g.targetUnit) }}
          </p>
        </div>
      </div>

      @if (loading()) {
        <div class="space-y-3 py-2">
          <fo-skeleton height="3.5rem" />
          <fo-skeleton height="3.5rem" />
          <fo-skeleton height="3.5rem" />
        </div>
      } @else if (contributions().length === 0) {
        <fo-empty-state
          icon="file-text"
          title="Sin aportes registrados"
          description="Todavía no se registraron aportes para esta meta. Podés sumar el primero con el botón Aportar."
        />
      } @else {
        <div class="space-y-2.5 max-h-[60vh] overflow-y-auto pr-1">
          @for (c of contributions(); track c.id) {
            <div
              class="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface-2/60 p-3 transition-colors"
            >
              <div class="min-w-0 flex-1">
                <div class="flex items-baseline gap-2">
                  <strong class="text-sm font-semibold text-text tabular-nums">
                    {{ c.amount | money: c.currencyCode }}
                  </strong>
                  @if (g.targetUnit !== c.currencyCode) {
                    <span class="text-xs text-muted tabular-nums">
                      ({{ formatUnit(c.amountInGoalUnit, g.targetUnit) }})
                    </span>
                  }
                </div>

                <div class="mt-1 flex items-center gap-2 text-xs text-muted">
                  <span>{{ c.contributionDate }}</span>
                  @if (c.user?.fullName) {
                    <span>·</span>
                    <span class="truncate">Por {{ c.user.fullName }}</span>
                  }
                </div>

                @if (c.notes) {
                  <p class="mt-1 text-xs text-text italic">
                    «{{ c.notes }}»
                  </p>
                }
              </div>

              <!-- Delete Contribution Button -->
              @if (confirmingDeleteId() === c.id) {
                <div class="flex items-center gap-1.5 shrink-0">
                  <fo-button
                    variant="ghost"
                    size="sm"
                    type="button"
                    (click)="cancelDelete()"
                    [disabled]="deletingId() === c.id"
                  >
                    No
                  </fo-button>
                  <fo-button
                    variant="danger"
                    size="sm"
                    type="button"
                    (click)="confirmDelete(c)"
                    [loading]="deletingId() === c.id"
                  >
                    Borrar
                  </fo-button>
                </div>
              } @else {
                <button
                  type="button"
                  aria-label="Eliminar aporte"
                  (click)="startDelete(c.id)"
                  class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface hover:text-danger"
                >
                  <fo-icon name="trash-2" [size]="16" />
                </button>
              }
            </div>
          }
        </div>
      }

      <div class="mt-4 flex justify-end border-t border-border pt-3">
        <fo-button variant="ghost" type="button" (click)="closed.emit()">
          Cerrar
        </fo-button>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalHistorySheetComponent {
  readonly goal = input<SavingsGoal | null>(null);
  readonly contributions = input<Contribution[]>([]);
  readonly loading = input<boolean>(false);
  readonly deletingId = input<string | null>(null);

  readonly deleteContribution = output<Contribution>();
  readonly closed = output<void>();

  protected readonly confirmingDeleteId = signal<string | null>(null);

  protected formatUnit(amount: string | number, unit: string): string {
    return formatUnitAmount(amount, unit as TargetUnit);
  }

  protected targetUnitLabel(unit: string): string {
    return formatTargetUnit(unit as TargetUnit);
  }

  protected startDelete(id: string): void {
    this.confirmingDeleteId.set(id);
  }

  protected cancelDelete(): void {
    this.confirmingDeleteId.set(null);
  }

  protected confirmDelete(contribution: Contribution): void {
    this.deleteContribution.emit(contribution);
    this.confirmingDeleteId.set(null);
  }
}
