import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import {
  AccountOption,
  Allowance,
  AllowanceAdjustmentRule,
  AllowanceCreate,
  AllowanceFrequency,
  AllowancePatch,
  FamilyMemberSummary,
} from '../data-access/allowances.models';

export type AllowanceFormSubmitPayload =
  | { mode: 'create'; data: AllowanceCreate }
  | { mode: 'edit'; id: string; data: AllowancePatch };

@Component({
  selector: 'fo-allowance-form-sheet',
  standalone: true,
  imports: [FormsModule, ButtonComponent, IconComponent],
  template: `
    <form (ngSubmit)="handleSubmit()" class="space-y-4">
      <!-- Child selection -->
      <div>
        <label for="allowance-child" class="block text-xs font-medium text-muted">
          Hijo / Destinatario *
        </label>
        @if (isEditing()) {
          <div class="mt-1 rounded-xl border border-border bg-surface-2/60 px-3 py-2 text-sm font-semibold text-text">
            {{ editingChildName() }}
          </div>
        } @else {
          <select
            id="allowance-child"
            name="childUserId"
            required
            [(ngModel)]="childUserId"
            class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
          >
            <option value="" disabled>Seleccioná un hijo</option>
            @for (c of children(); track c.id) {
              <option [value]="c.id">{{ c.fullName }}</option>
            }
          </select>
        }
      </div>

      <!-- Target Account -->
      <div>
        <label for="allowance-target-account" class="block text-xs font-medium text-muted">
          Cuenta del hijo (donde se acredita) *
        </label>
        <select
          id="allowance-target-account"
          name="targetAccountId"
          required
          [(ngModel)]="targetAccountId"
          class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
        >
          <option value="" disabled>Seleccioná la cuenta</option>
          @for (acc of accounts(); track acc.id) {
            <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
          }
        </select>
      </div>

      <!-- Source Account (optional) -->
      <div>
        <label for="allowance-source-account" class="block text-xs font-medium text-muted">
          Cuenta origen de débito (opcional)
        </label>
        <select
          id="allowance-source-account"
          name="sourceAccountId"
          [(ngModel)]="sourceAccountId"
          class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
        >
          <option [value]="null">Sin cuenta origen automática</option>
          @for (acc of accounts(); track acc.id) {
            <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
          }
        </select>
      </div>

      <!-- Amount and Currency -->
      <div class="grid grid-cols-3 gap-2">
        <div class="col-span-2">
          <label for="allowance-amount" class="block text-xs font-medium text-muted">
            Monto base *
          </label>
          <input
            id="allowance-amount"
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
          <label for="allowance-currency" class="block text-xs font-medium text-muted">
            Divisa *
          </label>
          <select
            id="allowance-currency"
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

      <!-- Frequency & Day of Period -->
      <div class="grid grid-cols-2 gap-2">
        <div>
          <label for="allowance-frequency" class="block text-xs font-medium text-muted">
            Frecuencia *
          </label>
          <select
            id="allowance-frequency"
            name="frequency"
            [(ngModel)]="frequency"
            class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
          >
            <option value="MONTHLY">Mensual</option>
            <option value="WEEKLY">Semanal</option>
          </select>
        </div>

        <div>
          <label for="allowance-day" class="block text-xs font-medium text-muted">
            {{ frequency() === 'MONTHLY' ? 'Día del mes (1-31)' : 'Día de la semana (1-7)' }} *
          </label>
          <input
            id="allowance-day"
            name="dayOfPeriod"
            type="number"
            [min]="1"
            [max]="frequency() === 'MONTHLY' ? 31 : 7"
            required
            [(ngModel)]="dayOfPeriod"
            class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
          />
        </div>
      </div>

      <!-- Adjustment Rule -->
      <div>
        <label for="allowance-rule" class="block text-xs font-medium text-muted">
          Regla de ajuste *
        </label>
        <select
          id="allowance-rule"
          name="adjustmentRule"
          [(ngModel)]="adjustmentRule"
          class="fo-focus-ring mt-1 w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
        >
          <option value="MANUAL">Manual / Monto fijo</option>
          <option value="IPC">Ajuste por inflación (IPC)</option>
          <option value="REFERENCE_CURRENCY">Ajuste por divisa de referencia</option>
        </select>
      </div>

      <!-- Active toggle (when editing) -->
      @if (isEditing()) {
        <div class="flex items-center justify-between rounded-xl border border-border bg-surface p-3">
          <div>
            <span class="text-xs font-semibold text-text">Mensualidad activa</span>
            <p class="text-2xs text-muted">Habilitar o pausar esta mensualidad</p>
          </div>
          <label class="relative inline-flex cursor-pointer items-center">
            <input
              type="checkbox"
              name="isActive"
              [(ngModel)]="isActive"
              class="sr-only peer"
            />
            <div class="h-6 w-11 rounded-full bg-border peer-focus:outline-none peer-checked:bg-accent peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-border after:border after:rounded-full after:h-5 after:w-5 after:transition-all"></div>
          </label>
        </div>
      }

      <!-- Actions -->
      <div class="flex items-center justify-between gap-2 border-t border-border pt-3">
        <div>
          @if (isEditing()) {
            <fo-button
              variant="danger"
              size="sm"
              type="button"
              [disabled]="saving()"
              (click)="handleDelete()"
            >
              <fo-icon name="trash-2" [size]="14" />
              <span>Eliminar</span>
            </fo-button>
          }
        </div>

        <div class="flex items-center gap-2">
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
            {{ isEditing() ? 'Guardar cambios' : 'Crear mensualidad' }}
          </fo-button>
        </div>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AllowanceFormSheetComponent {
  readonly allowance = input<Allowance | null>(null);
  readonly children = input<FamilyMemberSummary[]>([]);
  readonly accounts = input<AccountOption[]>([]);
  readonly saving = input<boolean>(false);

  readonly submitted = output<AllowanceFormSubmitPayload>();
  readonly delete = output<Allowance>();
  readonly cancelled = output<void>();

  protected readonly childUserId = signal<string>('');
  protected readonly targetAccountId = signal<string>('');
  protected readonly sourceAccountId = signal<string | null>(null);
  protected readonly amount = signal<string>('');
  protected readonly currencyCode = signal<string>('ARS');
  protected readonly frequency = signal<AllowanceFrequency>('MONTHLY');
  protected readonly dayOfPeriod = signal<number>(1);
  protected readonly adjustmentRule = signal<AllowanceAdjustmentRule>('IPC');
  protected readonly isActive = signal<boolean>(true);

  protected readonly isEditing = signal<boolean>(false);
  protected readonly editingChildName = signal<string>('');

  protected readonly currenciesList = computed(() => {
    const list = ['ARS', 'USD', 'EUR'];
    const accs = this.accounts();
    if (accs) {
      accs.forEach((a) => {
        const code = a.currencyCode?.trim().toUpperCase();
        if (code && !list.includes(code)) {
          list.push(code);
        }
      });
    }
    return list;
  });

  constructor() {
    effect(() => {
      const item = this.allowance();
      if (item) {
        this.isEditing.set(true);
        this.editingChildName.set(item.child.fullName);
        this.childUserId.set(item.child.id);
        this.targetAccountId.set(item.targetAccount.id);
        this.sourceAccountId.set(item.sourceAccount?.id ?? null);
        this.amount.set(item.amount);
        this.currencyCode.set(item.currencyCode);
        this.frequency.set(item.frequency);
        this.dayOfPeriod.set(item.dayOfPeriod);
        this.adjustmentRule.set(item.adjustmentRule);
        this.isActive.set(item.isActive);
      } else {
        this.isEditing.set(false);
        this.editingChildName.set('');
        const firstChild = this.children()[0];
        this.childUserId.set(firstChild ? firstChild.id : '');
        const firstAccount = this.accounts()[0];
        this.targetAccountId.set(firstAccount ? firstAccount.id : '');
        this.sourceAccountId.set(null);
        this.amount.set('');
        this.currencyCode.set('ARS');
        this.frequency.set('MONTHLY');
        this.dayOfPeriod.set(1);
        this.adjustmentRule.set('IPC');
        this.isActive.set(true);
      }
    });
  }

  protected isValid(): boolean {
    const amountVal = Number(this.amount());
    const validAmount = Number.isFinite(amountVal) && amountVal > 0;
    const validChild = this.childUserId().trim().length > 0;
    const validTarget = this.targetAccountId().trim().length > 0;
    const day = this.dayOfPeriod();
    const validDay =
      this.frequency() === 'WEEKLY' ? day >= 1 && day <= 7 : day >= 1 && day <= 31;
    return validAmount && validChild && validTarget && validDay;
  }

  protected handleSubmit(): void {
    if (!this.isValid()) {
      return;
    }

    const item = this.allowance();
    if (item) {
      this.submitted.emit({
        mode: 'edit',
        id: item.id,
        data: {
          targetAccountId: this.targetAccountId(),
          sourceAccountId: this.sourceAccountId(),
          amount: this.amount().trim(),
          currencyCode: this.currencyCode(),
          frequency: this.frequency(),
          dayOfPeriod: this.dayOfPeriod(),
          adjustmentRule: this.adjustmentRule(),
          isActive: this.isActive(),
        },
      });
    } else {
      this.submitted.emit({
        mode: 'create',
        data: {
          childUserId: this.childUserId(),
          targetAccountId: this.targetAccountId(),
          sourceAccountId: this.sourceAccountId(),
          amount: this.amount().trim(),
          currencyCode: this.currencyCode(),
          frequency: this.frequency(),
          dayOfPeriod: this.dayOfPeriod(),
          adjustmentRule: this.adjustmentRule(),
        },
      });
    }
  }

  protected handleDelete(): void {
    const item = this.allowance();
    if (item) {
      this.delete.emit(item);
    }
  }
}
