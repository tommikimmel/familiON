import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../api/api-config';
import {
  InstallmentPlanCreate,
  TransactionCreate,
  TransactionItem,
} from './transactions.models';

@Injectable({ providedIn: 'root' })
export class TransactionsApi {
  private readonly http = inject(HttpClient);
  private readonly transactionsUrl = `${API}/transactions`;
  private readonly installmentPlansUrl = `${API}/installment-plans`;

  create(body: TransactionCreate): Observable<TransactionItem> {
    return this.http.post<TransactionItem>(this.transactionsUrl, body);
  }

  createInstallmentPlan(body: InstallmentPlanCreate): Observable<unknown> {
    return this.http.post<unknown>(this.installmentPlansUrl, body);
  }
}
