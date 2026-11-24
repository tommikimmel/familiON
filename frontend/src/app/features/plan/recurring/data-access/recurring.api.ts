import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../../core/api/api-config';
import {
  ConfirmPaymentPayload,
  RecurringExpense,
  RecurringExpenseCreate,
  RecurringExpensePatch,
} from './recurring.models';

@Injectable({ providedIn: 'root' })
export class RecurringApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/recurring-expenses`;

  list(month: string): Observable<RecurringExpense[]> {
    const params = new HttpParams().set('month', month);
    return this.http.get<RecurringExpense[]>(this.baseUrl, { params });
  }

  create(payload: RecurringExpenseCreate): Observable<RecurringExpense> {
    return this.http.post<RecurringExpense>(this.baseUrl, payload);
  }

  update(id: string, patch: RecurringExpensePatch): Observable<RecurringExpense> {
    return this.http.patch<RecurringExpense>(
      `${this.baseUrl}/${encodeURIComponent(id)}`,
      patch,
    );
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  confirmPayment(id: string, payload: ConfirmPaymentPayload): Observable<RecurringExpense> {
    return this.http.post<RecurringExpense>(
      `${this.baseUrl}/${encodeURIComponent(id)}/confirm-payment`,
      payload,
    );
  }
}
