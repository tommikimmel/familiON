import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CardComponent, IconComponent } from '../../../shared/ui';
import {
  FUEL_TYPE_LABEL,
  formatKm,
  sortVehicleAlerts,
} from '../data-access/vehicle-labels';
import { Vehicle } from '../data-access/vehicles.models';
import { VehicleAlertChipComponent } from './vehicle-alert-chip.component';

@Component({
  selector: 'fo-vehicle-card',
  standalone: true,
  imports: [CardComponent, IconComponent, VehicleAlertChipComponent],
  template: `
    <fo-card padding="md" [interactive]="true">
      <div class="flex flex-col gap-3">
        <div class="flex items-start justify-between gap-3">
          <button
            type="button"
            data-testid="vehicle-card-open"
            (click)="opened.emit(vehicle().id)"
            class="fo-focus-ring group flex min-w-0 flex-1 cursor-pointer items-start gap-3 rounded-xl text-left"
          >
            <div
              class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
            >
              <fo-icon name="car" [size]="22" />
            </div>

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <h2 class="truncate text-base font-semibold text-text group-hover:text-accent">
                  {{ vehicle().nickname }}
                </h2>
                <span
                  class="rounded-md border border-border bg-surface-2 px-2 py-0.5 font-mono text-xs font-semibold tracking-wider text-text uppercase"
                >
                  {{ vehicle().licensePlate }}
                </span>
              </div>
              <p class="mt-0.5 truncate text-sm text-muted">
                {{ vehicle().brand }} {{ vehicle().model }} · {{ vehicle().modelYear }}
              </p>
            </div>
          </button>

          @if (isAdult()) {
            <button
              type="button"
              data-testid="vehicle-card-edit"
              [attr.aria-label]="'Editar ' + vehicle().nickname"
              (click)="edited.emit(vehicle().id)"
              class="fo-focus-ring flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-xl text-muted transition-colors hover:bg-surface-2 hover:text-text"
            >
              <fo-icon name="pencil" [size]="16" />
            </button>
          }
        </div>

        <div class="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-muted">
          <span class="inline-flex items-center gap-1.5 font-medium text-text tabular-nums">
            <fo-icon name="trending-up" [size]="14" />
            {{ formattedOdometer() }}
          </span>

          <span class="inline-flex items-center gap-1.5">
            <fo-icon name="fuel" [size]="14" />
            {{ fuelSummary() }}
          </span>
        </div>

        @if (sortedAlerts().length > 0) {
          <div class="flex flex-wrap gap-1.5" aria-label="Alertas del vehículo">
            @for (alert of sortedAlerts(); track alert.kind + '-' + alert.title) {
              <fo-vehicle-alert-chip [alert]="alert" [compact]="true" />
            }
          </div>
        }

        <div class="flex items-center justify-between gap-2 border-t border-border pt-3">
          <button
            type="button"
            data-testid="vehicle-card-fuel"
            (click)="fuelClicked.emit(vehicle().id)"
            class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-xl bg-accent-soft px-3 py-2 text-xs font-semibold text-accent transition-opacity hover:opacity-90"
          >
            <fo-icon name="fuel" [size]="15" />
            <span>Cargar combustible</span>
          </button>

          <button
            type="button"
            (click)="opened.emit(vehicle().id)"
            class="fo-focus-ring inline-flex cursor-pointer items-center gap-1 rounded-xl px-2.5 py-2 text-xs font-medium text-muted transition-colors hover:bg-surface-2 hover:text-text"
          >
            <span>Ver ficha</span>
            <fo-icon name="chevron-right" [size]="15" />
          </button>
        </div>
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleCardComponent {
  readonly vehicle = input.required<Vehicle>();
  readonly isAdult = input<boolean>(false);

  readonly opened = output<string>();
  readonly edited = output<string>();
  readonly fuelClicked = output<string>();

  protected readonly formattedOdometer = computed(() => formatKm(this.vehicle().currentOdometerKm));

  protected readonly fuelSummary = computed(() => {
    const v = this.vehicle();
    const primary = FUEL_TYPE_LABEL[v.primaryFuelType] ?? v.primaryFuelType;
    if (v.secondaryFuelType) {
      const secondary = FUEL_TYPE_LABEL[v.secondaryFuelType] ?? v.secondaryFuelType;
      return `${primary} / ${secondary}`;
    }
    return primary;
  });

  protected readonly sortedAlerts = computed(() => sortVehicleAlerts(this.vehicle().alerts ?? []));
}
