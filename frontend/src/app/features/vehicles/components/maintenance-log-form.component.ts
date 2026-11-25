import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Account } from '../../../core/finance/finance.models';
import { ButtonComponent, IconComponent } from '../../../shared/ui';
import {
  MAINTENANCE_DUE_SUGGESTIONS,
  MAINTENANCE_TYPE_ICON,
  MAINTENANCE_TYPE_LABEL,
  MAINTENANCE_TYPE_OPTIONS,
  addMonthsToIsoDate,
  formatKm,
  getTodayIsoDate,
} from '../data-access/vehicle-labels';
import {
  MaintenanceLogCreate,
  MaintenanceType,
  Vehicle,
} from '../data-access/vehicles.models';

const NON_NEGATIVE_DECIMAL_PATTERN = /^\d+([.,]\d{1,4})?$/;

function trimmedRequiredValidator(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value.length > 0 ? null : { required: true };
}

function requiredOdometerValidator(control: AbstractControl): ValidationErrors | null {
  const raw = String(control.value ?? '').trim();
  if (raw === '') {
    return { required: true };
  }
  if (!/^\d+$/.test(raw)) {
    return { odometer: true };
  }
  const km = Number.parseInt(raw, 10);
  return Number.isSafeInteger(km) && km >= 0 ? null : { odometer: true };
}

function optionalPositiveIntegerValidator(control: AbstractControl): ValidationErrors | null {
  const raw = String(control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!/^\d+$/.test(raw)) {
    return { positiveInteger: true };
  }
  const val = Number.parseInt(raw, 10);
  return Number.isSafeInteger(val) && val > 0 ? null : { positiveInteger: true };
}

function nonNegativeCostValidator(control: AbstractControl): ValidationErrors | null {
  const raw = String(control.value ?? '').trim();
  if (raw === '') {
    return null;
  }
  if (!NON_NEGATIVE_DECIMAL_PATTERN.test(raw)) {
    return { cost: true };
  }
  const num = Number(raw.replace(',', '.'));
  return Number.isFinite(num) && num >= 0 ? null : { cost: true };
}

function parseCostValue(raw: string): number {
  const trimmed = raw.trim();
  if (trimmed === '' || !NON_NEGATIVE_DECIMAL_PATTERN.test(trimmed)) {
    return 0;
  }
  const num = Number(trimmed.replace(',', '.'));
  return Number.isFinite(num) && num > 0 ? num : 0;
}

