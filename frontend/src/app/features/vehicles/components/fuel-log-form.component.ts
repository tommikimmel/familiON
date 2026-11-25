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
  FUEL_TYPE_LABEL,
  FUEL_TYPE_OPTIONS,
  defaultFuelTypeForVehicle,
  formatKm,
  getTodayIsoDate,
} from '../data-access/vehicle-labels';
import { FuelLogCreate, FuelType, Vehicle } from '../data-access/vehicles.models';
import {
  FuelCalcField,
  FuelCalcState,
  buildFuelPayloadAmounts,
  createInitialFuelCalcState,
  hasCompleteFuelAmounts,
  updateFuelField,
} from '../utils/fuel-math';

function odometerValidator(control: AbstractControl): ValidationErrors | null {
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

@Component({
  selector: 'fo-fuel-log-form',
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

      <!-- Odometer: top priority for one-handed entry at the pump -->
      <div class="space-y-1.5">
        <div class="flex items-baseline justify-between gap-2">
          <label for="fo-fuel-odometer" class="block text-sm font-semibold text-text">
            Odómetro (km) <span class="text-danger">*</span>
          </label>
          @if (vehicle(); as v) {
            <span class="text-xs text-muted tabular-nums">
              Actual: {{ formatOdometer(v.currentOdometerKm) }}
            </span>
          }
        </div>
        <input
          id="fo-fuel-odometer"
          data-testid="fuel-odometer-input"
          type="text"
          inputmode="numeric"
          pattern="[0-9]*"
          autocomplete="off"
          formControlName="odometerKm"
          placeholder="Ej. 45620"
          [attr.aria-invalid]="hasError('odometerKm')"
          class="fo-focus-ring min-h-[48px] w-full rounded-xl border border-border bg-surface px-4 py-3 text-lg font-semibold text-text tabular-nums placeholder:font-normal placeholder:text-muted"
        />
        @if (fieldError('odometerKm'); as err) {
          <p role="alert" data-testid="fuel-odometer-error" class="text-xs text-danger">
            {{ err }}
          </p>
        } @else if (isInvalid('odometerKm')) {
          <p role="alert" class="text-xs text-danger">
            Ingresá el kilometraje actual (número entero mayor o igual a 0).
          </p>
        }
      </div>

      <!-- Fuel type chips -->
      <fieldset class="space-y-2">
        <legend class="text-sm font-semibold text-text">Combustible</legend>
        <div class="flex flex-wrap gap-2" role="group" aria-label="Tipo de combustible">
          @for (fuel of fuelOptions; track fuel) {
            @let selected = form.controls.fuelType.value === fuel;
            <button
              type="button"
              [attr.data-testid]="'fuel-type-' + fuel"
              [attr.aria-pressed]="selected"
              (click)="selectFuelType(fuel)"
              class="fo-focus-ring min-h-[40px] cursor-pointer rounded-xl border px-3 py-2 text-xs font-semibold transition-colors"
              [class.border-accent]="selected"
              [class.bg-accent]="selected"
              [class.text-accent-contrast]="selected"
              [class.border-border]="!selected"
              [class.bg-surface]="!selected"
              [class.text-text]="!selected"
              [class.hover:bg-surface-2]="!selected"
            >
              {{ fuelLabel[fuel] }}
            </button>
          }
        </div>
      </fieldset>

      <!-- 2-of-3 live calculator -->
      <div class="space-y-3 rounded-2xl border border-border bg-surface-2/50 p-3.5">
        <div class="flex items-center justify-between gap-2">
          <p class="text-xs font-semibold tracking-wide text-text uppercase">
            Importe y litros (2 de 3)
          </p>
          <span class="rounded-full bg-surface px-2 py-0.5 text-[11px] font-medium text-muted">
            {{ selectedCurrency() }}
          </span>
        </div>
        <p class="text-xs text-muted">
          Completá dos valores y calculamos el tercero automáticamente.
        </p>

        <div class="space-y-3">
          <div class="space-y-1">
            <div class="flex items-center justify-between gap-2">
              <label for="fo-fuel-liters" class="text-sm font-medium text-text">Litros (L)</label>
              @if (calcState().computedField === 'liters') {
                <span
                  data-testid="fuel-computed-badge-liters"
                  class="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent"
                >
                  Calculado
                </span>
              }
            </div>
            <input
              id="fo-fuel-liters"
              data-testid="fuel-liters-input"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              [value]="calcState().liters"
              (input)="onCalcInput('liters', $event)"
              placeholder="Ej. 40,5"
              [attr.aria-invalid]="amountsTouched() && !amountsComplete()"
              class="fo-focus-ring min-h-[48px] w-full rounded-xl border px-3.5 py-2.5 text-lg font-semibold text-text tabular-nums placeholder:font-normal placeholder:text-muted"
              [class.border-accent/60]="calcState().computedField === 'liters'"
              [class.bg-accent-soft/40]="calcState().computedField === 'liters'"
              [class.border-border]="calcState().computedField !== 'liters'"
              [class.bg-surface]="calcState().computedField !== 'liters'"
            />
          </div>

          <div class="grid grid-cols-1 gap-3 min-[340px]:grid-cols-2">
            <div class="min-w-0 space-y-1">
              <div class="flex items-center justify-between gap-1">
                <label for="fo-fuel-price" class="truncate text-sm font-medium text-text">
                  Precio / L
                </label>
                @if (calcState().computedField === 'pricePerLiter') {
                  <span
                    data-testid="fuel-computed-badge-pricePerLiter"
                    class="shrink-0 rounded-full bg-accent-soft px-1.5 py-0.5 text-[10px] font-medium text-accent"
                  >
                    Calculado
                  </span>
                }
              </div>
              <input
                id="fo-fuel-price"
                data-testid="fuel-price-input"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                [value]="calcState().pricePerLiter"
                (input)="onCalcInput('pricePerLiter', $event)"
                placeholder="Ej. 1250"
                [attr.aria-invalid]="amountsTouched() && !amountsComplete()"
                class="fo-focus-ring min-h-[48px] w-full rounded-xl border px-3 py-2.5 text-lg font-semibold text-text tabular-nums placeholder:font-normal placeholder:text-muted"
                [class.border-accent/60]="calcState().computedField === 'pricePerLiter'"
                [class.bg-accent-soft/40]="calcState().computedField === 'pricePerLiter'"
                [class.border-border]="calcState().computedField !== 'pricePerLiter'"
                [class.bg-surface]="calcState().computedField !== 'pricePerLiter'"
              />
            </div>

            <div class="min-w-0 space-y-1">
              <div class="flex items-center justify-between gap-1">
                <label for="fo-fuel-total" class="truncate text-sm font-medium text-text">
                  Total
                </label>
                @if (calcState().computedField === 'totalAmount') {
                  <span
                    data-testid="fuel-computed-badge-totalAmount"
                    class="shrink-0 rounded-full bg-accent-soft px-1.5 py-0.5 text-[10px] font-medium text-accent"
                  >
                    Calculado
                  </span>
                }
              </div>
              <input
                id="fo-fuel-total"
                data-testid="fuel-total-input"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                [value]="calcState().totalAmount"
                (input)="onCalcInput('totalAmount', $event)"
                placeholder="Ej. 50000"
                [attr.aria-invalid]="amountsTouched() && !amountsComplete()"
                class="fo-focus-ring min-h-[48px] w-full rounded-xl border px-3 py-2.5 text-lg font-semibold text-text tabular-nums placeholder:font-normal placeholder:text-muted"
                [class.border-accent/60]="calcState().computedField === 'totalAmount'"
                [class.bg-accent-soft/40]="calcState().computedField === 'totalAmount'"
                [class.border-border]="calcState().computedField !== 'totalAmount'"
                [class.bg-surface]="calcState().computedField !== 'totalAmount'"
              />
            </div>
          </div>
        </div>

        @if (amountsTouched() && !amountsComplete()) {
          <p role="alert" data-testid="fuel-amounts-error" class="text-xs text-danger">
            Ingresá al menos 2 valores mayores a cero (litros, precio por litro o total).
          </p>
        }
      </div>

      <!-- Full tank switch -->
      <div
        class="flex min-h-[54px] items-center justify-between gap-3 rounded-xl border border-border bg-surface px-4 py-2.5"
      >
        <div class="min-w-0">
          <span id="fo-fuel-full-tank-label" class="block text-sm font-semibold text-text">
            Tanque lleno
          </span>
          <span class="block text-xs text-muted">
            Permite calcular rendimiento exacto en km/L
          </span>
        </div>

        <button
          type="button"
          role="switch"
          data-testid="fuel-full-tank-switch"
          aria-labelledby="fo-fuel-full-tank-label"
          [attr.aria-checked]="fullTank()"
          (click)="toggleFullTank()"
          class="fo-focus-ring relative inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full transition-colors duration-150"
          [class.bg-accent]="fullTank()"
          [class.bg-border]="!fullTank()"
        >
          <span
            class="inline-block h-5 w-5 rounded-full bg-white shadow-xs transition-transform duration-150"
            [class.translate-x-6]="fullTank()"
            [class.translate-x-1]="!fullTank()"
          ></span>
        </button>
      </div>

      <!-- Account selector -->
      <div class="space-y-1.5">
        <label for="fo-fuel-account" class="block text-sm font-semibold text-text">
          Cuenta de pago <span class="text-danger">*</span>
        </label>
        <select
          id="fo-fuel-account"
          data-testid="fuel-account-select"
          formControlName="accountId"
          [attr.aria-invalid]="hasError('accountId')"
          class="fo-focus-ring min-h-[48px] w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
        >
          @if (accounts().length === 0) {
            <option value="">Sin cuentas disponibles</option>
          } @else {
            <option value="">Seleccioná una cuenta</option>
            @for (acc of accounts(); track acc.id) {
              <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
            }
          }
        </select>
        @if (fieldError('accountId'); as err) {
          <p role="alert" class="text-xs text-danger">{{ err }}</p>
        } @else if (isInvalid('accountId')) {
          <p role="alert" class="text-xs text-danger">Seleccioná la cuenta con la que pagaste.</p>
        }
      </div>

      <!-- Optional details -->
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div class="space-y-1.5">
          <label for="fo-fuel-station" class="block text-sm font-medium text-text">
            Estación (opcional)
          </label>
          <input
            id="fo-fuel-station"
            type="text"
            formControlName="stationName"
            maxlength="80"
            placeholder="Ej. YPF Ruta 9, Shell"
            class="fo-focus-ring min-h-[44px] w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
          />
        </div>

        <div class="space-y-1.5">
          <label for="fo-fuel-date" class="block text-sm font-medium text-text">Fecha</label>
          <input
            id="fo-fuel-date"
            type="date"
            formControlName="fuelDate"
            class="fo-focus-ring min-h-[44px] w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text tabular-nums"
          />
        </div>
      </div>

      <div class="space-y-1.5">
        <label for="fo-fuel-notes" class="block text-sm font-medium text-text">
          Notas (opcional)
        </label>
        <input
          id="fo-fuel-notes"
          type="text"
          formControlName="notes"
          maxlength="240"
          placeholder="Ej. Descuento app, viaje a Córdoba"
          class="fo-focus-ring min-h-[44px] w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
        />
      </div>

      <div class="space-y-2 pt-2">
        <fo-button
          variant="primary"
          size="lg"
          type="submit"
          [fullWidth]="true"
          [loading]="saving()"
          data-testid="fuel-submit-btn"
        >
          <fo-icon name="check" [size]="18" />
          <span>Guardar carga</span>
        </fo-button>

        <fo-button
          variant="ghost"
          size="md"
          type="button"
          [fullWidth]="true"
          [disabled]="saving()"
          (click)="cancelled.emit()"
        >
          Cancelar
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuelLogFormComponent {
  private readonly fb = inject(FormBuilder);

  readonly vehicle = input<Vehicle | null>(null);
  readonly accounts = input<readonly Account[]>([]);
  readonly defaultAccountId = input<string | null>(null);
  readonly defaultCurrency = input<string>('ARS');
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);
  readonly fieldErrors = input<Record<string, string>>({});

  readonly submitted = output<FuelLogCreate>();
  readonly cancelled = output<void>();

  protected readonly fuelOptions = FUEL_TYPE_OPTIONS;
  protected readonly fuelLabel = FUEL_TYPE_LABEL;

  protected readonly calcState = signal<FuelCalcState>(createInitialFuelCalcState());
  protected readonly amountsTouched = signal<boolean>(false);
  protected readonly fullTank = signal<boolean>(true);
  private readonly selectedAccountId = signal<string>('');
  private initializedForVehicleId: string | null = null;

  protected readonly amountsComplete = computed(() => hasCompleteFuelAmounts(this.calcState()));

  protected readonly selectedCurrency = computed(() => {
    const accId = this.selectedAccountId();
    const acc = this.accounts().find((item) => item.id === accId);
    return acc?.currencyCode ?? this.defaultCurrency();
  });

  readonly form = this.fb.nonNullable.group({
    odometerKm: ['', [odometerValidator]],
    fuelType: this.fb.nonNullable.control<FuelType>('NAFTA_SUPER', [Validators.required]),
    accountId: ['', [Validators.required]],
    stationName: ['', [Validators.maxLength(80)]],
    fuelDate: [getTodayIsoDate()],
    notes: ['', [Validators.maxLength(240)]],
  });

  constructor() {
    this.form.controls.accountId.valueChanges.subscribe((value) => {
      this.selectedAccountId.set(value);
    });

    effect(() => {
      const currentVehicle = this.vehicle();
      if (currentVehicle && this.initializedForVehicleId !== currentVehicle.id) {
        this.initializedForVehicleId = currentVehicle.id;
        this.form.controls.odometerKm.setValue(String(currentVehicle.currentOdometerKm), {
          emitEvent: false,
        });
        this.form.controls.fuelType.setValue(
          defaultFuelTypeForVehicle(
            currentVehicle.primaryFuelType,
            currentVehicle.secondaryFuelType,
          ),
          { emitEvent: false },
        );
      }
    });

    effect(() => {
      const defaultId = this.defaultAccountId();
      const accountList = this.accounts();
      const current = this.form.controls.accountId.value;
      if (!current) {
        const chosen = defaultId ?? accountList[0]?.id ?? '';
        if (chosen) {
          this.form.controls.accountId.setValue(chosen);
          this.selectedAccountId.set(chosen);
        }
      }
    });
  }

  protected formatOdometer(km: number): string {
    return formatKm(km);
  }

  protected selectFuelType(fuel: FuelType): void {
    this.form.controls.fuelType.setValue(fuel);
  }

  protected toggleFullTank(): void {
    this.fullTank.update((value) => !value);
  }

  protected onCalcInput(field: FuelCalcField, event: Event): void {
    const rawValue = (event.target as HTMLInputElement).value;
    this.calcState.update((state) => updateFuelField(state, field, rawValue));
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
    this.form.markAllAsTouched();
    this.amountsTouched.set(true);

    if (this.form.invalid || !this.amountsComplete()) {
      return;
    }

    const raw = this.form.getRawValue();
    const amounts = buildFuelPayloadAmounts(this.calcState());
    const stationName = raw.stationName.trim();
    const notes = raw.notes.trim();
    const fuelDate = raw.fuelDate.trim();

    const payload: FuelLogCreate = {
      odometerKm: Number.parseInt(raw.odometerKm.trim(), 10),
      fuelType: raw.fuelType,
      ...amounts,
      currencyCode: this.selectedCurrency(),
      fullTank: this.fullTank(),
      accountId: raw.accountId,
      ...(fuelDate ? { fuelDate } : {}),
      ...(stationName ? { stationName } : {}),
      ...(notes ? { notes } : {}),
    };

    this.submitted.emit(payload);
  }
}
