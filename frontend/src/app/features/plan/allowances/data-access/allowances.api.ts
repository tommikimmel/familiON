import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../../core/api/api-config';
import {
  AccountOption,
  Allowance,
  AllowanceCreate,
  AllowancePatch,
  DisbursePayload,
  DisburseResult,
  FamilyMemberSummary,
} from './allowances.models';

@Injectable({ providedIn: 'root' })
export class AllowancesApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/family/allowances`;

  list(): Observable<Allowance[]> {
    return this.http.get<Allowance[]>(this.baseUrl);
  }

  create(payload: AllowanceCreate): Observable<Allowance> {
    return this.http.post<Allowance>(this.baseUrl, payload);
  }

  update(id: string, patch: AllowancePatch): Observable<Allowance> {
    return this.http.patch<Allowance>(`${this.baseUrl}/${encodeURIComponent(id)}`, patch);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }

  disburse(id: string, payload: DisbursePayload): Observable<DisburseResult> {
    return this.http.post<DisburseResult>(
      `${this.baseUrl}/${encodeURIComponent(id)}/disburse`,
      payload,
    );
  }

  members(): Observable<FamilyMemberSummary[]> {
    return this.http.get<FamilyMemberSummary[]>(`${API}/family/members`);
  }

  familyAccounts(): Observable<AccountOption[]> {
    const params = new HttpParams().set('view', 'FAMILY');
    return this.http.get<AccountOption[]>(`${API}/accounts`, { params });
  }
}
