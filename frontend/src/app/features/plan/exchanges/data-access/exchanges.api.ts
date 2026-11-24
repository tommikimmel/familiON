import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../../core/api/api-config';
import { View } from '../../../../core/state/view-context.store';
import {
  CurrencyPosition,
  Exchange,
  ExchangeCreate,
  ExchangePageResponse,
  IndexValue,
} from './exchanges.models';

@Injectable({ providedIn: 'root' })
export class ExchangesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/currency-exchanges`;

  list(view: View, page = 0, size = 20): Observable<ExchangePageResponse> {
    const params = new HttpParams()
      .set('view', view)
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<ExchangePageResponse>(this.baseUrl, { params });
  }

  positions(view: View): Observable<CurrencyPosition[]> {
    const params = new HttpParams().set('view', view);
    return this.http.get<CurrencyPosition[]>(`${this.baseUrl}/positions`, { params });
  }

  create(payload: ExchangeCreate): Observable<Exchange> {
    return this.http.post<Exchange>(this.baseUrl, payload);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  latestIndices(): Observable<IndexValue[]> {
    return this.http.get<IndexValue[]>(`${API}/economics/indices/latest`);
  }
}
