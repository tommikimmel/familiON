import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
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

@Component({
  selector: 'fo-family-settings-form',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, CardComponent, IconComponent],
  template: `
    <fo-card padding="md">
      <div class="space-y-4">
        <div class="space-y-0.5">
          <h2 class="text-base font-semibold text-text">Configuración económica</h2>
          <p class="text-xs text-muted">
            @if (editable()) {
              Define el nombre, divisas de referencia y zona horaria de tu familia.
            } @else {
              Solo un adulto de la familia puede modificar esta configuración.
            }
          </p>
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

        @if (editable()) {
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
                class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text transition-colors disabled:opacity-60"
              />
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label for="family-base-currency" class="block text-sm font-medium text-text">
                  Divisa principal
                </label>
                <select
                  id="family-base-currency"
                  formControlName="baseCurrency"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text transition-colors disabled:opacity-60"
                >
                  @for (currency of supportedCurrencies; track currency.value) {
                    <option [value]="currency.value">{{ currency.label }}</option>
                  }
                </select>
              </div>

              <div class="space-y-1.5">
                <label for="family-reference-currency" class="block text-sm font-medium text-text">
                  Divisa de referencia
                </label>
                <select
                  id="family-reference-currency"
                  formControlName="referenceCurrency"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text transition-colors disabled:opacity-60"
                >
                  @for (currency of referenceCurrencies; track currency.value) {
                    <option [value]="currency.value">{{ currency.label }}</option>
                  }
                </select>
              </div>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1.5">
                <label for="family-rate-source" class="block text-sm font-medium text-text">
                  Fuente de cotización
                </label>
                <select
                  id="family-rate-source"
                  formControlName="referenceRateSource"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text transition-colors disabled:opacity-60"
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
                <select
                  id="family-timezone"
                  formControlName="timezone"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text transition-colors disabled:opacity-60"
                >
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

            <div class="flex justify-end pt-1">
              <fo-button
                type="submit"
                [loading]="saving()"
                [disabled]="saving() || form.invalid || !form.dirty"
              >
                Guardar cambios
              </fo-button>
            </div>
          </form>
        } @else {
          <dl
            data-testid="family-settings-readonly"
            class="grid gap-3 rounded-xl border border-border bg-surface-2/60 p-4 text-sm sm:grid-cols-2"
          >
            <div>
              <dt class="text-xs text-muted">Nombre de la familia</dt>
              <dd class="font-medium text-text">{{ family().name }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted">Divisa principal</dt>
              <dd class="font-medium text-text">{{ family().baseCurrency }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted">Divisa de referencia</dt>
              <dd class="font-medium text-text">{{ family().referenceCurrency }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted">Fuente de cotización</dt>
              <dd class="font-medium text-text">{{ rateSourceLabel() }}</dd>
            </div>
            <div class="sm:col-span-2">
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

  protected readonly supportedCurrencies = SUPPORTED_CURRENCIES;
  protected readonly referenceCurrencies = REFERENCE_CURRENCIES;
  protected readonly rateSources = RATE_SOURCES;

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
    effect(() => {
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
    });
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
      this.form.markAsPristine();
      return;
    }

    this.saveSettings.emit(payload);
  }
}
