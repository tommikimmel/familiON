import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../api/api-config';
import {
  InstallmentPlanCreate,
  TransactionCreate,
  TransactionGetOptions,
  TransactionItem,
  TransactionListParams,
  TransactionPage,
  TransactionPatch,
} from './transactions.models';

const MAX_PAGE_SIZE = 50;

@Injectable({ providedIn: 'root' })
export class TransactionsApi {
  private readonly http = inject(HttpClient);
  private readonly transactionsUrl = `${API}/transactions`;
  private readonly installmentPlansUrl = `${API}/installment-plans`;

  list(params: TransactionListParams): Observable<TransactionPage> {
    let httpParams = new HttpParams()
      .set('view', params.view)
      .set('lens', params.lens);

    if (params.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to) {
      httpParams = httpParams.set('to', params.to);
    }
    if (params.type && params.type.length > 0) {
      for (const itemType of params.type) {
        httpParams = httpParams.append('type', itemType);
      }
    }
    if (params.categoryId) {
      httpParams = httpParams.set('categoryId', params.categoryId);
    }
    if (params.accountId) {
      httpParams = httpParams.set('accountId', params.accountId);
    }
    if (params.attributedToUserId) {
      httpParams = httpParams.set('attributedToUserId', params.attributedToUserId);
    }
    if (params.currency && params.currency.trim().length > 0) {
      httpParams = httpParams.set('currency', params.currency.trim().toUpperCase());
    }
    if (params.status && params.status.length > 0) {
      for (const statusItem of params.status) {
        httpParams = httpParams.append('status', statusItem);
      }
    }
    if (params.q && params.q.trim().length > 0) {
      httpParams = httpParams.set('q', params.q.trim());
    }
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', String(Math.max(0, Math.floor(params.page))));
    }
    if (params.size !== undefined) {
      const clampedSize = Math.min(MAX_PAGE_SIZE, Math.max(1, Math.floor(params.size)));
      httpParams = httpParams.set('size', String(clampedSize));
    }
    if (params.sort && params.sort.trim().length > 0) {
      httpParams = httpParams.set('sort', params.sort.trim());
    }

    return this.http.get<TransactionPage>(this.transactionsUrl, { params: httpParams });
  }

  get(id: string, options?: TransactionGetOptions): Observable<TransactionItem> {
    let httpParams = new HttpParams();
    if (options?.lens) {
      httpParams = httpParams.set('lens', options.lens);
    }
    if (options?.view) {
      httpParams = httpParams.set('view', options.view);
    }
    return this.http.get<TransactionItem>(`${this.transactionsUrl}/${encodeURIComponent(id)}`, {
      params: httpParams,
    });
  }

  create(body: TransactionCreate): Observable<TransactionItem> {
    return this.http.post<TransactionItem>(this.transactionsUrl, body);
  }

  update(id: string, patch: TransactionPatch): Observable<TransactionItem> {
    return this.http.patch<TransactionItem>(
      `${this.transactionsUrl}/${encodeURIComponent(id)}`,
      patch,
    );
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.transactionsUrl}/${encodeURIComponent(id)}`);
  }

  createInstallmentPlan(body: InstallmentPlanCreate): Observable<unknown> {
    return this.http.post<unknown>(this.installmentPlansUrl, body);
  }
}
