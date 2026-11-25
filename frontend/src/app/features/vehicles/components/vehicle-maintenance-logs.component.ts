import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent, CardComponent, IconComponent } from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import {
  MAINTENANCE_TYPE_LABEL,
  formatIsoDateShort,
  formatKm,
} from '../data-access/vehicle-labels';
import { MaintenanceLog } from '../data-access/vehicles.models';

@Component({
  selector: 'fo-vehicle-maintenance-logs',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card padding="md">
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-warning/15 text-warning">
            <fo-icon name="wrench" [size]="16" />
          </div>
          <h2 class="text-base font-semibold text-text">Últimos mantenimientos</h2>
        </div>

        <fo-button variant="ghost" size="sm" (click)="addRequested.emit()">
          <fo-icon name="plus" [size]="15" />
          <span>Registrar</span>
        </fo-button>
      </div>

      @if (logs() === null) {
        <p data-testid="maintenance-logs-degraded" class="py-8 text-center text-sm text-muted">
          No pudimos cargar los últimos mantenimientos.
        </p>
      } @else if (logs()!.length === 0) {
        <p class="py-8 text-center text-sm text-muted">
          Todavía no registraste services ni mantenimientos.
        </p>
      } @else {
        <ul class="mt-3 divide-y divide-border" aria-label="Últimos mantenimientos">
          @for (log of logs()!; track log.id) {
            <li class="flex flex-wrap items-center justify-between gap-2 py-3 first:pt-1 last:pb-0">
              <div class="min-w-0 flex-1 space-y-0.5">
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-text">
                    {{ maintenanceLabel[log.maintenanceType] }}
                  </span>
                  <span class="truncate text-sm font-semibold text-text">
                    {{ log.description }}
                  </span>
                </div>

                <p class="text-xs text-muted tabular-nums">
                  {{ formatDate(log.maintenanceDate) }} · {{ formatOdometer(log.odometerKm) }}
                  @if (log.workshopName) {
                    <span> · {{ log.workshopName }}</span>
                  }
                </p>

                @if (log.nextDueKm || log.nextDueDate) {
                  <p class="text-xs text-accent tabular-nums">
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
                <span class="text-sm font-semibold text-text tabular-nums">
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
