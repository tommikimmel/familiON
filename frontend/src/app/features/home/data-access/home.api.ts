import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API, BFF_BASE } from '../../../core/api/api-config';
import { ViewContextQueryParams } from '../../../core/state/view-context.store';
import { HomePayload, Series } from './home.models';

@Injectable({ providedIn: 'root' })
export class HomeApi {
  private readonly http = inject(HttpClient);

  getHome(params: ViewContextQueryParams): Observable<HomePayload> {
    const httpParams = new HttpParams()
      .set('view', params.view)
      .set('lens', params.lens)
      .set('granularity', params.granularity)
      .set('date', params.date);

    return this.http.get<HomePayload>(`${BFF_BASE}/home`, { params: httpParams });
  }

  getSeries(params: Pick<ViewContextQueryParams, 'view' | 'lens' | 'date'>, periods = 6): Observable<Series> {
    const httpParams = new HttpParams()
      .set('view', params.view)
      .set('lens', params.lens)
      .set('granularity', 'MONTH')
      .set('periods', String(periods))
      .set('date', params.date);

    return this.http.get<Series>(`${API}/analytics/series`, { params: httpParams });
  }
}
