import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { ContributionCreate, SavingsGoal, TargetUnit } from '../data-access/goals.models';
import { formatTargetUnit } from '../utils/goal-formatters';

function formatTodayIso(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

@Component({
  selector: 'fo-goal-contribution-sheet',
  standalone: true,
  imports: [FormsModule, ButtonComponent],
  template: `
    @if (goal(); as g) {
      <div class="mb-4 rounded-xl border border-border bg-surface-2/60 p-3">
        <span class="text-xs text-muted">Aportar a</span>
        <h4 class="text-sm font-semibold text-text">{{ g.name }}</h4>
        <p class="text-xs text-muted">
          Meta en {{ targetUnitLabel(g.targetUnit) }} · Restante: {{ g.remainingInUnit }} {{ targetUnitLabel(g.targetUnit) }}
        </p>
      </div>

      <form (ngSubmit)="handleSubmit()" class="space-y-4">
        <!-- Amount & Currency -->
        <div class="grid grid-cols-3 gap-2">
          <div class="col-span-2">
            <label for="contrib-amount" class="block text-xs font-medium text-muted">
              Monto del aporte *
            </label>
            <input
              id="contrib-amount"
              name="amount"
              type="number"
              step="any"
              min="0.01"
              required
              [(ngModel)]="amount"
              placeholder="0.00"
              class="fo-focus-ring mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm font-semibold text-text placeholder:text-muted tabular-nums"
            />
          </div>

          <div>
            <label for="contrib-currency" class="block text-xs font-medium text-muted">
              Divisa *
            </label>
            <select
              id="contrib-currency"
              name="currencyCode"
              [(ngModel)]="currencyCode"
              class="fo-focus-ring mt-1 w-full rounded-xl border border-border bg-surface px-2.5 py-2 text-sm text-text"
            >
              @for (cur of currenciesList(); track cur) {
                <option [value]="cur">{{ cur }}</option>
              }
            </select>
          </div>
        </div>

        <!-- Date -->
        <div>
          <label for="contrib-date" class="block text-xs font-medium text-muted">
            Fecha del aporte
          </label>
          <input
            id="contrib-date"
            name="contributionDate"
            type="date"
            [(ngModel)]="contributionDate"
            class="fo-focus-ring mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          />
        </div>

        <!-- Notes -->
        <div>
          <label for="contrib-notes" class="block text-xs font-medium text-muted">
            Notas (opcional)
          </label>
          <input
            id="contrib-notes"
            name="notes"
            type="text"
            maxlength="255"
            [(ngModel)]="notes"
            placeholder="Ej: Ahorro de aguinaldo, vuelto del viaje"
            class="fo-focus-ring mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text placeholder:text-muted"
          />
        </div>

        <!-- Actions -->
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
            Registrar aporte
          </fo-button>
        </div>
      </form>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalContributionSheetComponent {
  readonly goal = input<SavingsGoal | null>(null);
  readonly currencies = input<string[]>([]);
  readonly saving = input<boolean>(false);

  readonly submitted = output<ContributionCreate>();
  readonly cancelled = output<void>();

  protected readonly amount = signal<string>('');
  protected readonly currencyCode = signal<string>('ARS');
  protected readonly contributionDate = signal<string>(formatTodayIso());
  protected readonly notes = signal<string>('');

  protected readonly currenciesList = signal<string[]>(['ARS', 'USD', 'EUR']);

  constructor() {
    effect(() => {
      const g = this.goal();
      const curs = this.currencies();

      // Collect available currencies
      const set = new Set<string>();
      if (curs && curs.length > 0) {
        curs.forEach((c) => set.add(c.trim().toUpperCase()));
      }
      set.add('ARS');
      set.add('USD');
      if (g && g.targetUnit !== 'ARS_IPC' && g.targetUnit !== 'UVA') {
        set.add(g.targetUnit);
      }
      const list = Array.from(set);
      this.currenciesList.set(list);

      // Default currency to goal unit if standard currency, or base
      if (g && (g.targetUnit === 'USD' || g.targetUnit === 'EUR' || g.targetUnit === 'ARS')) {
        this.currencyCode.set(g.targetUnit);
      } else {
        this.currencyCode.set(list[0] || 'ARS');
      }

      this.amount.set('');
      this.contributionDate.set(formatTodayIso());
      this.notes.set('');
    });
  }

  protected targetUnitLabel(unit: string): string {
    return formatTargetUnit(unit as TargetUnit);
  }

  protected isValid(): boolean {
    const val = Number(this.amount());
    return Number.isFinite(val) && val > 0 && this.currencyCode().trim().length > 0;
  }

  protected handleSubmit(): void {
    if (!this.isValid()) {
      return;
    }

    this.submitted.emit({
      amount: this.amount().trim(),
      currencyCode: this.currencyCode().trim().toUpperCase(),
      contributionDate: this.contributionDate().trim() || undefined,
      notes: this.notes().trim() || null,
    });
  }
}
