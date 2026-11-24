import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BFF_BASE } from '../../../../core/api/api-config';
import { View } from '../../../../core/state/view-context.store';
import { PlanPayload } from './plan-overview.models';

export interface PlanQueryParams {
  view: View;
  month: string;
}

@Injectable({ providedIn: 'root' })
export class PlanOverviewApi {
  private readonly http = inject(HttpClient);

  getPlan(params: PlanQueryParams): Observable<PlanPayload> {
    const httpParams = new HttpParams().set('view', params.view).set('month', params.month);
    return this.http.get<PlanPayload>(`${BFF_BASE}/plan`, { params: httpParams });
  }
}