@Component({
  selector: 'fo-maintenance-log-form',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-5" novalidate>
      @if (errorMessage(); as error) {
        <div
          role="alert"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger"
        >
          {{ error }}
        </div>
      }

      <fieldset class="space-y-2">
        <legend class="text-sm font-semibold text-text">Tipo de mantenimiento</legend>
        <div class="flex flex-wrap gap-2" role="group" aria-label="Tipo de mantenimiento">
          @for (type of maintenanceOptions; track type) {
            @let selected = selectedType() === type;
            <button
              type="button"
              [attr.data-testid]="'maintenance-type-' + type"
              [attr.aria-pressed]="selected"
              (click)="selectType(type)"
              class="fo-focus-ring inline-flex min-h-[38px] cursor-pointer items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-medium transition-colors"
              [class.border-accent]="selected"
              [class.bg-accent-soft]="selected"
              [class.text-accent]="selected"
              [class.border-border]="!selected"
              [class.bg-surface]="!selected"
              [class.text-text]="!selected"
              [class.hover:bg-surface-2]="!selected"
            >
              <fo-icon [name]="maintenanceIcon[type]" [size]="14" />
              <span>{{ maintenanceLabel[type] }}</span>
            </button>
          }
        </div>
      </fieldset>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div class="space-y-1.5">
          <label for="fo-maint-date" class="block text-sm font-medium text-text">Fecha</label>
          <input
            id="fo-maint-date"
            type="date"
            formControlName="maintenanceDate"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums"
          />
        </div>

        <div class="space-y-1.5">
          <div class="flex items-baseline justify-between gap-2">
            <label for="fo-maint-odometer" class="block text-sm font-medium text-text">
              Odómetro (km) <span class="text-danger">*</span>
            </label>
            @if (vehicle(); as v) {
              <span class="text-xs text-muted tabular-nums">
                Actual: {{ formatOdometer(v.currentOdometerKm) }}
              </span>
            }
          </div>
          <input
            id="fo-maint-odometer"
            data-testid="maintenance-odometer-input"
            type="text"
            inputmode="numeric"
            pattern="[0-9]*"
            formControlName="odometerKm"
            placeholder="Ej. 45200"
            [attr.aria-invalid]="hasError('odometerKm')"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
          />
          @if (fieldError('odometerKm'); as err) {
            <p role="alert" data-testid="maintenance-odometer-error" class="text-xs text-danger">
              {{ err }}
            </p>
          } @else if (isInvalid('odometerKm')) {
            <p role="alert" class="text-xs text-danger">
              Ingresá el kilometraje actual (entero mayor o igual a 0).
            </p>
          }
        </div>

        <div class="space-y-1.5 sm:col-span-2">
          <label for="fo-maint-description" class="block text-sm font-medium text-text">
            Descripción <span class="text-danger">*</span>
          </label>
          <input
            id="fo-maint-description"
            data-testid="maintenance-description-input"
            type="text"
            formControlName="description"
            maxlength="160"
            placeholder="Ej. Cambio de aceite 5W30 y filtro de aire"
            [attr.aria-invalid]="hasError('description')"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
          />
          @if (fieldError('description'); as err) {
            <p role="alert" class="text-xs text-danger">{{ err }}</p>
          } @else if (isInvalid('description')) {
            <p role="alert" class="text-xs text-danger">Ingresá una descripción del trabajo.</p>
          }
        </div>

        <div class="space-y-1.5 sm:col-span-2">
          <label for="fo-maint-workshop" class="block text-sm font-medium text-text">
            Taller o lugar (opcional)
          </label>
          <input
            id="fo-maint-workshop"
            type="text"
            formControlName="workshopName"
            maxlength="80"
            placeholder="Ej. Lubricentro Sur, Concesionario oficial"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
          />
        </div>
      </div>

      <!-- Cost & conditional account -->
      <div class="space-y-3 rounded-xl border border-border bg-surface-2/50 p-4">
        <p class="text-xs font-semibold tracking-wide text-muted uppercase">Costo y pago</p>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <label for="fo-maint-cost" class="block text-sm font-medium text-text">
              Costo (0 si fue sin cargo)
            </label>
            <input
              id="fo-maint-cost"
              data-testid="maintenance-cost-input"
              type="text"
              inputmode="decimal"
              formControlName="costAmount"
              placeholder="0"
              [attr.aria-invalid]="hasError('costAmount')"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
            />
            @if (fieldError('costAmount'); as err) {
              <p role="alert" class="text-xs text-danger">{{ err }}</p>
            } @else if (isInvalid('costAmount')) {
              <p role="alert" class="text-xs text-danger">Ingresá un importe válido (mayor o igual a 0).</p>
            }
          </div>

          <div class="space-y-1.5">
            <label for="fo-maint-currency" class="block text-sm font-medium text-text">
              Divisa
            </label>
            <select
              id="fo-maint-currency"
              formControlName="currencyCode"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
            >
              @for (currency of currencyOptions(); track currency) {
                <option [value]="currency">{{ currency }}</option>
              }
            </select>
          </div>
        </div>

        @if (requiresAccount()) {
          <div class="space-y-1.5" data-testid="maintenance-account-group">
            <label for="fo-maint-account" class="block text-sm font-medium text-text">
              Cuenta de pago <span class="text-danger">*</span>
            </label>
            <select
              id="fo-maint-account"
              data-testid="maintenance-account-select"
              formControlName="accountId"
              [attr.aria-invalid]="hasError('accountId')"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
            >
              <option value="">Seleccioná una cuenta</option>
              @for (acc of accounts(); track acc.id) {
                <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
              }
            </select>
            @if (fieldError('accountId'); as err) {
              <p role="alert" class="text-xs text-danger">{{ err }}</p>
            } @else if (isInvalid('accountId')) {
              <p role="alert" data-testid="maintenance-account-error" class="text-xs text-danger">
                Seleccioná la cuenta para registrar el gasto.
              </p>
            }
          </div>
        } @else {
          <p class="text-xs text-muted">
            Sin costo: no se generará un movimiento en tus cuentas.
          </p>
        }
      </div>

      <!-- Next due reminder + suggestions -->
      <div class="space-y-3 rounded-xl border border-border bg-surface-2/50 p-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <p class="text-xs font-semibold tracking-wide text-muted uppercase">
            Próximo vencimiento (opcional)
          </p>

          @if (dueSuggestion(); as suggestion) {
            <button
              type="button"
              data-testid="maintenance-apply-suggestion"
              (click)="applySuggestion()"
              class="fo-focus-ring inline-flex cursor-pointer items-center gap-1 rounded-full bg-accent-soft px-3 py-1 text-xs font-semibold text-accent transition-opacity hover:opacity-90"
            >
              <fo-icon name="sparkles" [size]="13" />
              <span>Sugerencia: {{ suggestion.label }}</span>
            </button>
          }
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <label for="fo-maint-next-km" class="block text-sm font-medium text-text">
              Próximo por km
            </label>
            <input
              id="fo-maint-next-km"
              data-testid="maintenance-next-km-input"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              formControlName="nextDueKm"
              placeholder="Ej. 55200"
              [attr.aria-invalid]="hasError('nextDueKm')"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums placeholder:text-muted"
            />
            @if (isInvalid('nextDueKm')) {
              <p role="alert" class="text-xs text-danger">Ingresá un kilometraje válido mayor a 0.</p>
            }
          </div>

          <div class="space-y-1.5">
            <label for="fo-maint-next-date" class="block text-sm font-medium text-text">
              Próximo por fecha
            </label>
            <input
              id="fo-maint-next-date"
              data-testid="maintenance-next-date-input"
              type="date"
              formControlName="nextDueDate"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums"
            />
          </div>
        </div>
      </div>

      <div class="space-y-1.5">
        <label for="fo-maint-notes" class="block text-sm font-medium text-text">
          Notas (opcional)
        </label>
        <textarea
          id="fo-maint-notes"
          rows="2"
          formControlName="notes"
          maxlength="500"
          placeholder="Detalle de repuestos, garantía o recomendaciones del mecánico..."
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
        ></textarea>
      </div>

      <div class="flex flex-wrap items-center justify-end gap-2 pt-2">
        <fo-button variant="ghost" type="button" [disabled]="saving()" (click)="cancelled.emit()">
          Cancelar
        </fo-button>
        <fo-button
          variant="primary"
          type="submit"
          [loading]="saving()"
          data-testid="maintenance-submit-btn"
        >
          Registrar mantenimiento
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MaintenanceLogFormComponent {
  private readonly fb = inject(FormBuilder);

  readonly vehicle = input<Vehicle | null>(null);
  readonly accounts = input<readonly Account[]>([]);
  readonly defaultAccountId = input<string | null>(null);
  readonly enabledCurrencies = input<readonly string[]>(['ARS', 'USD']);
  readonly defaultCurrency = input<string>('ARS');
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);
  readonly fieldErrors = input<Record<string, string>>({});

  readonly submitted = output<MaintenanceLogCreate>();
  readonly cancelled = output<void>();

  protected readonly maintenanceOptions = MAINTENANCE_TYPE_OPTIONS;
  protected readonly maintenanceLabel = MAINTENANCE_TYPE_LABEL;
  protected readonly maintenanceIcon = MAINTENANCE_TYPE_ICON;

  protected readonly selectedType = signal<MaintenanceType>('OIL_CHANGE');
  protected readonly requiresAccount = signal<boolean>(false);

  private initializedForVehicleId: string | null = null;

  protected readonly dueSuggestion = computed(
    () => MAINTENANCE_DUE_SUGGESTIONS[this.selectedType()] ?? null,
  );

  protected readonly currencyOptions = computed(() => {
    const base = this.enabledCurrencies()
      .map((c) => c.trim().toUpperCase())
      .filter(Boolean);
    const list = base.length > 0 ? [...base] : ['ARS', 'USD'];
    const def = this.defaultCurrency().trim().toUpperCase();
    if (def && !list.includes(def)) {
      list.unshift(def);
    }
    return list;
  });

  readonly form = this.fb.nonNullable.group({
    maintenanceType: this.fb.nonNullable.control<MaintenanceType>('OIL_CHANGE', [
      Validators.required,
    ]),
    maintenanceDate: [getTodayIsoDate()],
    odometerKm: ['', [requiredOdometerValidator]],
    description: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(160)]],
    workshopName: ['', [Validators.maxLength(80)]],
    costAmount: ['0', [nonNegativeCostValidator]],
    currencyCode: ['ARS', [Validators.required]],
    accountId: [''],
    nextDueKm: ['', [optionalPositiveIntegerValidator]],
    nextDueDate: [''],
    notes: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    this.form.controls.costAmount.valueChanges.subscribe((value) => {
      this.syncAccountRequirement(value);
    });

    this.form.controls.accountId.valueChanges.subscribe((accountId) => {
      const account = this.accounts().find((item) => item.id === accountId);
      if (account && account.currencyCode !== this.form.controls.currencyCode.value) {
        this.form.controls.currencyCode.setValue(account.currencyCode, { emitEvent: false });
      }
    });

    effect(() => {
      const defCurrency = this.defaultCurrency().trim().toUpperCase();
      if (defCurrency && this.form.controls.currencyCode.pristine) {
        this.form.controls.currencyCode.setValue(defCurrency, { emitEvent: false });
      }
    });

    effect(() => {
      const currentVehicle = this.vehicle();
      if (currentVehicle && this.initializedForVehicleId !== currentVehicle.id) {
        this.initializedForVehicleId = currentVehicle.id;
        this.form.controls.odometerKm.setValue(String(currentVehicle.currentOdometerKm), {
          emitEvent: false,
        });
      }
    });
  }

  protected formatOdometer(km: number): string {
    return formatKm(km);
  }

  protected selectType(type: MaintenanceType): void {
    this.selectedType.set(type);
    this.form.controls.maintenanceType.setValue(type);
  }

  protected applySuggestion(): void {
    const suggestion = this.dueSuggestion();
    if (!suggestion) {
      return;
    }

    if (suggestion.addKm !== null) {
      const baseOdometer = Number.parseInt(this.form.controls.odometerKm.value.trim(), 10);
      const kmStart = Number.isFinite(baseOdometer)
        ? baseOdometer
        : (this.vehicle()?.currentOdometerKm ?? 0);
      this.form.controls.nextDueKm.setValue(String(kmStart + suggestion.addKm));
    }

    if (suggestion.addMonths !== null) {
      const baseDate = this.form.controls.maintenanceDate.value.trim() || getTodayIsoDate();
      this.form.controls.nextDueDate.setValue(addMonthsToIsoDate(baseDate, suggestion.addMonths));
    }
  }

  protected fieldError(field: string): string | null {
    return this.fieldErrors()[field] ?? null;
  }

  protected isInvalid(controlName: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && (control.touched || control.dirty);
  }

  protected hasError(controlName: keyof typeof this.form.controls): boolean {
    return this.isInvalid(controlName) || this.fieldError(controlName) !== null;
  }

  protected onSubmit(): void {
    this.syncAccountRequirement(this.form.controls.costAmount.value);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    const raw = this.form.getRawValue();
    const numericCost = parseCostValue(raw.costAmount);
    const normalizedCost =
      raw.costAmount.trim() === '' ? '0' : raw.costAmount.trim().replace(',', '.');

    const maintenanceDate = raw.maintenanceDate.trim();
    const workshopName = raw.workshopName.trim();
    const nextDueKmRaw = raw.nextDueKm.trim();
    const nextDueDate = raw.nextDueDate.trim();
    const notes = raw.notes.trim();

    const payload: MaintenanceLogCreate = {
      maintenanceType: raw.maintenanceType,
      odometerKm: Number.parseInt(raw.odometerKm.trim(), 10),
      description: raw.description.trim(),
      costAmount: normalizedCost,
      currencyCode: raw.currencyCode.trim().toUpperCase(),
      ...(maintenanceDate ? { maintenanceDate } : {}),
      ...(workshopName ? { workshopName } : {}),
      ...(numericCost > 0 && raw.accountId ? { accountId: raw.accountId } : {}),
      ...(nextDueKmRaw ? { nextDueKm: Number.parseInt(nextDueKmRaw, 10) } : {}),
      ...(nextDueDate ? { nextDueDate } : {}),
      ...(notes ? { notes } : {}),
    };

    this.submitted.emit(payload);
  }

  private syncAccountRequirement(costRaw: string): void {
    const needsAccount = parseCostValue(costRaw) > 0;
    this.requiresAccount.set(needsAccount);

    const accountControl = this.form.controls.accountId;
    if (needsAccount) {
      accountControl.setValidators([Validators.required]);
      if (!accountControl.value) {
        const preferred = this.defaultAccountId();
        if (preferred) {
          accountControl.setValue(preferred, { emitEvent: false });
        }
      }
    } else {
      accountControl.clearValidators();
    }
    accountControl.updateValueAndValidity({ emitEvent: false });
  }
}
