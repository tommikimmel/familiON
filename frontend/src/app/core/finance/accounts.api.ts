import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../api/api-config';
import { View } from '../state/view-context.store';
import { Account, AccountCreate } from './finance.models';

@Injectable({ providedIn: 'root' })
export class AccountsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/accounts`;

  list(view: View): Observable<Account[]> {
    const params = new HttpParams().set('view', view);
    return this.http.get<Account[]>(this.baseUrl, { params });
  }

  create(payload: AccountCreate): Observable<Account> {
    return this.http.post<Account>(this.baseUrl, payload);
  }

  update(id: string, patch: Partial<AccountCreate>): Observable<Account> {
    return this.http.patch<Account>(`${this.baseUrl}/${encodeURIComponent(id)}`, patch);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
}
