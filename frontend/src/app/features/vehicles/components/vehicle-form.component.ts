import {
  ChangeDetectionStrategy,
  Component,
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
  Validators,
} from '@angular/forms';
import { ButtonComponent } from '../../../shared/ui';
import {
  FUEL_TYPE_LABEL,
  FUEL_TYPE_OPTIONS,
  PRIMARY_FUEL_TYPE_OPTIONS,
} from '../data-access/vehicle-labels';
import {
  FuelType,
  PrimaryFuelType,
  Vehicle,
  VehicleCreate,
} from '../data-access/vehicles.models';

const MIN_MODEL_YEAR = 1900;
const MAX_MODEL_YEAR = 2100;

function trimmedRequiredValidator(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value.length > 0 ? null : { required: true };
}

function modelYearValidator(control: AbstractControl): ValidationErrors | null {
  const raw = String(control.value ?? '').trim();
  if (raw === '') {
    return { required: true };
  }
  if (!/^\d{4}$/.test(raw)) {
    return { modelYear: true };
  }
  const year = Number.parseInt(raw, 10);
  return year >= MIN_MODEL_YEAR && year <= MAX_MODEL_YEAR ? null : { modelYear: true };
}

function nonNegativeIntegerValidator(control: AbstractControl): ValidationErrors | null {
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
  selector: 'fo-vehicle-form',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent],
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

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div class="space-y-1.5 sm:col-span-2">
          <label for="fo-vehicle-nickname" class="block text-[11.5px] font-semibold text-text-2">
            Apodo <span class="text-danger">*</span>
          </label>
          <input
            id="fo-vehicle-nickname"
            type="text"
            formControlName="nickname"
            maxlength="80"
            placeholder="Ej. Cronos gris, Auto familiar"
            [attr.aria-invalid]="hasError('nickname')"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
          />
          @if (fieldError('nickname'); as err) {
            <p role="alert" class="text-xs text-danger">{{ err }}</p>
          } @else if (isInvalid('nickname')) {
            <p role="alert" class="text-xs text-danger">Ingresá un apodo para identificar el vehículo.</p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="fo-vehicle-brand" class="block text-[11.5px] font-semibold text-text-2">
            Marca <span class="text-danger">*</span>
          </label>
          <input
            id="fo-vehicle-brand"
            type="text"
            formControlName="brand"
            maxlength="60"
            placeholder="Ej. Fiat, Toyota"
            [attr.aria-invalid]="hasError('brand')"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
          />
          @if (fieldError('brand'); as err) {
            <p role="alert" class="text-xs text-danger">{{ err }}</p>
          } @else if (isInvalid('brand')) {
            <p role="alert" class="text-xs text-danger">Ingresá la marca del vehículo.</p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="fo-vehicle-model" class="block text-[11.5px] font-semibold text-text-2">
            Modelo <span class="text-danger">*</span>
          </label>
          <input
            id="fo-vehicle-model"
            type="text"
            formControlName="model"
            maxlength="60"
            placeholder="Ej. Cronos, Corolla"
            [attr.aria-invalid]="hasError('model')"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
          />
          @if (fieldError('model'); as err) {
            <p role="alert" class="text-xs text-danger">{{ err }}</p>
          } @else if (isInvalid('model')) {
            <p role="alert" class="text-xs text-danger">Ingresá el modelo del vehículo.</p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="fo-vehicle-year" class="block text-[11.5px] font-semibold text-text-2">
            Año <span class="text-danger">*</span>
          </label>
          <input
            id="fo-vehicle-year"
            type="number"
            inputmode="numeric"
            min="1900"
            max="2100"
            step="1"
            formControlName="modelYear"
            placeholder="Ej. 2022"
            [attr.aria-invalid]="hasError('modelYear')"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums placeholder:text-muted"
          />
          @if (fieldError('modelYear'); as err) {
            <p role="alert" class="text-xs text-danger">{{ err }}</p>
          } @else if (isInvalid('modelYear')) {
            <p role="alert" class="text-xs text-danger">Ingresá un año válido entre 1900 y 2100.</p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="fo-vehicle-plate" class="block text-[11.5px] font-semibold text-text-2">
            Patente <span class="text-danger">*</span>
          </label>
          <input
            id="fo-vehicle-plate"
            type="text"
            formControlName="licensePlate"
            maxlength="20"
            placeholder="Ej. AF 123 CD"
            [attr.aria-invalid]="hasError('licensePlate')"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] font-mono text-[13px] focus:border-accent uppercase text-text placeholder:font-sans placeholder:normal-case placeholder:text-muted"
          />
          @if (fieldError('licensePlate'); as err) {
            <p role="alert" class="text-xs text-danger">{{ err }}</p>
          } @else if (isInvalid('licensePlate')) {
            <p role="alert" class="text-xs text-danger">La patente es obligatoria.</p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="fo-vehicle-primary-fuel" class="block text-[11.5px] font-semibold text-text-2">
            Combustible principal <span class="text-danger">*</span>
          </label>
          <select
            id="fo-vehicle-primary-fuel"
            formControlName="primaryFuelType"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
          >
            @for (fuel of primaryFuelOptions; track fuel) {
              <option [value]="fuel">{{ fuelLabel[fuel] }}</option>
            }
          </select>
        </div>

        <div class="space-y-1.5">
          <label for="fo-vehicle-secondary-fuel" class="block text-[11.5px] font-semibold text-text-2">
            Combustible secundario (opcional)
          </label>
          <select
            id="fo-vehicle-secondary-fuel"
            formControlName="secondaryFuelType"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
          >
            <option value="">Ninguno</option>
            @for (fuel of fuelOptions; track fuel) {
              <option [value]="fuel">{{ fuelLabel[fuel] }}</option>
            }
          </select>
        </div>

        <div class="space-y-1.5 sm:col-span-2">
          <label for="fo-vehicle-odometer" class="block text-[11.5px] font-semibold text-text-2">
            Odómetro actual (km) <span class="text-danger">*</span>
          </label>
          <input
            id="fo-vehicle-odometer"
            type="text"
            inputmode="numeric"
            formControlName="currentOdometerKm"
            placeholder="Ej. 45200"
            [attr.aria-invalid]="hasError('currentOdometerKm')"
            class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums placeholder:text-muted"
          />
          @if (fieldError('currentOdometerKm'); as err) {
            <p role="alert" data-testid="vehicle-odometer-error" class="text-xs text-danger">
              {{ err }}
            </p>
          } @else if (isInvalid('currentOdometerKm')) {
            <p role="alert" class="text-xs text-danger">
              Ingresá un kilometraje válido (entero mayor o igual a 0).
            </p>
          }
        </div>
      </div>

      <div class="space-y-3 rounded-xl border border-border bg-surface-2/50 p-4">
        <p class="text-xs font-semibold tracking-wide text-muted uppercase">
          Vencimientos y seguro (opcional)
        </p>

        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div class="space-y-1.5">
            <label for="fo-vehicle-vtv" class="block text-[11.5px] font-semibold text-text-2">
              Vencimiento VTV
            </label>
            <input
              id="fo-vehicle-vtv"
              type="date"
              formControlName="vtvExpirationDate"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums"
            />
          </div>

          <div class="space-y-1.5">
            <label for="fo-vehicle-insurance-exp" class="block text-[11.5px] font-semibold text-text-2">
              Vencimiento del seguro
            </label>
            <input
              id="fo-vehicle-insurance-exp"
              type="date"
              formControlName="insuranceExpiration"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums"
            />
          </div>

          <div class="space-y-1.5 sm:col-span-2">
            <label for="fo-vehicle-insurance-company" class="block text-[11.5px] font-semibold text-text-2">
              Compañía de seguro
            </label>
            <input
              id="fo-vehicle-insurance-company"
              type="text"
              formControlName="insuranceCompany"
              maxlength="80"
              placeholder="Ej. La Caja, Federación Patronal"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
            />
          </div>
        </div>
      </div>

      <div class="space-y-1.5">
        <label for="fo-vehicle-notes" class="block text-[11.5px] font-semibold text-text-2">
          Notas (opcional)
        </label>
        <textarea
          id="fo-vehicle-notes"
          rows="3"
          formControlName="notes"
          maxlength="500"
          placeholder="Número de póliza, presión de cubiertas, tipo de aceite..."
          class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
        ></textarea>
      </div>

      <div class="flex flex-wrap items-center justify-end gap-2 pt-2">
        <fo-button variant="ghost" type="button" [disabled]="saving()" (click)="cancelled.emit()">
          Cancelar
        </fo-button>
        <fo-button variant="primary" type="submit" [loading]="saving()">
          {{ vehicle() ? 'Guardar cambios' : 'Guardar vehículo' }}
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleFormComponent {
  private readonly fb = inject(FormBuilder);

  readonly vehicle = input<Vehicle | null>(null);
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);
  readonly fieldErrors = input<Record<string, string>>({});

  readonly submitted = output<VehicleCreate>();
  readonly cancelled = output<void>();

  protected readonly primaryFuelOptions = PRIMARY_FUEL_TYPE_OPTIONS;
  protected readonly fuelOptions = FUEL_TYPE_OPTIONS;
  protected readonly fuelLabel = FUEL_TYPE_LABEL;

  readonly form = this.fb.nonNullable.group({
    nickname: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(80)]],
    brand: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(60)]],
    model: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(60)]],
    modelYear: [String(new Date().getFullYear()), [modelYearValidator]],
    licensePlate: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(20)]],
    primaryFuelType: this.fb.nonNullable.control<PrimaryFuelType>('NAFTA_SUPER', [
      Validators.required,
    ]),
    secondaryFuelType: [''],
    currentOdometerKm: ['0', [nonNegativeIntegerValidator]],
    vtvExpirationDate: [''],
    insuranceExpiration: [''],
    insuranceCompany: ['', [Validators.maxLength(80)]],
    notes: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    effect(() => {
      const existing = this.vehicle();
      if (existing) {
        this.form.reset(
          {
            nickname: existing.nickname,
            brand: existing.brand,
            model: existing.model,
            modelYear: String(existing.modelYear),
            licensePlate: existing.licensePlate,
            primaryFuelType: existing.primaryFuelType,
            secondaryFuelType: existing.secondaryFuelType ?? '',
            currentOdometerKm: String(existing.currentOdometerKm),
            vtvExpirationDate: existing.vtvExpirationDate ?? '',
            insuranceExpiration: existing.insuranceExpiration ?? '',
            insuranceCompany: existing.insuranceCompany ?? '',
            notes: existing.notes ?? '',
          },
          { emitEvent: false },
        );
      } else {
        this.form.reset(
          {
            nickname: '',
            brand: '',
            model: '',
            modelYear: String(new Date().getFullYear()),
            licensePlate: '',
            primaryFuelType: 'NAFTA_SUPER',
            secondaryFuelType: '',
            currentOdometerKm: '0',
            vtvExpirationDate: '',
            insuranceExpiration: '',
            insuranceCompany: '',
            notes: '',
          },
          { emitEvent: false },
        );
      }
    });
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
    if (this.form.invalid) {
      return;
    }

    const raw = this.form.getRawValue();
    const secondary = raw.secondaryFuelType.trim();
    const vtv = raw.vtvExpirationDate.trim();
    const insuranceExp = raw.insuranceExpiration.trim();
    const insuranceCompany = raw.insuranceCompany.trim();
    const notes = raw.notes.trim();

    const payload: VehicleCreate = {
      nickname: raw.nickname.trim(),
      brand: raw.brand.trim(),
      model: raw.model.trim(),
      modelYear: Number.parseInt(String(raw.modelYear).trim(), 10),
      licensePlate: raw.licensePlate.trim().toUpperCase(),
      primaryFuelType: raw.primaryFuelType,
      secondaryFuelType: secondary ? (secondary as FuelType) : null,
      currentOdometerKm: Number.parseInt(String(raw.currentOdometerKm).trim(), 10),
      vtvExpirationDate: vtv || null,
      insuranceExpiration: insuranceExp || null,
      insuranceCompany: insuranceCompany || null,
      notes: notes || null,
    };

    this.submitted.emit(payload);
  }
}
