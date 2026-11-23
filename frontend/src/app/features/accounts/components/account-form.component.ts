import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import {
  ACCOUNT_TYPE_ICON,
  ACCOUNT_TYPE_LABEL,
  ACCOUNT_TYPE_ORDER,
} from '../../../core/finance/account-labels';
import { Account, AccountCreate, AccountType } from '../../../core/finance/finance.models';
import { ButtonComponent, IconComponent } from '../../../shared/ui';
import { FamilyMemberOption } from '../data-access/family-members.api';

const DECIMAL_PATTERN = /^-?\d+([.,]\d{1,4})?$/;
const POSITIVE_DECIMAL_PATTERN = /^\d+([.,]\d{1,4})?$/;

function trimmedRequiredValidator(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value.length > 0 ? null : { required: true };
}

function optionalDecimalValidator(control: AbstractControl): ValidationErrors | null {
  const raw = typeof control.value === 'string' ? control.value.trim() : String(control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  return DECIMAL_PATTERN.test(raw) ? null : { decimal: true };
}

function positiveDecimalValidator(control: AbstractControl): ValidationErrors | null {
  const raw = typeof control.value === 'string' ? control.value.trim() : String(control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!POSITIVE_DECIMAL_PATTERN.test(raw)) {
    return { positiveDecimal: true };
  }
  const numeric = Number(raw.replace(',', '.'));
  return Number.isFinite(numeric) && numeric > 0 ? null : { positiveDecimal: true };
}

function dayOfMonthValidator(control: AbstractControl): ValidationErrors | null {
  const raw = typeof control.value === 'string' ? control.value.trim() : String(control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!/^\d+$/.test(raw)) {
    return { dayOfMonth: true };
  }
  const day = Number.parseInt(raw, 10);
  return day >= 1 && day <= 31 ? null : { dayOfMonth: true };
}

function normalizeDecimal(raw: string): string {
  const trimmed = raw.trim().replace(',', '.');
  return trimmed === '' ? '0' : trimmed;
}

@Component({
  selector: 'fo-account-form',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4" novalidate>
      @if (errorMessage(); as error) {
        <div
          role="alert"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger"
        >
          {{ error }}
        </div>
      }

      <div class="space-y-1.5">
        <label for="fo-account-name" class="block text-sm font-medium text-text">
          Nombre de la cuenta
        </label>
        <input
          id="fo-account-name"
          type="text"
          formControlName="name"
          maxlength="80"
          placeholder="Ej. Mercado Pago, Galicia ARS, Visa"
          [attr.aria-invalid]="isInvalid('name')"
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
        />
        @if (isInvalid('name')) {
          <p role="alert" class="text-xs text-danger">Ingresá un nombre para la cuenta.</p>
        }
      </div>

      <fieldset class="space-y-2">
        <legend class="text-sm font-medium text-text">Tipo de cuenta</legend>
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-3" role="group" aria-label="Tipo de cuenta">
          @for (type of accountTypes; track type) {
            @let selected = form.controls.accountType.value === type;
            <button
              type="button"
              [attr.data-testid]="'account-type-' + type"
              [attr.aria-pressed]="selected"
              (click)="selectType(type)"
              class="fo-focus-ring flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 text-left text-xs font-medium transition-colors"
              [class.border-accent]="selected"
              [class.bg-accent-soft]="selected"
              [class.text-accent]="selected"
              [class.border-border]="!selected"
              [class.bg-surface]="!selected"
              [class.text-text]="!selected"
              [class.hover:bg-surface-2]="!selected"
            >
              <fo-icon [name]="accountTypeIcon[type]" [size]="16" />
              <span class="truncate">{{ accountTypeLabel[type] }}</span>
            </button>
          }
        </div>
      </fieldset>

      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1.5">
          <label for="fo-account-currency" class="block text-sm font-medium text-text">
            Divisa
          </label>
          <select
            id="fo-account-currency"
            formControlName="currencyCode"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
          >
            @for (currency of currencyOptions(); track currency) {
              <option [value]="currency">{{ currency }}</option>
            }
          </select>
        </div>

        <div class="space-y-1.5">
          <label for="fo-account-initial-balance" class="block text-sm font-medium text-text">
            Saldo inicial
          </label>
          <input
            id="fo-account-initial-balance"
            type="text"
            inputmode="decimal"
            formControlName="initialBalance"
            placeholder="0"
            [attr.aria-invalid]="isInvalid('initialBalance')"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
          />
          @if (isInvalid('initialBalance')) {
            <p role="alert" class="text-xs text-danger">Ingresá un monto válido.</p>
          }
        </div>
      </div>

      @if (isAdult()) {
        <div class="space-y-1.5">
          <label for="fo-account-owner" class="block text-sm font-medium text-text">
            Titular
          </label>
          <select
            id="fo-account-owner"
            formControlName="ownerSelection"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
          >
            <option value="SELF">Yo</option>
            <option value="SHARED">Compartida</option>
            @for (member of otherMembers(); track member.id) {
              <option [value]="member.id">{{ member.fullName }}</option>
            }
          </select>
        </div>
      }

      @if (isCreditCard()) {
        <div class="space-y-3 rounded-xl border border-border bg-surface-2/60 p-3.5">
          <p class="text-xs font-semibold tracking-wide text-muted uppercase">
            Datos de tarjeta de crédito
          </p>

          <div class="space-y-1.5">
            <label for="fo-account-credit-limit" class="block text-sm font-medium text-text">
              Límite de crédito
            </label>
            <input
              id="fo-account-credit-limit"
              type="text"
              inputmode="decimal"
              formControlName="creditLimit"
              placeholder="Ej. 500000"
              [attr.aria-invalid]="isInvalid('creditLimit')"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
            />
            @if (isInvalid('creditLimit')) {
              <p role="alert" class="text-xs text-danger">
                Ingresá un límite mayor a cero para la tarjeta.
              </p>
            }
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1.5">
              <label for="fo-account-closing-day" class="block text-sm font-medium text-text">
                Día de cierre (1–31)
              </label>
              <input
                id="fo-account-closing-day"
                type="number"
                min="1"
                max="31"
                step="1"
                formControlName="closingDay"
                placeholder="Ej. 20"
                [attr.aria-invalid]="isInvalid('closingDay')"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
              />
              @if (isInvalid('closingDay')) {
                <p role="alert" class="text-xs text-danger">Debe ser un día entre 1 y 31.</p>
              }
            </div>

            <div class="space-y-1.5">
              <label for="fo-account-due-day" class="block text-sm font-medium text-text">
                Día de vencimiento (1–31)
              </label>
              <input
                id="fo-account-due-day"
                type="number"
                min="1"
                max="31"
                step="1"
                formControlName="dueDay"
                placeholder="Ej. 5"
                [attr.aria-invalid]="isInvalid('dueDay')"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
              />
              @if (isInvalid('dueDay')) {
                <p role="alert" class="text-xs text-danger">Debe ser un día entre 1 y 31.</p>
              }
            </div>
          </div>
        </div>
      }

      <div class="flex items-center justify-end gap-2 pt-2">
        <fo-button variant="ghost" type="button" [disabled]="saving()" (click)="cancelled.emit()">
          Cancelar
        </fo-button>
        <fo-button variant="primary" type="submit" [loading]="saving()">
          {{ account() ? 'Guardar cambios' : 'Crear cuenta' }}
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountFormComponent {
  private readonly fb = inject(FormBuilder);

  readonly account = input<Account | null>(null);
  readonly enabledCurrencies = input<readonly string[]>(['ARS', 'USD']);
  readonly defaultCurrency = input<string>('ARS');
  readonly isAdult = input<boolean>(false);
  readonly currentUserId = input<string | null>(null);
  readonly members = input<readonly FamilyMemberOption[]>([]);
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly submitted = output<AccountCreate>();
  readonly cancelled = output<void>();

  protected readonly accountTypes = ACCOUNT_TYPE_ORDER;
  protected readonly accountTypeLabel = ACCOUNT_TYPE_LABEL;
  protected readonly accountTypeIcon = ACCOUNT_TYPE_ICON;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(80)]],
    accountType: this.fb.nonNullable.control<AccountType>('CASH', [Validators.required]),
    currencyCode: ['ARS', [Validators.required]],
    initialBalance: ['0', [optionalDecimalValidator]],
    ownerSelection: ['SELF'],
    creditLimit: [''],
    closingDay: [''],
    dueDay: [''],
  });

  protected readonly currencyOptions = computed<string[]>(() => {
    const enabled = this.enabledCurrencies().map((c) => c.trim().toUpperCase()).filter(Boolean);
    const base = enabled.length > 0 ? [...enabled] : ['ARS', 'USD'];
    const editingCurrency = this.account()?.currencyCode?.trim().toUpperCase();
    if (editingCurrency && !base.includes(editingCurrency)) {
      base.push(editingCurrency);
    }
    return base;
  });

  protected readonly otherMembers = computed(() => {
    const meId = this.currentUserId();
    return this.members().filter((member) => member.isActive && member.id !== meId);
  });

  constructor() {
    effect(() => {
      const existing = this.account();
      const currencies = this.currencyOptions();
      const fallbackCurrency = currencies.includes(this.defaultCurrency())
        ? this.defaultCurrency()
        : (currencies[0] ?? 'ARS');

      if (existing) {
        this.form.reset(
          {
            name: existing.name,
            accountType: existing.accountType,
            currencyCode: existing.currencyCode,
            initialBalance: existing.initialBalance ?? '0',
            ownerSelection: this.resolveOwnerSelection(existing.ownerUserId),
            creditLimit: existing.creditLimit ?? '',
            closingDay: existing.closingDay !== null ? String(existing.closingDay) : '',
            dueDay: existing.dueDay !== null ? String(existing.dueDay) : '',
          },
          { emitEvent: false },
        );
        this.syncCreditCardValidators(existing.accountType);
      } else {
        this.form.reset(
          {
            name: '',
            accountType: 'CASH',
            currencyCode: fallbackCurrency,
            initialBalance: '0',
            ownerSelection: 'SELF',
            creditLimit: '',
            closingDay: '',
            dueDay: '',
          },
          { emitEvent: false },
        );
        this.syncCreditCardValidators('CASH');
      }
    });
  }

  protected isCreditCard(): boolean {
    return this.form.controls.accountType.value === 'CREDIT_CARD';
  }

  protected selectType(type: AccountType): void {
    this.form.controls.accountType.setValue(type);
    this.syncCreditCardValidators(type);
  }

  protected isInvalid(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  protected onSubmit(): void {
    this.syncCreditCardValidators(this.form.controls.accountType.value);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    const raw = this.form.getRawValue();
    const payload: AccountCreate = {
      name: raw.name.trim(),
      accountType: raw.accountType,
      currencyCode: raw.currencyCode.trim().toUpperCase(),
      initialBalance: normalizeDecimal(raw.initialBalance),
    };

    if (this.isAdult()) {
      if (raw.ownerSelection === 'SHARED') {
        payload.ownerUserId = null;
      } else if (raw.ownerSelection === 'SELF') {
        const meId = this.currentUserId();
        if (meId) {
          payload.ownerUserId = meId;
        }
      } else if (raw.ownerSelection) {
        payload.ownerUserId = raw.ownerSelection;
      }
    }

    if (raw.accountType === 'CREDIT_CARD') {
      payload.creditLimit = normalizeDecimal(raw.creditLimit);
      payload.closingDay = Number.parseInt(String(raw.closingDay).trim(), 10);
      payload.dueDay = Number.parseInt(String(raw.dueDay).trim(), 10);
    }

    this.submitted.emit(payload);
  }

  private resolveOwnerSelection(ownerUserId: string | null): string {
    if (ownerUserId === null) {
      return 'SHARED';
    }
    if (ownerUserId === this.currentUserId()) {
      return 'SELF';
    }
    return ownerUserId;
  }

  private syncCreditCardValidators(type: AccountType): void {
    const isCard = type === 'CREDIT_CARD';
    const limitValidators: ValidatorFn[] = isCard
      ? [Validators.required, positiveDecimalValidator]
      : [];
    const dayValidators: ValidatorFn[] = isCard ? [Validators.required, dayOfMonthValidator] : [];

    this.form.controls.creditLimit.setValidators(limitValidators);
    this.form.controls.closingDay.setValidators(dayValidators);
    this.form.controls.dueDay.setValidators(dayValidators);

    this.form.controls.creditLimit.updateValueAndValidity({ emitEvent: false });
    this.form.controls.closingDay.updateValueAndValidity({ emitEvent: false });
    this.form.controls.dueDay.updateValueAndValidity({ emitEvent: false });
  }
}
