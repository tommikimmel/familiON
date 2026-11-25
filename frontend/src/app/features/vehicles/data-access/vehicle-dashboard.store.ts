import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, Signal, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, firstValueFrom, of, switchMap, tap } from 'rxjs';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { Lens, ViewContextStore } from '../../../core/state/view-context.store';
import { parseProblemError } from '../utils/problem-errors';
import { sortVehicleAlerts } from './vehicle-labels';
import { VehiclesApi } from './vehicles.api';
import {
  FuelLog,
  MaintenanceLog,
  Vehicle,
  VehicleAlert,
  VehicleDashboard,
  VehicleSummary,
} from './vehicles.models';

const DEFAULT_DASHBOARD_ERROR = 'No pudimos cargar el panel del vehículo. Intentá nuevamente.';

export type VehicleEffectiveLens = Exclude<Lens, 'INCOME_PCT'>;

interface DashboardRequest {
  vehicleId: string;
  lens: VehicleEffectiveLens;
}

export function resolveVehicleLens(lens: Lens): VehicleEffectiveLens {
  return lens === 'INCOME_PCT' ? 'NOMINAL' : lens;
}

@Injectable()
export class VehicleDashboardStore {
  private readonly api = inject(VehiclesApi);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _vehicleId = signal<string | null>(null);
  private readonly _dashboard = signal<VehicleDashboard | null>(null);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _notFound = signal<boolean>(false);
  private readonly _deletingFuelId = signal<string | null>(null);
  private readonly _deletingMaintenanceId = signal<string | null>(null);
  private readonly _reloadTick = signal<number>(0);

  private readonly trigger$ = new Subject<DashboardRequest>();

  readonly vehicleId: Signal<string | null> = this._vehicleId.asReadonly();
  readonly dashboard: Signal<VehicleDashboard | null> = this._dashboard.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly notFound: Signal<boolean> = this._notFound.asReadonly();
  readonly deletingFuelId: Signal<string | null> = this._deletingFuelId.asReadonly();
  readonly deletingMaintenanceId: Signal<string | null> = this._deletingMaintenanceId.asReadonly();

  readonly effectiveLens: Signal<VehicleEffectiveLens> = computed(() =>
    resolveVehicleLens(this.viewContext.lens()),
  );

  readonly lensForcedNominal: Signal<boolean> = computed(
    () => this.viewContext.lens() === 'INCOME_PCT',
  );

  readonly vehicle: Signal<Vehicle | null> = computed(() => this._dashboard()?.vehicle ?? null);
  readonly summary: Signal<VehicleSummary | null> = computed(
    () => this._dashboard()?.summary ?? null,
  );
  readonly recentFuelLogs: Signal<FuelLog[] | null> = computed(
    () => this._dashboard()?.recentFuelLogs ?? null,
  );
  readonly recentMaintenance: Signal<MaintenanceLog[] | null> = computed(
    () => this._dashboard()?.recentMaintenance ?? null,
  );
  readonly alerts: Signal<VehicleAlert[] | null> = computed(() => {
    const raw = this._dashboard()?.alerts;
    if (raw == null) {
      return null;
    }
    return sortVehicleAlerts(raw);
  });

  readonly hasDegradedSections: Signal<boolean> = computed(() => {
    const current = this._dashboard();
    if (!current) {
      return false;
    }
    return (
      current.vehicle === null ||
      current.summary === null ||
      current.recentFuelLogs === null ||
      current.recentMaintenance === null
    );
  });

  constructor() {
    this.trigger$
      .pipe(
        switchMap(({ vehicleId, lens }) => {
          this._loading.set(true);
          this._error.set(null);
          this._notFound.set(false);

          return this.api.getDashboard(vehicleId, { lens }).pipe(
            tap((payload) => {
              this._dashboard.set(payload);
              this._loading.set(false);
            }),
            catchError((err: unknown) => {
              if (err instanceof HttpErrorResponse && err.status === 404) {
                this._dashboard.set(null);
                this._notFound.set(true);
                this._error.set(null);
              } else {
                this._error.set(parseProblemError(err, DEFAULT_DASHBOARD_ERROR).message);
              }
              this._loading.set(false);
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();

    effect(() => {
      const id = this._vehicleId();
      const lens = this.effectiveLens();
      this.financeEvents.last();
      this._reloadTick();

      if (id) {
        this.trigger$.next({ vehicleId: id, lens });
      }
    });
  }

  setVehicleId(id: string | null): void {
    if (this._vehicleId() !== id) {
      this._dashboard.set(null);
      this._notFound.set(false);
      this._error.set(null);
    }
    this._vehicleId.set(id);
  }

  reload(): void {
    this._reloadTick.update((tick) => tick + 1);
  }

  async deleteFuelLog(logId: string): Promise<void> {
    const id = this._vehicleId();
    if (!id) {
      return;
    }
    this._deletingFuelId.set(logId);
    try {
      await firstValueFrom(this.api.deleteFuelLog(id, logId));
      this._dashboard.update((current) => {
        if (!current || !current.recentFuelLogs) {
          return current;
        }
        return {
          ...current,
          recentFuelLogs: current.recentFuelLogs.filter((item) => item.id !== logId),
        };
      });
      this.financeEvents.emit('vehicle-changed');
      this.financeEvents.emit('transaction-deleted');
    } finally {
      this._deletingFuelId.set(null);
    }
  }

  async deleteMaintenanceLog(logId: string): Promise<void> {
    const id = this._vehicleId();
    if (!id) {
      return;
    }
    this._deletingMaintenanceId.set(logId);
    try {
      const target = this.recentMaintenance()?.find((item) => item.id === logId) ?? null;
      await firstValueFrom(this.api.deleteMaintenanceLog(id, logId));
      this._dashboard.update((current) => {
        if (!current || !current.recentMaintenance) {
          return current;
        }
        return {
          ...current,
          recentMaintenance: current.recentMaintenance.filter((item) => item.id !== logId),
        };
      });
      this.financeEvents.emit('vehicle-changed');
      if (target?.transactionId) {
        this.financeEvents.emit('transaction-deleted');
      }
    } finally {
      this._deletingMaintenanceId.set(null);
    }
  }
}
