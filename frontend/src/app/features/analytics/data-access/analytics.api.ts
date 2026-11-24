import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../core/api/api-config';
import { View, ViewContextQueryParams } from '../../../core/state/view-context.store';
import {
  AnalyticsSummary,
  CalendarView,
  CategoryBreakdown,
  PersonBreakdown,
  Series,
} from './analytics.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsApi {
  private readonly http = inject(HttpClient);

  getSummary(params: ViewContextQueryParams): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>(`${API}/analytics/summary`, {
      params: this.toContextParams(params),
    });
  }

  getSeries(params: ViewContextQueryParams, periods = 12): Observable<Series> {
    const httpParams = this.toContextParams(params).set('periods', String(periods));
    return this.http.get<Series>(`${API}/analytics/series`, { params: httpParams });
  }

  getByCategory(params: ViewContextQueryParams): Observable<CategoryBreakdown> {
    return this.http.get<CategoryBreakdown>(`${API}/analytics/by-category`, {
      params: this.toContextParams(params),
    });
  }

  getByPerson(params: ViewContextQueryParams): Observable<PersonBreakdown> {
    return this.http.get<PersonBreakdown>(`${API}/analytics/by-person`, {
      params: this.toContextParams(params),
    });
  }

  getCalendar(view: View, from: string, to: string): Observable<CalendarView> {
    const httpParams = new HttpParams().set('view', view).set('from', from).set('to', to);
    return this.http.get<CalendarView>(`${API}/analytics/calendar`, { params: httpParams });
  }

  private toContextParams(params: ViewContextQueryParams): HttpParams {
    return new HttpParams()
      .set('view', params.view)
      .set('lens', params.lens)
      .set('granularity', params.granularity)
      .set('date', params.date);
  }
}
