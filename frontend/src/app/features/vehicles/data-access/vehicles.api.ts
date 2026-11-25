import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API, BFF_BASE } from '../../../core/api/api-config';
import {
  FuelLog,
  FuelLogCreate,
  MaintenanceLog,
  MaintenanceLogCreate,
  Vehicle,
  VehicleCreate,
  VehicleDashboard,
  VehiclePage,
  VehicleUpdate,
} from './vehicles.models';

export interface VehicleDashboardParams {
  lens: string;
  from?: string;
  to?: string;
}

@Injectable({ providedIn: 'root' })
export class VehiclesApi {
  private readonly http = inject(HttpClient);

  list(): Observable<Vehicle[]> {
    return this.http.get<Vehicle[]>(`${API}/vehicles`);
  }

  getById(id: string): Observable<Vehicle> {
    return this.http.get<Vehicle>(`${API}/vehicles/${encodeURIComponent(id)}`);
  }

  create(payload: VehicleCreate): Observable<Vehicle> {
    return this.http.post<Vehicle>(`${API}/vehicles`, payload);
  }

  update(id: string, patch: VehicleUpdate): Observable<Vehicle> {
    return this.http.patch<Vehicle>(`${API}/vehicles/${encodeURIComponent(id)}`, patch);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/vehicles/${encodeURIComponent(id)}`);
  }

  getDashboard(id: string, query: VehicleDashboardParams): Observable<VehicleDashboard> {
    let params = new HttpParams().set('lens', query.lens);
    if (query.from) {
      params = params.set('from', query.from);
    }
    if (query.to) {
      params = params.set('to', query.to);
    }
    return this.http.get<VehicleDashboard>(
      `${BFF_BASE}/vehicles/${encodeURIComponent(id)}/dashboard`,
      { params },
    );
  }

  listFuelLogs(id: string, page = 0, size = 20): Observable<VehiclePage<FuelLog>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<VehiclePage<FuelLog>>(
      `${API}/vehicles/${encodeURIComponent(id)}/fuel-logs`,
      { params },
    );
  }

  createFuelLog(id: string, payload: FuelLogCreate): Observable<FuelLog> {
    return this.http.post<FuelLog>(
      `${API}/vehicles/${encodeURIComponent(id)}/fuel-logs`,
      payload,
    );
  }

  deleteFuelLog(id: string, logId: string): Observable<void> {
    return this.http.delete<void>(
      `${API}/vehicles/${encodeURIComponent(id)}/fuel-logs/${encodeURIComponent(logId)}`,
    );
  }

  listMaintenanceLogs(id: string, page = 0, size = 20): Observable<VehiclePage<MaintenanceLog>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<VehiclePage<MaintenanceLog>>(
      `${API}/vehicles/${encodeURIComponent(id)}/maintenance-logs`,
      { params },
    );
  }

  createMaintenanceLog(id: string, payload: MaintenanceLogCreate): Observable<MaintenanceLog> {
    return this.http.post<MaintenanceLog>(
      `${API}/vehicles/${encodeURIComponent(id)}/maintenance-logs`,
      payload,
    );
  }

  deleteMaintenanceLog(id: string, logId: string): Observable<void> {
    return this.http.delete<void>(
      `${API}/vehicles/${encodeURIComponent(id)}/maintenance-logs/${encodeURIComponent(logId)}`,
    );
  }
}
