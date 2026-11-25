import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { Allowance, DisbursePayload } from '../data-access/allowances.models';

function formatTodayIso(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

@Component({
  selector: 'fo-allowance-disburse-sheet',
  standalone: true,
  imports: [FormsModule, ButtonComponent, IconComponent, MoneyPipe],
  template: `
    @if (allowance(); as item) {
      <div class="mb-4 rounded-xl border border-border bg-surface-2/60 p-3.5">
        <div class="flex items-center gap-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-accent-soft text-accent">
            <fo-icon name="banknote" [size]="16" />
          </div>
          <div>
            <h4 class="text-sm font-semibold text-text">
              Acreditar mensualidad para {{ item.child.fullName }}
            </h4>
            <span class="text-xs text-muted">
              Se acreditará en {{ item.targetAccount.name }}
            </span>
          </div>
        </div>
      </div>

      <form (ngSubmit)="handleConfirm()" class="space-y-4">
        <!-- Amount -->
        <div>
          <label for="disburse-amount" class="block text-xs font-medium text-muted">
            Monto a acreditar ({{ item.currencyCode }}) *
          </label>
          <input
            id="disburse-amount"
            name="amount"
            type="number"
            step="any"
            min="0.01"
            required
            [(ngModel)]="amount"
            class="fo-focus-ring mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-base font-bold text-text tabular-nums"
          />
          <p class="mt-1 text-2xs text-muted">
            Monto vigente habitual: {{ item.currentAmount | money: item.currencyCode }}. Podés modificarlo si corresponde.
          </p>
        </div>

        <!-- Date -->
        <div>
          <label for="disburse-date" class="block text-xs font-medium text-muted">
            Fecha de acreditación
          </label>
          <input
            id="disburse-date"
            name="date"
            type="date"
            [(ngModel)]="date"
            class="fo-focus-ring mt-1 w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          />
        </div>

        @if (item.sourceAccount) {
          <p class="rounded-xl border border-border bg-surface-2/40 px-3 py-2 text-xs text-muted">
            Se debitará de la cuenta origen: <strong class="text-text">{{ item.sourceAccount.name }}</strong>.
          </p>
        }

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
            Confirmar acreditación
          </fo-button>
        </div>
      </form>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AllowanceDisburseSheetComponent {
  readonly allowance = input<Allowance | null>(null);
  readonly saving = input<boolean>(false);

  readonly confirmed = output<DisbursePayload>();
  readonly cancelled = output<void>();

  protected readonly amount = signal<string>('');
  protected readonly date = signal<string>(formatTodayIso());

  constructor() {
    effect(() => {
      const item = this.allowance();
      if (item) {
        this.amount.set(item.currentAmount);
        this.date.set(formatTodayIso());
      } else {
        this.amount.set('');
        this.date.set(formatTodayIso());
      }
    });
  }

  protected isValid(): boolean {
    const val = Number(this.amount());
    return Number.isFinite(val) && val > 0;
  }

  protected handleConfirm(): void {
    if (!this.isValid()) {
      return;
    }

    this.confirmed.emit({
      amount: this.amount().trim(),
      date: this.date().trim() || undefined,
    });
  }
}
