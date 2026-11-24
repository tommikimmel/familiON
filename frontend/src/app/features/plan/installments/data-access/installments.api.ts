import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../../core/api/api-config';
import { View } from '../../../../core/state/view-context.store';
import {
  CancelRemainingPayload,
  InstallmentCommitment,
  InstallmentPlan,
  InstallmentPlanDetail,
  InstallmentPlanStatus,
} from './installments.models';

@Injectable({ providedIn: 'root' })
export class InstallmentsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/installment-plans`;

  list(view: View, status?: InstallmentPlanStatus): Observable<InstallmentPlan[]> {
    let params = new HttpParams().set('view', view);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<InstallmentPlan[]>(this.baseUrl, { params });
  }

  getById(id: string): Observable<InstallmentPlanDetail> {
    return this.http.get<InstallmentPlanDetail>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  cancelRemaining(id: string, payload: CancelRemainingPayload): Observable<InstallmentPlan> {
    return this.http.post<InstallmentPlan>(
      `${this.baseUrl}/${encodeURIComponent(id)}/cancel-remaining`,
      payload,
    );
  }

  commitment(view: View, months = 12): Observable<InstallmentCommitment[]> {
    const params = new HttpParams().set('months', String(months)).set('view', view);
    return this.http.get<InstallmentCommitment[]>(`${this.baseUrl}/commitment`, { params });
  }
}
