import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import {
  GoalScope,
  GoalStatus,
  SavingsGoal,
  SavingsGoalCreate,
  SavingsGoalPatch,
  TARGET_UNIT_OPTIONS,
  TargetUnit,
} from '../data-access/goals.models';

export type GoalFormSubmitPayload =
  | { mode: 'create'; data: SavingsGoalCreate }
  | { mode: 'edit'; id: string; data: SavingsGoalPatch };

@Component({
  selector: 'fo-goal-form-sheet',
  standalone: true,
  imports: [FormsModule, ButtonComponent, IconComponent],
  template: `
    <form (ngSubmit)="handleSubmit()" class="space-y-4">
      <!-- Goal Name -->
      <div>
        <label for="goal-name" class="block text-xs font-medium text-muted">
          Nombre de la meta *
        </label>
        <input
          id="goal-name"
          name="name"
          type="text"
          required
          maxlength="120"
          [(ngModel)]="name"
          placeholder="Ej: Vacaciones, Fondo de emergencia, Auto"
          class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
        />
      </div>

      <!-- Scope (Personal vs Familiar) -->
      @if (!isEditing()) {
        <div>
          <span class="block text-xs font-medium text-muted mb-1">
            Alcance *
          </span>
          <div class="grid grid-cols-2 gap-2">
            <button
              type="button"
              (click)="scope.set('PERSONAL')"
              class="fo-focus-ring flex items-center justify-center gap-2 rounded-xl border p-2.5 text-xs font-medium transition-colors"
              [class.border-accent]="scope() === 'PERSONAL'"
              [class.bg-accent-soft]="scope() === 'PERSONAL'"
              [class.text-accent]="scope() === 'PERSONAL'"
              [class.border-border]="scope() !== 'PERSONAL'"
              [class.bg-surface]="scope() !== 'PERSONAL'"
              [class.text-muted]="scope() !== 'PERSONAL'"
            >
              <fo-icon name="user" [size]="16" />
              <span>Personal</span>
            </button>
            <button
              type="button"
              (click)="scope.set('FAMILY')"
              class="fo-focus-ring flex items-center justify-center gap-2 rounded-xl border p-2.5 text-xs font-medium transition-colors"
              [class.border-accent]="scope() === 'FAMILY'"
              [class.bg-accent-soft]="scope() === 'FAMILY'"
              [class.text-accent]="scope() === 'FAMILY'"
              [class.border-border]="scope() !== 'FAMILY'"
              [class.bg-surface]="scope() !== 'FAMILY'"
              [class.text-muted]="scope() !== 'FAMILY'"
            >
              <fo-icon name="users" [size]="16" />
              <span>Familiar</span>
            </button>
          </div>
        </div>
      }

      <!-- Target Amount -->
      <div>
        <label for="goal-target-amount" class="block text-xs font-medium text-muted">
          Monto objetivo *
        </label>
        <div class="relative mt-1">
          <input
            id="goal-target-amount"
            name="targetAmount"
            type="number"
            step="any"
            min="0.01"
            required
            [(ngModel)]="targetAmount"
            placeholder="0.00"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm font-semibold text-text placeholder:text-muted tabular-nums"
          />
        </div>
      </div>

      <!-- Target Unit with Explanations -->
      @if (!isEditing()) {
        <div>
          <span class="block text-xs font-medium text-muted mb-1">
            Unidad de ahorro *
          </span>
          <div class="space-y-1.5">
            @for (opt of unitOptions; track opt.value) {
              <label
                class="flex cursor-pointer items-start gap-3 rounded-xl border p-2.5 transition-colors"
                [class.border-accent]="targetUnit() === opt.value"
                [class.bg-accent-soft/40]="targetUnit() === opt.value"
                [class.border-border]="targetUnit() !== opt.value"
                [class.bg-surface]="targetUnit() !== opt.value"
              >
                <input
                  type="radio"
                  name="targetUnit"
                  [value]="opt.value"
                  [checked]="targetUnit() === opt.value"
                  (change)="targetUnit.set(opt.value)"
                  class="mt-0.5 text-accent focus:ring-accent"
                />
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-2">
                    <span class="text-xs font-semibold text-text">{{ opt.label }}</span>
                    <span class="rounded bg-surface-2 px-1.5 py-0.5 text-2xs font-mono text-muted">
                      {{ opt.badge }}
                    </span>
                  </div>
                  <p class="mt-0.5 text-xs text-muted">{{ opt.explanation }}</p>
                </div>
              </label>
            }
          </div>
        </div>
      }

      <!-- Target Date (optional) -->
      <div>
        <label for="goal-target-date" class="block text-xs font-medium text-muted">
          Fecha objetivo (opcional)
        </label>
        <input
          id="goal-target-date"
          name="targetDate"
          type="date"
          [(ngModel)]="targetDate"
          class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
        />
        <p class="mt-1 text-2xs text-muted">
          Permite calcular el ahorro mensual sugerido para alcanzar la meta.
        </p>
      </div>

      <!-- Status (only when editing) -->
      @if (isEditing()) {
        <div>
          <label for="goal-status" class="block text-xs font-medium text-muted">
            Estado de la meta
          </label>
          <select
            id="goal-status"
            name="status"
            [(ngModel)]="status"
            class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
          >
            <option value="ACTIVE">Activa</option>
            <option value="PAUSED">Pausada</option>
            <option value="COMPLETED">Completada</option>
            <option value="CANCELLED">Cancelada</option>
          </select>
        </div>
      }

      <!-- Form Actions -->
      <div class="flex items-center justify-end gap-2 pt-2">
        <fo-button
          variant="ghost"
          type="button"
          [disabled]="saving()"
          (click)="cancelled.emit()"
        >
          Cancelar
        </fo-button>
        <fo-button
          variant="primary"
          type="submit"
          [loading]="saving()"
          [disabled]="!isValid()"
        >
          {{ isEditing() ? 'Guardar cambios' : 'Crear meta' }}
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalFormSheetComponent {
  readonly goal = input<SavingsGoal | null>(null);
  readonly saving = input<boolean>(false);

  readonly submitted = output<GoalFormSubmitPayload>();
  readonly cancelled = output<void>();

  protected readonly unitOptions = TARGET_UNIT_OPTIONS;

  protected readonly name = signal<string>('');
  protected readonly scope = signal<GoalScope>('PERSONAL');
  protected readonly targetAmount = signal<string>('');
  protected readonly targetUnit = signal<TargetUnit>('USD');
  protected readonly targetDate = signal<string>('');
  protected readonly status = signal<GoalStatus>('ACTIVE');

  protected readonly isEditing = signal<boolean>(false);

  constructor() {
    effect(() => {
      const g = this.goal();
      if (g) {
        this.isEditing.set(true);
        this.name.set(g.name);
        this.scope.set(g.scope);
        this.targetAmount.set(g.targetAmount);
        this.targetUnit.set(g.targetUnit);
        this.targetDate.set(g.targetDate ?? '');
        this.status.set(g.status);
      } else {
        this.isEditing.set(false);
        this.name.set('');
        this.scope.set('PERSONAL');
        this.targetAmount.set('');
        this.targetUnit.set('USD');
        this.targetDate.set('');
        this.status.set('ACTIVE');
      }
    });
  }

  protected isValid(): boolean {
    const trimmedName = this.name().trim();
    const amount = Number(this.targetAmount());
    return trimmedName.length > 0 && Number.isFinite(amount) && amount > 0;
  }

  protected handleSubmit(): void {
    if (!this.isValid()) {
      return;
    }

    const g = this.goal();
    const targetDateValue = this.targetDate().trim() ? this.targetDate().trim() : null;

    if (g) {
      this.submitted.emit({
        mode: 'edit',
        id: g.id,
        data: {
          name: this.name().trim(),
          targetAmount: this.targetAmount().trim(),
          targetDate: targetDateValue,
          status: this.status(),
        },
      });
    } else {
      this.submitted.emit({
        mode: 'create',
        data: {
          name: this.name().trim(),
          scope: this.scope(),
          targetAmount: this.targetAmount().trim(),
          targetUnit: this.targetUnit(),
          targetDate: targetDateValue,
        },
      });
    }
  }
}
