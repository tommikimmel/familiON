import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent, IconComponent } from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import { MAINTENANCE_TYPE_LABEL, formatIsoDateShort, formatKm } from '../data-access/vehicle-labels';
import { MaintenanceLog } from '../data-access/vehicles.models';

@Component({
  selector: 'fo-vehicle-maintenance-logs',
  standalone: true,
  imports: [ButtonComponent, IconComponent, MoneyPipe],
  template: `
    <section class="fo-card">
      <div class="vh-head">
        <h2 class="vh-title">Últimos mantenimientos</h2>
        <button type="button" class="fo-sec-link fo-focus-ring rounded-md" (click)="addRequested.emit()">
          + Registrar
        </button>
      </div>

      @if (logs() === null) {
        <p data-testid="maintenance-logs-degraded" class="vh-sub py-6 text-center">
          No pudimos cargar los últimos mantenimientos.
        </p>
      } @else if (logs()!.length === 0) {
        <p class="vh-sub py-6 text-center">Todavía no registraste services ni mantenimientos.</p>
      } @else {
        <ul class="mt-2.5" aria-label="Últimos mantenimientos">
          @for (log of logs()!; track log.id) {
            <li class="vh-row flex-wrap">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="fo-pill-tag">
                    {{ maintenanceLabel[log.maintenanceType] }}
                  </span>
                  <span class="fo-tx-title truncate">
                    {{ log.description }}
                  </span>
                </div>

                <p class="fo-tx-meta fo-num">
                  {{ formatDate(log.maintenanceDate) }} · {{ formatOdometer(log.odometerKm) }}
                  @if (log.workshopName) {
                    <span> · {{ log.workshopName }}</span>
                  }
                </p>

                @if (log.nextDueKm || log.nextDueDate) {
                  <p class="fo-tx-meta fo-num text-accent">
                    Próximo:
                    @if (log.nextDueKm) {
                      <span>{{ formatOdometer(log.nextDueKm) }}</span>
                    }
                    @if (log.nextDueKm && log.nextDueDate) {
                      <span> o </span>
                    }
                    @if (log.nextDueDate) {
                      <span>{{ formatDate(log.nextDueDate) }}</span>
                    }
                  </p>
                }
              </div>

              <div class="flex items-center gap-2">
                <span class="fo-tx-amt">
                  @if (hasPositiveCost(log.costAmount)) {
                    {{ log.costAmount | money: log.currencyCode }}
                  } @else {
                    Sin costo
                  }
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
                      [attr.data-testid]="'maintenance-log-confirm-delete-' + log.id"
                      [loading]="deletingId() === log.id"
                      (click)="confirmDelete(log.id)"
                    >
                      Borrar
                    </fo-button>
                  </div>
                } @else {
                  <button
                    type="button"
                    [attr.data-testid]="'maintenance-log-delete-' + log.id"
                    [attr.aria-label]="'Borrar mantenimiento ' + log.description"
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
export class VehicleMaintenanceLogsComponent {
  readonly logs = input<readonly MaintenanceLog[] | null>(null);
  readonly deletingId = input<string | null>(null);

  readonly deleteRequested = output<string>();
  readonly addRequested = output<void>();

  protected readonly maintenanceLabel = MAINTENANCE_TYPE_LABEL;
  protected readonly confirmId = signal<string | null>(null);

  protected formatDate(isoDate: string): string {
    return formatIsoDateShort(isoDate);
  }

  protected formatOdometer(km: number): string {
    return formatKm(km);
  }

  protected hasPositiveCost(costAmount: string): boolean {
    return toNumber(costAmount) > 0;
  }

  protected confirmDelete(id: string): void {
    this.confirmId.set(null);
    this.deleteRequested.emit(id);
  }
}
