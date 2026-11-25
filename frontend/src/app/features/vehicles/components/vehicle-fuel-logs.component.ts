import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent, CardComponent, IconComponent } from '../../../shared/ui';
import {
  FUEL_TYPE_LABEL,
  formatDecimal,
  formatIsoDateShort,
  formatKm,
} from '../data-access/vehicle-labels';
import { FuelLog } from '../data-access/vehicles.models';

@Component({
  selector: 'fo-vehicle-fuel-logs',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card padding="md">
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-soft text-accent">
            <fo-icon name="fuel" [size]="16" />
          </div>
          <h2 class="text-base font-semibold text-text">Últimas cargas</h2>
        </div>

        <fo-button variant="ghost" size="sm" (click)="addRequested.emit()">
          <fo-icon name="plus" [size]="15" />
          <span>Cargar</span>
        </fo-button>
      </div>

      @if (logs() === null) {
        <p data-testid="fuel-logs-degraded" class="py-8 text-center text-sm text-muted">
          No pudimos cargar las últimas cargas de combustible.
        </p>
      } @else if (logs()!.length === 0) {
        <p class="py-8 text-center text-sm text-muted">
          Todavía no registraste cargas de combustible para este vehículo.
        </p>
      } @else {
        <ul class="mt-3 divide-y divide-border" aria-label="Últimas cargas de combustible">
          @for (log of logs()!; track log.id) {
            <li class="flex flex-wrap items-center justify-between gap-2 py-3 first:pt-1 last:pb-0">
              <div class="min-w-0 flex-1 space-y-0.5">
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="text-sm font-semibold text-text">
                    {{ fuelLabel[log.fuelType] }}
                  </span>
                  @if (log.fullTank) {
                    <span class="rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent">
                      Tanque lleno
                    </span>
                  }
                </div>

                <p class="text-xs text-muted tabular-nums">
                  {{ formatDate(log.fuelDate) }} · {{ formatOdometer(log.odometerKm) }} ·
                  {{ formatLiters(log.liters) }} ({{ log.pricePerLiter | money: log.currencyCode }}/L)
                </p>

                @if (log.stationName) {
                  <p class="truncate text-xs text-muted">{{ log.stationName }}</p>
                }
              </div>

              <div class="flex items-center gap-2">
                <span class="text-sm font-semibold text-text tabular-nums">
                  {{ log.totalAmount | money: log.currencyCode }}
                </span>

                @if (confirmId() === log.id) {
                  <div class="flex items-center gap-1">
                    <fo-button
                      variant="ghost"
                      size="sm"
                      [disabled]="deletingId() === log.id"
                      (click)="confirmId.set(null)"
                    >
                      No
                    </fo-button>
                    <fo-button
                      variant="danger"
                      size="sm"
                      [attr.data-testid]="'fuel-log-confirm-delete-' + log.id"
                      [loading]="deletingId() === log.id"
                      (click)="confirmDelete(log.id)"
                    >
                      Borrar
                    </fo-button>
                  </div>
                } @else {
                  <button
                    type="button"
                    [attr.data-testid]="'fuel-log-delete-' + log.id"
                    [attr.aria-label]="'Borrar carga del ' + formatDate(log.fuelDate)"
                    [disabled]="deletingId() === log.id"
                    (click)="confirmId.set(log.id)"
                    class="fo-focus-ring flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-danger/10 hover:text-danger disabled:opacity-50"
                  >
                    <fo-icon name="trash-2" [size]="15" />
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleFuelLogsComponent {
  readonly logs = input<readonly FuelLog[] | null>(null);
  readonly deletingId = input<string | null>(null);

  readonly deleteRequested = output<string>();
  readonly addRequested = output<void>();

  protected readonly fuelLabel = FUEL_TYPE_LABEL;
  protected readonly confirmId = signal<string | null>(null);

  protected formatDate(isoDate: string): string {
    return formatIsoDateShort(isoDate);
  }

  protected formatOdometer(km: number): string {
    return formatKm(km);
  }

  protected formatLiters(liters: string): string {
    return formatDecimal(liters, 'L');
  }

  protected confirmDelete(id: string): void {
    this.confirmId.set(null);
    this.deleteRequested.emit(id);
  }
}
