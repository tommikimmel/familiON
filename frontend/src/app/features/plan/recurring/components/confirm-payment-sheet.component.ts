import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { Account } from '../../../../core/finance/finance.models';
import { ButtonComponent } from '../../../../shared/ui';
import {
  ConfirmPaymentPayload,
  RecurringExpense,
} from '../data-access/recurring.models';
import {
  formatEditableDecimal,
  formatMonthLabel,
  formatShortDayMonth,
  formatTodayIso,
  parsePositiveDecimalInput,
} from '../utils/recurring-format';

@Component({
  selector: 'fo-confirm-payment-sheet',
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <form class="space-y-4" novalidate (submit)="onSubmit($event)">
      <div class="rounded-xl border border-border bg-surface-2/60 px-3.5 py-2.5 text-xs text-muted">
        <p class="font-semibold text-text">{{ expense().name }}</p>
        <p class="mt-0.5">
          {{ monthLabel() }} · Vencimiento {{ dueDateLabel() }}
        </p>
      </div>

      @if (errorMessage(); as apiError) {
        <p
          role="alert"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger"
        >
          {{ apiError }}
        </p>
      }

      <div>
        <label for="fo-pay-amount" class="mb-1 block text-xs font-medium text-text">
          Monto ({{ expense().currencyCode }})
        </label>
        <input
          id="fo-pay-amount"
          data-testid="pay-amount-input"
          type="text"
          inputmode="decimal"
          [value]="amount()"
          (input)="onAmountInput($event)"
          [attr.aria-invalid]="amountError() ? 'true' : null"
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2.5 text-base font-semibold text-text tabular-nums"
          placeholder="0,00"
        />
        @if (amountError(); as err) {
          <p role="alert" class="mt-1 text-xs text-danger">{{ err }}</p>
        }
      </div>

      <div>
        <label for="fo-pay-account" class="mb-1 block text-xs font-medium text-text">
          Cuenta
        </label>
        @if (currencyAccounts().length > 0) {
          <select
            id="fo-pay-account"
            data-testid="pay-account-select"
            [value]="accountId()"
            (change)="onAccountChange($event)"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2.5 text-sm text-text"
          >
            @for (acc of currencyAccounts(); track acc.id) {
              <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
            }
          </select>
        } @else {
          <p class="rounded-xl border border-border bg-surface-2 px-3 py-2 text-xs text-muted">
            No encontramos cuentas activas en {{ expense().currencyCode }}. Creá una cuenta en esa
            divisa para registrar el pago.
          </p>
        }
      </div>

      <div>
        <label for="fo-pay-date" class="mb-1 block text-xs font-medium text-text">
          Fecha de pago
        </label>
        <input
          id="fo-pay-date"
          data-testid="pay-date-input"
          type="date"
          [value]="paymentDate()"
          (input)="onDateInput($event)"
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2.5 text-sm text-text"
        />
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
          data-testid="confirm-payment-submit-btn"
          [disabled]="currencyAccounts().length === 0"
          [loading]="saving()"
        >
          Confirmar pago
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmPaymentSheetComponent {
  readonly expense = input.required<RecurringExpense>();
  readonly accounts = input.required<readonly Account[]>();
  readonly defaultAccountId = input<string | null>(null);
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly confirmed = output<ConfirmPaymentPayload>();
  readonly cancelled = output<void>();

  protected readonly amount = signal<string>('');
  protected readonly accountId = signal<string>('');
  protected readonly paymentDate = signal<string>(formatTodayIso());
  protected readonly submittedAttempt = signal<boolean>(false);

  protected readonly currencyAccounts = computed<Account[]>(() => {
    const code = this.expense().currencyCode.trim().toUpperCase();
    return this.accounts().filter(
      (acc) => acc.isActive && acc.currencyCode.trim().toUpperCase() === code,
    );
  });

  protected readonly monthLabel = computed<string>(() =>
    formatMonthLabel(this.expense().month.month),
  );

  protected readonly dueDateLabel = computed<string>(() =>
    formatShortDayMonth(this.expense().month.dueDate),
  );

  protected readonly amountError = computed<string | null>(() => {
    if (!this.submittedAttempt()) {
      return null;
    }
    if (parsePositiveDecimalInput(this.amount()) === null) {
      return 'Ingresá un monto mayor a 0.';
    }
    return null;
  });

  constructor() {
    effect(() => {
      const target = this.expense();
      this.amount.set(formatEditableDecimal(target.month.estimatedAmount));
      this.paymentDate.set(formatTodayIso());
      this.submittedAttempt.set(false);
    });

    effect(() => {
      const available = this.currencyAccounts();
      const preferred = this.defaultAccountId();
      const current = this.accountId();

      if (current && available.some((acc) => acc.id === current)) {
        return;
      }
      if (preferred && available.some((acc) => acc.id === preferred)) {
        this.accountId.set(preferred);
        return;
      }
      this.accountId.set(available[0]?.id ?? '');
    });
  }

  protected onAmountInput(event: Event): void {
    this.amount.set((event.target as HTMLInputElement).value);
  }

  protected onAccountChange(event: Event): void {
    this.accountId.set((event.target as HTMLSelectElement).value);
  }

  protected onDateInput(event: Event): void {
    this.paymentDate.set((event.target as HTMLInputElement).value);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.submittedAttempt.set(true);

    const parsedAmount = parsePositiveDecimalInput(this.amount());
    if (parsedAmount === null) {
      return;
    }

    const selectedAccount = this.accountId().trim();
    const selectedDate = this.paymentDate().trim();

    const payload: ConfirmPaymentPayload = {
      month: this.expense().month.month,
      amount: parsedAmount,
      ...(selectedAccount ? { accountId: selectedAccount } : {}),
      ...(selectedDate ? { paymentDate: selectedDate } : {}),
    };

    this.confirmed.emit(payload);
  }
}
