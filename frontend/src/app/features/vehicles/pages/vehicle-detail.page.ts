import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { SessionStore } from '../../../core/state/session.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  ButtonComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { VehicleAlertChipComponent } from '../components/vehicle-alert-chip.component';
import { VehicleFuelLogsComponent } from '../components/vehicle-fuel-logs.component';
import { VehicleMaintenanceLogsComponent } from '../components/vehicle-maintenance-logs.component';
import { VehicleMonthlyChartComponent } from '../components/vehicle-monthly-chart.component';
import { VehiclePriceTrendChartComponent } from '../components/vehicle-price-trend-chart.component';
import { VehicleDashboardStore } from '../data-access/vehicle-dashboard.store';
import { FUEL_TYPE_LABEL, formatDecimal, formatIsoDateShort, formatKm } from '../data-access/vehicle-labels';
import { parseProblemError } from '../utils/problem-errors';

@Component({
  selector: 'fo-vehicle-detail-page',
  standalone: true,
  providers: [VehicleDashboardStore],
  imports: [
    ButtonComponent,
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
    <section class="vh-stack">
      @if (store.notFound()) {
        <h1 class="sr-only">Detalle del vehículo</h1>
        <fo-empty-state
          icon="car"
          title="No encontramos este vehículo"
          description="Puede haber sido eliminado o no pertenecer a tu familia."
        >
          <fo-button variant="primary" (click)="goToList()">Volver a vehículos</fo-button>
        </fo-empty-state>
      } @else {
        @if (store.error(); as error) {
          <div class="fo-card flex flex-wrap items-center justify-between gap-3" role="alert">
            <div class="flex items-center gap-2 text-xs text-danger">
              <fo-icon name="alert-circle" [size]="16" />
              <span>{{ error }}</span>
            </div>
            <fo-button variant="ghost" size="sm" (click)="store.reload()">Reintentar</fo-button>
          </div>
        }

        <div class="veh-top fo-card fo-hero-glow" aria-label="Indicadores del período">
          <div class="veh-id">
            @if (store.vehicle(); as vehicle) {
              <div class="vh-head">
                <p class="fo-hero-label fo-num truncate">
                  {{ vehicle.licensePlate }} · {{ fuelLabel[vehicle.primaryFuelType] }}
                </p>
                <div class="flex shrink-0 items-center gap-2.5">
                  <button
                    type="button"
                    aria-label="Volver a vehículos"
                    (click)="goToList()"
                    class="fo-sec-link fo-focus-ring rounded-md"
                  >
                    Todos
                  </button>
                  @if (isAdult() && store.vehicleId(); as id) {
                    <button
                      type="button"
                      data-testid="vehicle-detail-edit"
                      class="fo-sec-link fo-focus-ring rounded-md"
                      (click)="goToEdit(id)"
                    >
                      Editar
                    </button>
                  }
                </div>
              </div>
              <h1 class="veh-name">{{ vehicle.brand }} {{ vehicle.model }} {{ vehicle.modelYear }}</h1>
              <p class="veh-odo fo-num">
                Odómetro: <strong>{{ formatOdometer(vehicle.currentOdometerKm) }}</strong>
                @if (vehicle.nickname) {
                  · {{ vehicle.nickname }}
                }
              </p>
            } @else {
              <h1 class="veh-name">Detalle del vehículo</h1>
              @if (store.loading()) {
                <fo-skeleton width="180px" height="12px" />
              }
            }
          </div>

          @if (store.summary(); as summary) {
            <div class="veh-stat">
              <p data-testid="kpi-km-per-liter" class="veh-stat-v">
                {{ formatConsumption(summary.avgKmPerLiter, 'km/L') }}
              </p>
              <p class="veh-stat-k">Consumo promedio</p>
              <p data-testid="kpi-liters-per-100" class="veh-stat-sub">
                {{ formatConsumption(summary.avgLitersPer100Km, 'L cada 100 km') }}
              </p>
            </div>
            <div class="veh-stat">
              <p data-testid="kpi-cost-per-km" class="veh-stat-v">
                @if (summary.costPerKm !== null) {
                  {{ summary.costPerKm | money: summary.unit }}/km
                } @else {
                  —
                }
              </p>
              <p class="veh-stat-k">Costo por km</p>
              <p class="veh-stat-sub">Combustible + mantenimiento</p>
            </div>
            <div class="veh-stat">
              <p data-testid="kpi-total-cost" class="veh-stat-v">
                {{ summary.fuelCost | money: summary.unit }}
              </p>
              <p class="veh-stat-k">Combustible del período</p>
              <p class="veh-stat-sub">
                Total {{ summary.totalCost | money: summary.unit }} ·
                {{ formatOdometer(summary.distanceKm) }}
              </p>
            </div>
          } @else if (store.loading() && !store.dashboard()) {
            @for (item of [1, 2, 3]; track item) {
              <div class="veh-stat" aria-busy="true">
                <fo-skeleton width="60%" height="14px" />
              </div>
            }
          }
        </div>

        @if (store.vehicleId(); as id) {
          <div class="veh-actions">
            <button
              type="button"
              data-testid="vehicle-action-fuel"
              class="fo-btn-primary fo-focus-ring inline-flex items-center justify-center gap-1.5"
              (click)="goToFuelNew(id)"
            >
              <fo-icon name="fuel" [size]="15" />
              <span>Cargar combustible</span>
            </button>
            <button
              type="button"
              data-testid="vehicle-action-maintenance"
              class="fo-btn-soft fo-focus-ring inline-flex items-center justify-center gap-1.5"
              (click)="goToMaintenanceNew(id)"
            >
              <fo-icon name="wrench" [size]="15" />
              <span>Registrar service</span>
            </button>
          </div>
        }

        @if (store.lensForcedNominal() || store.summary()?.lensApproximated) {
          <div class="flex flex-wrap items-center gap-2">
            @if (store.lensForcedNominal()) {
              <span data-testid="vehicle-lens-forced-badge" class="fo-pill-tag gap-1">
                <fo-icon name="alert-circle" [size]="11" />
                La lente «% de ingresos» no aplica a vehículos: mostrando valores nominales.
              </span>
            }
            @if (store.summary()?.lensApproximated) {
              <span class="fo-pill-tag vh-pill-warning gap-1">
                <fo-icon name="alert-circle" [size]="11" />
                Algunos índices económicos son aproximados para el período.
              </span>
            }
          </div>
        }

        @if (!store.summary() && store.dashboard()) {
          <div data-testid="vehicle-summary-degraded" class="fo-card flex flex-wrap items-center justify-between gap-3">
            <div class="vh-sub flex items-center gap-2">
              <fo-icon name="alert-circle" [size]="16" />
              <span>No pudimos cargar las métricas de consumo y costos en este momento.</span>
            </div>
            <fo-button variant="ghost" size="sm" (click)="store.reload()">Reintentar</fo-button>
          </div>
        }

        @if (!store.loading() || store.dashboard()) {
          <div class="vh-two-col">
            <section
              class="fo-card"
              aria-label="Alertas activas"
              [attr.data-testid]="(store.alerts()?.length ?? 0) > 0 ? 'vehicle-alerts-banner' : null"
            >
              <h2 class="vh-title mb-2.5">Desgaste y vencimientos</h2>
              @if (store.alerts(); as alerts) {
                @if (alerts.length > 0) {
                  <div>
                    @for (alert of alerts; track alert.kind + '-' + alert.title) {
                      <fo-vehicle-alert-chip class="vh-row" layout="row" [alert]="alert" />
                    }
                  </div>
                } @else {
                  <p class="vh-sub">Sin vencimientos próximos. Todo al día.</p>
                }
              } @else {
                <p class="vh-sub">No pudimos cargar las alertas.</p>
              }
            </section>

            <fo-vehicle-fuel-logs
              [logs]="store.recentFuelLogs()"
              [deletingId]="store.deletingFuelId()"
              (deleteRequested)="handleDeleteFuelLog($event)"
              (addRequested)="goToFuelNew(store.vehicleId()!)"
            />
          </div>

          @if (store.summary(); as summary) {
            <div class="vh-two-col">
              <fo-vehicle-monthly-chart [monthly]="summary.monthly" [unit]="summary.unit" />
              <fo-vehicle-price-trend-chart [trend]="summary.pricePerLiterTrend" [unit]="summary.unit" />
            </div>
          }

          <fo-vehicle-maintenance-logs
            [logs]="store.recentMaintenance()"
            [deletingId]="store.deletingMaintenanceId()"
            (deleteRequested)="handleDeleteMaintenanceLog($event)"
            (addRequested)="goToMaintenanceNew(store.vehicleId()!)"
          />
        }
      }
    </section>
  `,
  styleUrl: '../vehicles-ui.css',
  styles: `
    .veh-top {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 8px;
    }
    .veh-id {
      grid-column: 1 / -1;
      min-width: 0;
    }
    .veh-name {
      font-size: 16px;
      font-weight: 700;
      margin-top: 2px;
    }
    .veh-odo {
      font-size: 11.5px;
      color: var(--fo-text-muted);
      margin: 2px 0 2px;
    }
    .veh-odo strong {
      color: var(--fo-text);
    }
    .veh-stat {
      display: flex;
      flex-direction: column;
      align-items: center;
      min-width: 0;
      padding: 8px;
      border-radius: 11px;
      background: var(--fo-surface-2);
      text-align: center;
    }
    .veh-stat-v {
      font-size: 13px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .veh-stat-k {
      font-size: 9.5px;
      color: var(--fo-text-muted);
      margin-top: 2px;
    }
    .veh-stat-sub {
      display: none;
    }
    .veh-actions {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
    }
    @media (min-width: 1024px) {
      .veh-top {
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 14px;
        padding: 0;
        border: none;
        background: transparent;
        box-shadow: none;
        overflow: visible;
      }
      .veh-top::before {
        display: none;
      }
      .veh-id,
      .veh-stat {
        grid-column: auto;
        display: flex;
        flex-direction: column;
        align-items: stretch;
        position: relative;
        padding: 15px 16px;
        border: 1px solid var(--fo-border);
        border-radius: var(--fo-radius);
        background: var(--fo-surface);
        box-shadow: var(--fo-shadow);
        text-align: left;
      }
      .veh-stat-k {
        order: -1;
        font-size: 11.5px;
        font-weight: 500;
        margin: 0;
      }
      .veh-stat-v {
        font-size: 24px;
        letter-spacing: -0.03em;
        margin-top: 4px;
      }
      .veh-stat-sub {
        display: block;
        font-size: 11px;
        color: var(--fo-text-muted);
        margin-top: 4px;
      }
      .veh-actions {
        display: flex;
        justify-content: flex-end;
      }
    }
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
