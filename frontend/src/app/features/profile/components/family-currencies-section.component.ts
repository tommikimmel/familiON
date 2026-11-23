import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  EnableFamilyCurrencyRequest,
  FamilyCurrency,
  RATE_SOURCES,
  SUPPORTED_CURRENCIES,
  getRateSourceLabel,
} from '../data-access/family.models';

@Component({
  selector: 'fo-family-currencies-section',
  standalone: true,
  imports: [FormsModule, ButtonComponent, CardComponent, IconComponent],
  template: `
    <fo-card padding="md">
      <div class="space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="space-y-0.5">
            <h2 class="text-base font-semibold text-text">Divisas habilitadas</h2>
            <p class="text-xs text-muted">
              Monedas disponibles para registrar movimientos y consultar saldos.
            </p>
          </div>

          @if (editable() && availableToAdd().length > 0 && !showAddForm()) {
            <fo-button
              size="sm"
              variant="ghost"
              data-testid="open-add-currency-btn"
              (click)="openAddForm()"
            >
              <fo-icon name="plus" [size]="16" />
              <span>Agregar divisa</span>
            </fo-button>
          }
        </div>

        <ul class="grid gap-2.5 sm:grid-cols-2" aria-label="Lista de divisas habilitadas">
          @for (currency of currencies(); track currency.currencyCode) {
            @let isBase = currency.currencyCode === baseCurrency();
            @let isUpdating = updatingCode() === currency.currencyCode;
            <li
              data-testid="currency-chip-item"
              [attr.data-currency]="currency.currencyCode"
              class="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface-2/60 px-3.5 py-2.5"
              [class.opacity-60]="!currency.isActive"
            >
              <div class="flex min-w-0 items-center gap-2.5">
                <span
                  class="relative inline-flex h-6 w-6 shrink-0 overflow-hidden rounded-full ring-1 ring-black/10"
                  aria-hidden="true"
                >
                  @switch (currency.currencyCode) {
                    @case ('ARS') {
                      <svg viewBox="0 0 24 24" class="h-full w-full">
                        <rect width="24" height="8" y="0" fill="#74ACDF" />
                        <rect width="24" height="8" y="8" fill="#FFFFFF" />
                        <rect width="24" height="8" y="16" fill="#74ACDF" />
                        <circle cx="12" cy="12" r="2.6" fill="#F6B40E" />
                      </svg>
                    }
                    @case ('USD') {
                      <svg viewBox="0 0 24 24" class="h-full w-full">
                        <rect width="24" height="24" fill="#FFFFFF" />
                        <rect width="24" height="3" y="0" fill="#B22234" />
                        <rect width="24" height="3" y="6" fill="#B22234" />
                        <rect width="24" height="3" y="12" fill="#B22234" />
                        <rect width="24" height="3" y="18" fill="#B22234" />
                        <rect width="12" height="12" x="0" y="0" fill="#3C3B6E" />
                      </svg>
                    }
                    @case ('EUR') {
                      <svg viewBox="0 0 24 24" class="h-full w-full">
                        <rect width="24" height="24" fill="#003399" />
                        <circle
                          cx="12"
                          cy="12"
                          r="6"
                          fill="none"
                          stroke="#FFCC00"
                          stroke-width="2"
                          stroke-dasharray="1.8 2.9"
                        />
                      </svg>
                    }
                    @case ('USDT') {
                      <svg viewBox="0 0 24 24" class="h-full w-full">
                        <rect width="24" height="24" fill="#26A17B" />
                        <path
                          d="M6.5 7.5H17.5M12 7.5V17"
                          stroke="#FFFFFF"
                          stroke-width="2.2"
                          stroke-linecap="round"
                        />
                        <ellipse
                          cx="12"
                          cy="12"
                          rx="6.2"
                          ry="2.2"
                          fill="none"
                          stroke="#FFFFFF"
                          stroke-width="1.6"
                        />
                      </svg>
                    }
                    @default {
                      <svg viewBox="0 0 24 24" class="h-full w-full">
                        <rect width="24" height="24" fill="var(--fo-accent)" />
                        <circle
                          cx="12"
                          cy="12"
                          r="6"
                          fill="none"
                          stroke="var(--fo-accent-contrast)"
                          stroke-width="1.8"
                        />
                      </svg>
                    }
                  }
                </span>

                <div class="min-w-0">
                  <div class="flex items-center gap-1.5">
                    <span class="text-sm font-semibold text-text">
                      {{ currency.currencyCode }}
                    </span>
                    @if (isBase) {
                      <span
                        class="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent"
                      >
                        Principal
                      </span>
                    }
                  </div>
                  @if (currency.preferredRateSource) {
                    <p class="truncate text-xs text-muted">
                      {{ rateSourceLabel(currency.preferredRateSource) }}
                    </p>
                  }
                </div>
              </div>

              @if (editable()) {
                <button
                  type="button"
                  role="switch"
                  data-testid="currency-toggle-switch"
                  [attr.data-currency]="currency.currencyCode"
                  [attr.aria-checked]="currency.isActive"
                  [attr.aria-label]="
                    isBase
                      ? currency.currencyCode + ' es la divisa principal y no se puede desactivar'
                      : (currency.isActive ? 'Desactivar ' : 'Activar ') + currency.currencyCode
                  "
                  [disabled]="isBase || isUpdating"
                  (click)="onToggle(currency)"
                  class="fo-focus-ring relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-50"
                  [class.cursor-pointer]="!isBase && !isUpdating"
                  [class.bg-accent]="currency.isActive"
                  [class.bg-border]="!currency.isActive"
                >
                  <span
                    aria-hidden="true"
                    class="inline-block h-4 w-4 rounded-full bg-white shadow-xs transition-transform duration-150"
                    [class.translate-x-6]="currency.isActive"
                    [class.translate-x-1]="!currency.isActive"
                  ></span>
                </button>
              } @else {
                <span class="text-xs font-medium text-muted">
                  {{ currency.isActive ? 'Activa' : 'Inactiva' }}
                </span>
              }
            </li>
          }
        </ul>

        @if (editable() && showAddForm() && availableToAdd().length > 0) {
          <form
            data-testid="add-currency-form"
            (ngSubmit)="onAddSubmit()"
            class="space-y-3 rounded-xl border border-border bg-surface-2/50 p-4"
          >
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="space-y-1">
                <label for="add-currency-code" class="block text-xs font-medium text-text">
                  Divisa
                </label>
                <select
                  id="add-currency-code"
                  name="currencyCode"
                  [ngModel]="selectedCode()"
                  (ngModelChange)="selectedCode.set($event)"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                >
                  @for (option of availableToAdd(); track option.value) {
                    <option [value]="option.value">{{ option.label }}</option>
                  }
                </select>
              </div>

              <div class="space-y-1">
                <label for="add-currency-rate-source" class="block text-xs font-medium text-text">
                  Cotización preferida (opcional)
                </label>
                <select
                  id="add-currency-rate-source"
                  name="preferredRateSource"
                  [ngModel]="selectedRateSource()"
                  (ngModelChange)="selectedRateSource.set($event)"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                >
                  <option value="">Automática</option>
                  @for (source of rateSources; track source.value) {
                    <option [value]="source.value">{{ source.label }}</option>
                  }
                </select>
              </div>
            </div>

            <div class="flex justify-end gap-2">
              <fo-button
                type="button"
                size="sm"
                variant="ghost"
                [disabled]="adding()"
                (click)="cancelAddForm()"
              >
                Cancelar
              </fo-button>
              <fo-button type="submit" size="sm" [loading]="adding()" [disabled]="adding()">
                Agregar divisa
              </fo-button>
            </div>
          </form>
        }
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyCurrenciesSectionComponent {
  readonly currencies = input.required<readonly FamilyCurrency[]>();
  readonly baseCurrency = input.required<string>();
  readonly editable = input<boolean>(false);
  readonly adding = input<boolean>(false);
  readonly updatingCode = input<string | null>(null);

  readonly addCurrency = output<EnableFamilyCurrencyRequest>();
  readonly toggleCurrency = output<{ currencyCode: string; isActive: boolean }>();

  protected readonly rateSources = RATE_SOURCES;
  protected readonly showAddForm = signal(false);
  protected readonly selectedCode = signal<string>('');
  protected readonly selectedRateSource = signal<string>('');

  protected readonly availableToAdd = computed(() => {
    const existing = new Set(this.currencies().map((c) => c.currencyCode.toUpperCase()));
    return SUPPORTED_CURRENCIES.filter((option) => !existing.has(option.value));
  });

  protected rateSourceLabel(source: string | null): string {
    return getRateSourceLabel(source);
  }

  protected openAddForm(): void {
    const available = this.availableToAdd();
    if (available.length === 0) {
      return;
    }
    this.selectedCode.set(available[0].value);
    this.selectedRateSource.set('');
    this.showAddForm.set(true);
  }

  protected cancelAddForm(): void {
    this.showAddForm.set(false);
  }

  protected onAddSubmit(): void {
    const code = this.selectedCode();
    if (!code || this.adding()) {
      return;
    }
    const source = this.selectedRateSource().trim();
    this.addCurrency.emit({
      currencyCode: code,
      ...(source ? { preferredRateSource: source } : {}),
    });
    this.showAddForm.set(false);
  }

  protected onToggle(currency: FamilyCurrency): void {
    if (!this.editable() || currency.currencyCode === this.baseCurrency()) {
      return;
    }
    this.toggleCurrency.emit({
      currencyCode: currency.currencyCode,
      isActive: !currency.isActive,
    });
  }
}
