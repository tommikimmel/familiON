import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { SessionStore } from '../../../core/state/session.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import { VehicleAlertChipComponent } from '../components/vehicle-alert-chip.component';
import { VehicleFuelLogsComponent } from '../components/vehicle-fuel-logs.component';
import { VehicleMaintenanceLogsComponent } from '../components/vehicle-maintenance-logs.component';
import { VehicleMonthlyChartComponent } from '../components/vehicle-monthly-chart.component';
import { VehiclePriceTrendChartComponent } from '../components/vehicle-price-trend-chart.component';
import { VehicleDashboardStore } from '../data-access/vehicle-dashboard.store';
import {
  FUEL_TYPE_LABEL,
  formatDecimal,
  formatIsoDateShort,
  formatKm,
} from '../data-access/vehicle-labels';
import { parseProblemError } from '../utils/problem-errors';

@Component({
  selector: 'fo-vehicle-detail-page',
  standalone: true,
  providers: [VehicleDashboardStore],
  imports: [
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    IconComponent,
    MoneyPipe,
    SkeletonComponent,
    VehicleAlertChipComponent,
    VehicleFuelLogsComponent,
    VehicleMaintenanceLogsComponent,
    VehicleMonthlyChartComponent,
    VehiclePriceTrendChartComponent,
  ],
  template: `
    <section class="mx-auto max-w-5xl space-y-5 p-4 sm:p-6">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-3">
          <button
            type="button"
            aria-label="Volver a vehículos"
            (click)="goToList()"
            class="fo-focus-ring flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface text-text transition-colors hover:bg-surface-2"
          >
            <fo-icon name="chevron-left" [size]="20" />
          </button>

          @if (store.vehicle(); as vehicle) {
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h1 class="truncate text-xl font-bold tracking-tight text-text sm:text-2xl">
                  {{ vehicle.nickname }}
                </h1>
                <span
                  class="rounded-md border border-border bg-surface-2 px-2 py-0.5 font-mono text-xs font-semibold tracking-wider text-text uppercase"
                >
                  {{ vehicle.licensePlate }}
                </span>
              </div>
              <p class="mt-0.5 text-xs text-muted sm:text-sm">
                {{ vehicle.brand }} {{ vehicle.model }} · {{ vehicle.modelYear }} ·
                <span class="font-medium text-text tabular-nums">{{ formatOdometer(vehicle.currentOdometerKm) }}</span>
                · {{ fuelLabel[vehicle.primaryFuelType] }}
              </p>
            </div>
          } @else {
            <div class="min-w-0 space-y-1">
              <h1 class="text-xl font-bold tracking-tight text-text">Detalle del vehículo</h1>
              @if (store.loading()) {
                <fo-skeleton width="220px" height="14px" />
              }
            </div>
          }
        </div>

        @if (isAdult() && store.vehicleId(); as id) {
          <fo-button variant="ghost" size="sm" data-testid="vehicle-detail-edit" (click)="goToEdit(id)">
            <fo-icon name="pencil" [size]="15" />
            <span>Editar</span>
          </fo-button>
        }
      </header>

      @if (store.notFound()) {
        <fo-empty-state
          icon="car"
          title="No encontramos este vehículo"
          description="Puede haber sido eliminado o no pertenecer a tu familia."
        >
          <fo-button variant="primary" (click)="goToList()">Volver a vehículos</fo-button>
        </fo-empty-state>
      } @else {
        @if (store.error(); as error) {
          <fo-card padding="md">
            <div class="flex flex-wrap items-center justify-between gap-3" role="alert">
              <div class="flex items-center gap-2 text-sm text-danger">
                <fo-icon name="alert-circle" [size]="18" />
                <span>{{ error }}</span>
              </div>
              <fo-button variant="ghost" size="sm" (click)="store.reload()">Reintentar</fo-button>
            </div>
          </fo-card>
        }

        @if (store.alerts(); as alerts) {
          @if (alerts.length > 0) {
            <section aria-label="Alertas activas" data-testid="vehicle-alerts-banner">
              <fo-card padding="sm">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-text">
                    <fo-icon name="bell" [size]="14" />
                    Alertas
                  </span>
                  @for (alert of alerts; track alert.kind + '-' + alert.title) {
                    <fo-vehicle-alert-chip [alert]="alert" />
                  }
                </div>
              </fo-card>
            </section>
          }
        }

        @if (store.vehicleId(); as id) {
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <fo-button
              variant="primary"
              size="lg"
              [fullWidth]="true"
              data-testid="vehicle-action-fuel"
              (click)="goToFuelNew(id)"
            >
              <fo-icon name="fuel" [size]="20" />
              <span>Cargar combustible</span>
            </fo-button>

            <button
              type="button"
              data-testid="vehicle-action-maintenance"
              (click)="goToMaintenanceNew(id)"
              class="fo-focus-ring inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-xl border-2 border-accent bg-surface px-5 py-3 text-base font-medium text-accent transition-all duration-150 hover:bg-accent-soft active:scale-[0.98]"
            >
              <fo-icon name="wrench" [size]="20" />
              <span>Registrar service</span>
            </button>
          </div>
        }

        @if (store.lensForcedNominal() || store.summary()?.lensApproximated) {
          <div class="flex flex-wrap items-center gap-2 text-xs text-muted">
            @if (store.lensForcedNominal()) {
              <span
                data-testid="vehicle-lens-forced-badge"
                class="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1"
              >
                <fo-icon name="alert-circle" [size]="13" />
                La lente «% de ingresos» no aplica a vehículos: mostrando valores nominales.
              </span>
            }
            @if (store.summary()?.lensApproximated) {
              <span class="inline-flex items-center gap-1.5 rounded-full border border-warning/30 bg-warning/10 px-3 py-1 text-warning">
                <fo-icon name="alert-circle" [size]="13" />
                Algunos índices económicos son aproximados para el período.
              </span>
            }
          </div>
        }

        @if (store.loading() && !store.dashboard()) {
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-3" aria-busy="true">
            @for (item of [1, 2, 3]; track item) {
              <fo-card padding="md">
                <div class="space-y-2">
                  <fo-skeleton width="45%" height="14px" />
                  <fo-skeleton width="70%" height="28px" />
                  <fo-skeleton width="60%" height="14px" />
                </div>
              </fo-card>
            }
          </div>
        } @else {
          @if (store.summary(); as summary) {
            <section class="grid grid-cols-1 gap-4 sm:grid-cols-3" aria-label="Indicadores del período">
              <fo-card padding="md">
                <p class="text-xs font-medium text-muted">Rendimiento</p>
                <p data-testid="kpi-km-per-liter" class="mt-1.5 text-2xl font-bold text-text tabular-nums">
                  {{ formatConsumption(summary.avgKmPerLiter, 'km/L') }}
                </p>
                <p data-testid="kpi-liters-per-100" class="mt-1 text-xs text-muted tabular-nums">
                  {{ formatConsumption(summary.avgLitersPer100Km, 'L/100 km') }} ·
                  {{ formatConsumption(summary.totalLiters, 'L') }} cargados
                </p>
              </fo-card>

              <fo-card padding="md">
                <p class="text-xs font-medium text-muted">Costo por km</p>
                <p data-testid="kpi-cost-per-km" class="mt-1.5 text-2xl font-bold text-text tabular-nums">
                  @if (summary.costPerKm !== null) {
                    {{ summary.costPerKm | money: summary.unit }}
                    <span class="text-sm font-medium text-muted">/ km</span>
                  } @else {
                    —
                  }
                </p>
                <p class="mt-1 text-xs text-muted tabular-nums">
                  {{ formatOdometer(summary.distanceKm) }} recorridos
                  ({{ formatDate(summary.from) }} – {{ formatDate(summary.to) }})
                </p>
              </fo-card>

              <fo-card padding="md">
                <p class="text-xs font-medium text-muted">Costo total del período</p>
                <p data-testid="kpi-total-cost" class="mt-1.5 text-2xl font-bold text-text tabular-nums">
                  {{ summary.totalCost | money: summary.unit }}
                </p>

                <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
                  <div class="flex h-full w-full">
                    <div
                      class="h-full bg-accent transition-all duration-300"
                      [style.width.%]="fuelSharePct()"
                    ></div>
                    <div
                      class="h-full bg-warning transition-all duration-300"
                      [style.width.%]="maintenanceSharePct()"
                    ></div>
                  </div>
                </div>

                <div class="mt-1.5 flex flex-wrap items-center justify-between gap-2 text-xs text-muted tabular-nums">
                  <span>
                    Combustible:
                    <strong class="font-semibold text-accent">
                      {{ summary.fuelCost | money: summary.unit }}
                    </strong>
                  </span>
                  <span>
                    Service:
                    <strong class="font-semibold text-warning">
                      {{ summary.maintenanceCost | money: summary.unit }}
                    </strong>
                  </span>
                </div>
              </fo-card>
            </section>

            <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <fo-vehicle-monthly-chart [monthly]="summary.monthly" [unit]="summary.unit" />
              <fo-vehicle-price-trend-chart
                [trend]="summary.pricePerLiterTrend"
                [unit]="summary.unit"
              />
            </div>
          } @else if (store.dashboard()) {
            <fo-card padding="md">
              <div
                data-testid="vehicle-summary-degraded"
                class="flex flex-wrap items-center justify-between gap-3"
              >
                <div class="flex items-center gap-2 text-sm text-muted">
                  <fo-icon name="alert-circle" [size]="18" />
                  <span>No pudimos cargar las métricas de consumo y costos en este momento.</span>
                </div>
                <fo-button variant="ghost" size="sm" (click)="store.reload()">Reintentar</fo-button>
              </div>
            </fo-card>
          }

          <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <fo-vehicle-fuel-logs
              [logs]="store.recentFuelLogs()"
              [deletingId]="store.deletingFuelId()"
              (deleteRequested)="handleDeleteFuelLog($event)"
              (addRequested)="goToFuelNew(store.vehicleId()!)"
            />

            <fo-vehicle-maintenance-logs
              [logs]="store.recentMaintenance()"
              [deletingId]="store.deletingMaintenanceId()"
              (deleteRequested)="handleDeleteMaintenanceLog($event)"
              (addRequested)="goToMaintenanceNew(store.vehicleId()!)"
            />
          </div>
        }
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleDetailPage {
  protected readonly store = inject(VehicleDashboardStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  protected readonly isAdult = this.session.isAdult;
  protected readonly fuelLabel = FUEL_TYPE_LABEL;

  protected readonly fuelSharePct = computed(() => {
    const summary = this.store.summary();
    if (!summary) {
      return 0;
    }
    const fuel = Math.max(0, toNumber(summary.fuelCost));
    const maintenance = Math.max(0, toNumber(summary.maintenanceCost));
    const total = fuel + maintenance;
    return total > 0 ? Math.round((fuel / total) * 100) : 0;
  });

  protected readonly maintenanceSharePct = computed(() => {
    const summary = this.store.summary();
    if (!summary) {
      return 0;
    }
    const fuel = Math.max(0, toNumber(summary.fuelCost));
    const maintenance = Math.max(0, toNumber(summary.maintenanceCost));
    const total = fuel + maintenance;
    return total > 0 ? Math.max(0, 100 - this.fuelSharePct()) : 0;
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.store.setVehicleId(params.get('id'));
    });
  }

  protected formatOdometer(km: number): string {
    return formatKm(km);
  }

  protected formatConsumption(value: string | null, unit: string): string {
    return formatDecimal(value, unit);
  }

  protected formatDate(isoDate: string): string {
    return formatIsoDateShort(isoDate);
  }

  protected goToList(): void {
    void this.router.navigate(['/vehicles']);
  }

  protected goToEdit(id: string): void {
    void this.router.navigate(['/vehicles', id, 'edit']);
  }

  protected goToFuelNew(id: string): void {
    void this.router.navigate(['/vehicles', id, 'fuel', 'new']);
  }

  protected goToMaintenanceNew(id: string): void {
    void this.router.navigate(['/vehicles', id, 'maintenance', 'new']);
  }

  protected async handleDeleteFuelLog(logId: string): Promise<void> {
    try {
      await this.store.deleteFuelLog(logId);
      this.toast.success('Carga eliminada');
    } catch (err: unknown) {
      this.toast.error(parseProblemError(err, 'No pudimos eliminar la carga.').message);
    }
  }

  protected async handleDeleteMaintenanceLog(logId: string): Promise<void> {
    try {
      await this.store.deleteMaintenanceLog(logId);
      this.toast.success('Mantenimiento eliminado');
    } catch (err: unknown) {
      this.toast.error(parseProblemError(err, 'No pudimos eliminar el mantenimiento.').message);
    }
  }
}
