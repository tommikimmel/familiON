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
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  Family,
  RATE_SOURCES,
  REFERENCE_CURRENCIES,
  SUPPORTED_CURRENCIES,
  UpdateFamilySettingsRequest,
  getRateSourceLabel,
  getSupportedTimezones,
} from '../data-access/family.models';

const FIELD_CLASS =
  'fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text transition-colors disabled:opacity-60';

@Component({
  selector: 'fo-family-settings-form',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, CardComponent, IconComponent],
  template: `
    <fo-card padding="lg">
      <div class="space-y-5">
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-1">
            <h2 class="text-base font-semibold text-text">Datos de la familia</h2>
            <p class="text-sm text-muted">
              @if (editable()) {
                Nombre, monedas y zona horaria de tu familia.
              } @else {
                Solo un adulto puede cambiar estos datos.
              }
            </p>
          </div>
          @if (editable() && !editing()) {
            <fo-button
              size="sm"
              variant="soft"
              data-testid="edit-family-settings-btn"
              (click)="startEdit()"
            >
              <fo-icon name="pencil" [size]="14" />
              <span>Editar</span>
            </fo-button>
          }
        </div>

        @if (errorMessage(); as error) {
          <div
            role="alert"
            data-testid="family-settings-error"
            class="flex items-start gap-2 rounded-xl border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger"
          >
            <fo-icon name="alert-circle" [size]="18" class="mt-0.5 shrink-0" />
            <span>{{ error }}</span>
          </div>
        }

        @if (editing()) {
          <form
            [formGroup]="form"
            (ngSubmit)="onSubmit()"
            novalidate
            data-testid="family-settings-form"
            class="space-y-4"
          >
            <div class="space-y-1.5">
              <label for="family-name" class="block text-sm font-medium text-text">
                Nombre de la familia
              </label>
              <input
                id="family-name"
                type="text"
                formControlName="name"
                maxlength="120"
                [class]="fieldClass"
              />
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label for="family-base-currency" class="block text-sm font-medium text-text">
                  Moneda principal
                </label>
                <select
                  id="family-base-currency"
                  formControlName="baseCurrency"
                  [class]="fieldClass"
                >
                  @for (currency of supportedCurrencies; track currency.value) {
                    <option [value]="currency.value">{{ currency.label }}</option>
                  }
                </select>
              </div>

              <div class="space-y-1.5">
                <label for="family-reference-currency" class="block text-sm font-medium text-text">
                  Moneda de referencia
                </label>
                <select
                  id="family-reference-currency"
                  formControlName="referenceCurrency"
                  [class]="fieldClass"
                >
                  @for (currency of referenceCurrencies; track currency.value) {
                    <option [value]="currency.value">{{ currency.label }}</option>
                  }
                </select>
              </div>

              <div class="space-y-1.5">
                <label for="family-rate-source" class="block text-sm font-medium text-text">
                  Cotización
                </label>
                <select
                  id="family-rate-source"
                  formControlName="referenceRateSource"
                  [class]="fieldClass"
                >
                  @for (source of rateSources; track source.value) {
                    <option [value]="source.value">{{ source.label }}</option>
                  }
                </select>
              </div>

              <div class="space-y-1.5">
                <label for="family-timezone" class="block text-sm font-medium text-text">
                  Zona horaria
                </label>
                <select id="family-timezone" formControlName="timezone" [class]="fieldClass">
                  <optgroup label="América">
                    @for (tz of timezoneGroups().america; track tz) {
                      <option [value]="tz">{{ tz }}</option>
                    }
                  </optgroup>
                  @if (timezoneGroups().other.length > 0) {
                    <optgroup label="Otras zonas horarias">
                      @for (tz of timezoneGroups().other; track tz) {
                        <option [value]="tz">{{ tz }}</option>
                      }
                    </optgroup>
                  }
                </select>
              </div>
            </div>

            @if (pendingPayload()) {
              <div
                role="alertdialog"
                aria-labelledby="family-settings-confirm-title"
                data-testid="family-settings-confirm"
                class="space-y-3 rounded-xl border border-warning/40 bg-warning/10 p-4"
              >
                <p id="family-settings-confirm-title" class="text-sm font-semibold text-text">
                  ¿Seguro que querés cambiar la moneda principal o la zona horaria?
                </p>
                <p class="text-sm text-muted">
                  Cambia cómo se calculan y muestran los saldos de toda la familia.
                </p>
                <div class="flex flex-wrap justify-end gap-2">
                  <fo-button
                    size="sm"
                    variant="ghost"
                    [disabled]="saving()"
                    (click)="pendingPayload.set(null)"
                  >
                    Volver
                  </fo-button>
                  <fo-button
                    size="sm"
                    data-testid="confirm-family-settings-btn"
                    [loading]="saving()"
                    (click)="confirmSave()"
                  >
                    Sí, cambiar
                  </fo-button>
                </div>
              </div>
            } @else {
              <div class="flex flex-wrap justify-end gap-2 pt-1">
                <fo-button
                  variant="ghost"
                  data-testid="cancel-family-settings-btn"
                  [disabled]="saving()"
                  (click)="cancelEdit()"
                >
                  Cancelar
                </fo-button>
                <fo-button
                  type="submit"
                  [loading]="saving()"
                  [disabled]="saving() || form.invalid || !form.dirty"
                >
                  Guardar
                </fo-button>
              </div>
            }
          </form>
        } @else {
          <dl
            data-testid="family-settings-readonly"
            class="grid gap-x-6 gap-y-4 text-sm sm:grid-cols-2"
          >
            <div class="space-y-0.5 sm:col-span-2">
              <dt class="text-xs text-muted">Nombre</dt>
              <dd class="font-medium text-text">{{ family().name }}</dd>
            </div>
            <div class="space-y-0.5">
              <dt class="text-xs text-muted">Moneda principal</dt>
              <dd class="font-medium text-text">{{ family().baseCurrency }}</dd>
            </div>
            <div class="space-y-0.5">
              <dt class="text-xs text-muted">Moneda de referencia</dt>
              <dd class="font-medium text-text">{{ family().referenceCurrency }}</dd>
            </div>
            <div class="space-y-0.5">
              <dt class="text-xs text-muted">Cotización</dt>
              <dd class="font-medium text-text">{{ rateSourceLabel() }}</dd>
            </div>
            <div class="space-y-0.5">
              <dt class="text-xs text-muted">Zona horaria</dt>
              <dd class="font-medium text-text">{{ family().timezone }}</dd>
            </div>
          </dl>
        }
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilySettingsFormComponent {
  private readonly fb = inject(FormBuilder);

  readonly family = input.required<Family>();
  readonly editable = input<boolean>(false);
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly saveSettings = output<UpdateFamilySettingsRequest>();

  protected readonly fieldClass = FIELD_CLASS;
  protected readonly supportedCurrencies = SUPPORTED_CURRENCIES;
  protected readonly referenceCurrencies = REFERENCE_CURRENCIES;
  protected readonly rateSources = RATE_SOURCES;
  protected readonly editing = signal(false);
  /** Sensitive change (base currency / timezone) waiting for explicit confirmation. */
  protected readonly pendingPayload = signal<UpdateFamilySettingsRequest | null>(null);

  protected readonly timezoneGroups = computed(() =>
    getSupportedTimezones(this.family().timezone),
  );
  protected readonly rateSourceLabel = computed(() =>
    getRateSourceLabel(this.family().referenceRateSource),
  );

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    baseCurrency: ['ARS', [Validators.required]],
    referenceCurrency: ['USD', [Validators.required]],
    referenceRateSource: ['USD_MEP', [Validators.required]],
    timezone: ['America/Argentina/Buenos_Aires', [Validators.required]],
  });

  constructor() {
    // A new family value means the save went through (or data reloaded): back to read-only.
    effect(() => {
      this.family();
      this.resetForm();
      this.editing.set(false);
      this.pendingPayload.set(null);
    });
  }

  protected startEdit(): void {
    this.resetForm();
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.resetForm();
    this.pendingPayload.set(null);
    this.editing.set(false);
  }

  protected onSubmit(): void {
    if (!this.editable() || this.saving()) {
      return;
    }
    const raw = this.form.getRawValue();
    const trimmedName = raw.name.trim();
    if (!trimmedName || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const current = this.family();
    const payload: UpdateFamilySettingsRequest = {};
    if (trimmedName !== current.name) {
      payload.name = trimmedName;
    }
    if (raw.baseCurrency !== current.baseCurrency) {
      payload.baseCurrency = raw.baseCurrency;
    }
    if (raw.referenceCurrency !== current.referenceCurrency) {
      payload.referenceCurrency = raw.referenceCurrency;
    }
    if (raw.referenceRateSource !== current.referenceRateSource) {
      payload.referenceRateSource = raw.referenceRateSource;
    }
    if (raw.timezone !== current.timezone) {
      payload.timezone = raw.timezone;
    }

    if (Object.keys(payload).length === 0) {
      this.cancelEdit();
      return;
    }

    if (payload.baseCurrency || payload.timezone) {
      this.pendingPayload.set(payload);
      return;
    }

    this.saveSettings.emit(payload);
  }

  protected confirmSave(): void {
    const payload = this.pendingPayload();
    if (payload && !this.saving()) {
      this.saveSettings.emit(payload);
    }
  }

  private resetForm(): void {
    const fam = this.family();
    this.form.reset(
      {
        name: fam.name,
        baseCurrency: fam.baseCurrency,
        referenceCurrency: fam.referenceCurrency,
        referenceRateSource: fam.referenceRateSource,
        timezone: fam.timezone,
      },
      { emitEvent: false },
    );
  }
}
