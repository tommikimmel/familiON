import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent, IconComponent } from '../../../shared/ui';
import { FUEL_TYPE_LABEL, formatDecimal, formatIsoDateShort, formatKm } from '../data-access/vehicle-labels';
import { FuelLog } from '../data-access/vehicles.models';

@Component({
  selector: 'fo-vehicle-fuel-logs',
  standalone: true,
  imports: [ButtonComponent, IconComponent, MoneyPipe],
  template: `
    <section class="fo-card">
      <div class="vh-head">
        <h2 class="vh-title">Últimas cargas de combustible</h2>
        <button type="button" class="fo-sec-link fo-focus-ring rounded-md" (click)="addRequested.emit()">
          + Cargar
        </button>
      </div>

      @if (logs() === null) {
        <p data-testid="fuel-logs-degraded" class="vh-sub py-6 text-center">
          No pudimos cargar las últimas cargas de combustible.
        </p>
      } @else if (logs()!.length === 0) {
        <p class="vh-sub py-6 text-center">Todavía no registraste cargas de combustible para este vehículo.</p>
      } @else {
        <ul class="mt-2.5" aria-label="Últimas cargas de combustible">
          @for (log of logs()!; track log.id) {
            <li class="vh-row flex-wrap">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="fo-tx-title">
                    {{ fuelLabel[log.fuelType] }}
                  </span>
                  @if (log.fullTank) {
                    <span class="fo-pill-tag fo-pill-accent"> Tanque lleno </span>
                  }
                </div>

                <p class="fo-tx-meta fo-num">
                  {{ formatDate(log.fuelDate) }} · {{ formatOdometer(log.odometerKm) }} ·
                  {{ formatLiters(log.liters) }} ({{ log.pricePerLiter | money: log.currencyCode }}/L)
                </p>

                @if (log.stationName) {
                  <p class="fo-tx-meta truncate">{{ log.stationName }}</p>
                }
              </div>

              <div class="flex items-center gap-2">
                <span class="fo-tx-amt">
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
                    class="fo-focus-ring flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-danger/10 hover:text-danger disabled:opacity-50"
                  >
                    <fo-icon name="trash-2" [size]="14" />
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styleUrl: '../vehicles-ui.css',
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
