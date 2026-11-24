import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { Account, Category } from '../../../../core/finance/finance.models';
import {
  ButtonComponent,
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../../shared/ui';
import {
  AdjustmentRule,
  RecurringExpense,
  RecurringExpenseCreate,
  RecurringExpensePatch,
  RecurringScope,
} from '../data-access/recurring.models';
import {
  ADJUSTMENT_RULE_OPTIONS,
  formatEditableDecimal,
  formatTodayIso,
  parsePositiveDecimalInput,
} from '../utils/recurring-format';

const SCOPE_OPTIONS: readonly SegmentedOption<RecurringScope>[] = [
  { value: 'FAMILY', label: 'Familia' },
  { value: 'PERSONAL', label: 'Personal' },
];

@Component({
  selector: 'fo-recurring-expense-form',
  standalone: true,
  imports: [ButtonComponent, SegmentedToggleComponent],
  template: `
    <form class="space-y-4" novalidate (submit)="onSubmit($event)">
      @if (errorMessage(); as apiError) {
        <p
          role="alert"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger"
        >
          {{ apiError }}
        </p>
      }

      <div>
        <label for="fo-rec-name" class="mb-1 block text-xs font-medium text-text">
          Nombre
        </label>
        <input
          id="fo-rec-name"
          data-testid="recurring-name-input"
          type="text"
          maxlength="120"
          [value]="name()"
          (input)="onNameInput($event)"
          [attr.aria-invalid]="nameError() ? 'true' : null"
          placeholder="Ej. Alquiler, Internet, Expensas"
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
        />
        @if (nameError(); as err) {
          <p role="alert" data-testid="recurring-name-error" class="mt-1 text-xs text-danger">
            {{ err }}
          </p>
        }
      </div>

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label for="fo-rec-category" class="mb-1 block text-xs font-medium text-text">
            Categoría
          </label>
          <select
            id="fo-rec-category"
            data-testid="recurring-category-select"
            [value]="categoryId()"
            (change)="onCategoryChange($event)"
            [attr.aria-invalid]="categoryError() ? 'true' : null"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          >
            <option value="">Elegí una categoría</option>
            @for (cat of categories(); track cat.id) {
              <option [value]="cat.id">{{ cat.name }}</option>
            }
          </select>
          @if (categoryError(); as err) {
            <p role="alert" data-testid="recurring-category-error" class="mt-1 text-xs text-danger">
              {{ err }}
            </p>
          }
        </div>

        <div>
          <span class="mb-1 block text-xs font-medium text-text">Alcance</span>
          <fo-segmented-toggle
            [options]="scopeOptions"
            [value]="scope()"
            (valueChange)="scope.set($event)"
            size="sm"
            ariaLabel="Alcance del gasto fijo"
          />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label for="fo-rec-amount" class="mb-1 block text-xs font-medium text-text">
            Monto base
          </label>
          <input
            id="fo-rec-amount"
            data-testid="recurring-amount-input"
            type="text"
            inputmode="decimal"
            [value]="baseAmount()"
            (input)="onAmountInput($event)"
            [attr.aria-invalid]="amountError() ? 'true' : null"
            placeholder="0,00"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text tabular-nums"
          />
          @if (amountError(); as err) {
            <p role="alert" data-testid="recurring-amount-error" class="mt-1 text-xs text-danger">
              {{ err }}
            </p>
          }
        </div>

        <div>
          <label for="fo-rec-currency" class="mb-1 block text-xs font-medium text-text">
            Divisa
          </label>
          <select
            id="fo-rec-currency"
            data-testid="recurring-currency-select"
            [value]="currencyCode()"
            (change)="onCurrencyChange($event)"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          >
            @for (code of currencyOptions(); track code) {
              <option [value]="code">{{ code }}</option>
            }
          </select>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <label for="fo-rec-account" class="mb-1 block text-xs font-medium text-text">
            Cuenta sugerida (opcional)
          </label>
          <select
            id="fo-rec-account"
            data-testid="recurring-account-select"
            [value]="accountId()"
            (change)="onAccountChange($event)"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          >
            <option value="">Sin cuenta sugerida</option>
            @for (acc of matchingAccounts(); track acc.id) {
              <option [value]="acc.id">{{ acc.name }}</option>
            }
          </select>
        </div>

        <div>
          <label for="fo-rec-due-day" class="mb-1 block text-xs font-medium text-text">
            Día de vencimiento (1–31)
          </label>
          <input
            id="fo-rec-due-day"
            data-testid="recurring-due-day-input"
            type="number"
            min="1"
            max="31"
            step="1"
            [value]="dueDay()"
            (input)="onDueDayInput($event)"
            [attr.aria-invalid]="dueDayError() ? 'true' : null"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text tabular-nums"
          />
          @if (dueDayError(); as err) {
            <p role="alert" data-testid="recurring-due-day-error" class="mt-1 text-xs text-danger">
              {{ err }}
            </p>
          }
        </div>
      </div>

      <fieldset class="space-y-2">
        <legend class="text-xs font-medium text-text">Regla de ajuste</legend>
        <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
          @for (opt of ruleOptions; track opt.value) {
            @let selected = adjustmentRule() === opt.value;
            <label
              class="fo-focus-ring flex cursor-pointer items-start gap-2.5 rounded-xl border p-2.5 transition-colors"
              [class.border-accent]="selected"
              [class.bg-accent-soft/45]="selected"
              [class.border-border]="!selected"
              [class.bg-surface]="!selected"
            >
              <input
                type="radio"
                name="adjustmentRule"
                [value]="opt.value"
                [checked]="selected"
                (change)="adjustmentRule.set(opt.value)"
                class="mt-0.5 accent-[var(--fo-accent)]"
              />
              <span class="min-w-0">
                <span class="block text-xs font-semibold text-text">{{ opt.label }}</span>
                <span class="block text-[11px] text-muted">{{ opt.description }}</span>
              </span>
            </label>
          }
        </div>
      </fieldset>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label for="fo-rec-start-date" class="mb-1 block text-xs font-medium text-text">
            Fecha de inicio
          </label>
          <input
            id="fo-rec-start-date"
            data-testid="recurring-start-date-input"
            type="date"
            [value]="startDate()"
            (input)="onStartDateInput($event)"
            [attr.aria-invalid]="startDateError() ? 'true' : null"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          />
          @if (startDateError(); as err) {
            <p role="alert" class="mt-1 text-xs text-danger">{{ err }}</p>
          }
        </div>

        <div>
          <label for="fo-rec-end-date" class="mb-1 block text-xs font-medium text-text">
            Fecha de fin (opcional)
          </label>
          <input
            id="fo-rec-end-date"
            data-testid="recurring-end-date-input"
            type="date"
            [value]="endDate()"
            (input)="onEndDateInput($event)"
            [attr.aria-invalid]="endDateError() ? 'true' : null"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
          />
          @if (endDateError(); as err) {
            <p role="alert" data-testid="recurring-end-date-error" class="mt-1 text-xs text-danger">
              {{ err }}
            </p>
          }
        </div>
      </div>

      <div class="flex items-center justify-end gap-2 pt-2">
        <fo-button
          type="button"
          variant="ghost"
          [disabled]="saving()"
          (click)="cancelled.emit()"
        >
          Cancelar
        </fo-button>
        <fo-button
          type="submit"
          variant="primary"
          data-testid="recurring-submit-btn"
          [loading]="saving()"
        >
          {{ isEditMode() ? 'Guardar cambios' : 'Crear gasto fijo' }}
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecurringExpenseFormComponent {
  readonly expense = input<RecurringExpense | null>(null);
  readonly categories = input.required<readonly Category[]>();
  readonly accounts = input.required<readonly Account[]>();
  readonly enabledCurrencies = input<readonly string[]>(['ARS', 'USD']);
  readonly baseCurrency = input<string>('ARS');
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly createSubmitted = output<RecurringExpenseCreate>();
  readonly updateSubmitted = output<RecurringExpensePatch>();
  readonly cancelled = output<void>();

  protected readonly scopeOptions = SCOPE_OPTIONS;
  protected readonly ruleOptions = ADJUSTMENT_RULE_OPTIONS;

  protected readonly name = signal<string>('');
  protected readonly categoryId = signal<string>('');
  protected readonly accountId = signal<string>('');
  protected readonly scope = signal<RecurringScope>('FAMILY');
  protected readonly baseAmount = signal<string>('');
  protected readonly currencyCode = signal<string>('ARS');
  protected readonly dueDay = signal<string>('10');
  protected readonly adjustmentRule = signal<AdjustmentRule>('MANUAL');
  protected readonly startDate = signal<string>(formatTodayIso());
  protected readonly endDate = signal<string>('');
  protected readonly submittedAttempt = signal<boolean>(false);

  protected readonly isEditMode = computed<boolean>(() => this.expense() !== null);

  protected readonly currencyOptions = computed<string[]>(() => {
    const set = new Set<string>();
    const current = this.currencyCode().trim().toUpperCase();
    const base = this.baseCurrency().trim().toUpperCase();
    if (base) {
      set.add(base);
    }
    for (const code of this.enabledCurrencies()) {
      const normalized = code.trim().toUpperCase();
      if (normalized) {
        set.add(normalized);
      }
    }
    if (current) {
      set.add(current);
    }
    return Array.from(set);
  });

  protected readonly matchingAccounts = computed<Account[]>(() => {
    const code = this.currencyCode().trim().toUpperCase();
    return this.accounts().filter(
      (acc) => acc.isActive && acc.currencyCode.trim().toUpperCase() === code,
    );
  });

  protected readonly nameError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    const trimmed = this.name().trim();
    if (trimmed.length === 0) {
      return 'Ingresá un nombre para el gasto fijo.';
    }
    if (trimmed.length > 120) {
      return 'El nombre no puede superar los 120 caracteres.';
    }
    return null;
  });

  protected readonly categoryError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    return this.categoryId().trim().length === 0 ? 'Elegí una categoría.' : null;
  });

  protected readonly amountError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    return parsePositiveDecimalInput(this.baseAmount()) === null
      ? 'Ingresá un monto mayor a 0.'
      : null;
  });

  protected readonly dueDayError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    const parsed = this.parseDueDay();
    if (parsed === null) {
      return 'El día de vencimiento debe estar entre 1 y 31.';
    }
    return null;
  });

  protected readonly startDateError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    return this.startDate().trim().length === 0 ? 'Elegí la fecha de inicio.' : null;
  });

  protected readonly endDateError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    const end = this.endDate().trim();
    const start = this.startDate().trim();
    if (end && start && end < start) {
      return 'La fecha de fin no puede ser anterior a la de inicio.';
    }
    return null;
  });

  constructor() {
    effect(() => {
      const target = this.expense();
      this.submittedAttempt.set(false);
      if (target) {
        this.name.set(target.name);
        this.categoryId.set(target.category.id);
        this.accountId.set(target.account?.id ?? '');
        this.scope.set(target.scope);
        this.baseAmount.set(formatEditableDecimal(target.baseAmount));
        this.currencyCode.set(target.currencyCode.trim().toUpperCase());
        this.dueDay.set(String(target.dueDay));
        this.adjustmentRule.set(target.adjustmentRule);
        this.startDate.set(target.startDate);
        this.endDate.set(target.endDate ?? '');
      } else {
        this.name.set('');
        this.categoryId.set('');
        this.accountId.set('');
        this.scope.set('FAMILY');
        this.baseAmount.set('');
        this.currencyCode.set(this.baseCurrency().trim().toUpperCase() || 'ARS');
        this.dueDay.set('10');
        this.adjustmentRule.set('MANUAL');
        this.startDate.set(formatTodayIso());
        this.endDate.set('');
      }
    });
  }

  protected onNameInput(event: Event): void {
    this.name.set((event.target as HTMLInputElement).value);
  }

  protected onCategoryChange(event: Event): void {
    this.categoryId.set((event.target as HTMLSelectElement).value);
  }

  protected onAmountInput(event: Event): void {
    this.baseAmount.set((event.target as HTMLInputElement).value);
  }

  protected onCurrencyChange(event: Event): void {
    const nextCurrency = (event.target as HTMLSelectElement).value;
    this.currencyCode.set(nextCurrency);
    const currentAccountId = this.accountId();
    if (
      currentAccountId &&
      !this.matchingAccounts().some((acc) => acc.id === currentAccountId)
    ) {
      this.accountId.set('');
    }
  }

  protected onAccountChange(event: Event): void {
    this.accountId.set((event.target as HTMLSelectElement).value);
  }

  protected onDueDayInput(event: Event): void {
    this.dueDay.set((event.target as HTMLInputElement).value);
  }

  protected onStartDateInput(event: Event): void {
    this.startDate.set((event.target as HTMLInputElement).value);
  }

  protected onEndDateInput(event: Event): void {
    this.endDate.set((event.target as HTMLInputElement).value);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.submittedAttempt.set(true);

    const parsedAmount = parsePositiveDecimalInput(this.baseAmount());
    const parsedDueDay = this.parseDueDay();
    if (
      this.nameError() ||
      this.categoryError() ||
      parsedAmount === null ||
      parsedDueDay === null ||
      this.startDateError() ||
      this.endDateError()
    ) {
      return;
    }

    const trimmedName = this.name().trim();
    const selectedCategory = this.categoryId().trim();
    const selectedAccount = this.accountId().trim();
    const selectedStart = this.startDate().trim();
    const selectedEnd = this.endDate().trim();

    if (this.isEditMode()) {
      const patch: RecurringExpensePatch = {
        name: trimmedName,
        categoryId: selectedCategory,
        accountId: selectedAccount || null,
        scope: this.scope(),
        baseAmount: parsedAmount,
        currencyCode: this.currencyCode().trim().toUpperCase(),
        dueDay: parsedDueDay,
        adjustmentRule: this.adjustmentRule(),
        startDate: selectedStart,
        endDate: selectedEnd || null,
      };
      this.updateSubmitted.emit(patch);
      return;
    }

    const payload: RecurringExpenseCreate = {
      name: trimmedName,
      categoryId: selectedCategory,
      ...(selectedAccount ? { accountId: selectedAccount } : {}),
      scope: this.scope(),
      baseAmount: parsedAmount,
      currencyCode: this.currencyCode().trim().toUpperCase(),
      dueDay: parsedDueDay,
      adjustmentRule: this.adjustmentRule(),
      startDate: selectedStart,
      ...(selectedEnd ? { endDate: selectedEnd } : {}),
    };
    this.createSubmitted.emit(payload);
  }

  private parseDueDay(): number | null {
    const raw = this.dueDay().trim();
    if (!/^\d+$/.test(raw)) {
      return null;
    }
    const value = Number(raw);
    if (!Number.isInteger(value) || value < 1 || value > 31) {
      return null;
    }
    return value;
  }
}
