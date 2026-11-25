import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { parseProblemError } from '../utils/problem-errors';
import { VehiclesApi } from './vehicles.api';
import { Vehicle, VehicleCreate, VehicleUpdate } from './vehicles.models';

const DEFAULT_LOAD_ERROR = 'No pudimos cargar los vehículos. Intentá nuevamente.';

@Injectable({ providedIn: 'root' })
export class VehiclesStore {
  private readonly api = inject(VehiclesApi);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _vehicles = signal<Vehicle[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private loadSeq = 0;

  readonly vehicles: Signal<Vehicle[]> = this._vehicles.asReadonly();
  readonly activeVehicles: Signal<Vehicle[]> = computed(() =>
    this._vehicles().filter((vehicle) => vehicle.isActive !== false),
  );
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  async load(): Promise<void> {
    const requestId = ++this.loadSeq;
    this._loading.set(true);
    this._error.set(null);
    try {
      const list = await firstValueFrom(this.api.list());
      if (requestId === this.loadSeq) {
        this._vehicles.set(list);
      }
    } catch (err: unknown) {
      if (requestId === this.loadSeq) {
        this._error.set(parseProblemError(err, DEFAULT_LOAD_ERROR).message);
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  findCached(id: string): Vehicle | null {
    return this._vehicles().find((vehicle) => vehicle.id === id) ?? null;
  }

  async loadOne(id: string): Promise<Vehicle> {
    const vehicle = await firstValueFrom(this.api.getById(id));
    this.upsertLocal(vehicle);
    return vehicle;
  }

  async create(payload: VehicleCreate): Promise<Vehicle> {
    const created = await firstValueFrom(this.api.create(payload));
    this.upsertLocal(created);
    this.financeEvents.emit('vehicle-changed');
    return created;
  }

  async update(id: string, patch: VehicleUpdate): Promise<Vehicle> {
    const updated = await firstValueFrom(this.api.update(id, patch));
    this.upsertLocal(updated);
    this.financeEvents.emit('vehicle-changed');
    return updated;
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.api.remove(id));
    this._vehicles.update((current) => current.filter((vehicle) => vehicle.id !== id));
    this.financeEvents.emit('vehicle-changed');
  }

  private upsertLocal(vehicle: Vehicle): void {
    this._vehicles.update((current) => {
      const exists = current.some((item) => item.id === vehicle.id);
      if (!exists) {
        return [...current, vehicle];
      }
      return current.map((item) => (item.id === vehicle.id ? vehicle : item));
    });
  }
}
