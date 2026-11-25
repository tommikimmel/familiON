import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../api/api-config';

export type ReceiptStatus = 'UPLOADED' | 'PROCESSING' | 'PARSED' | 'FAILED';

export interface ReceiptDraft {
  merchant: string | null;
  date: string | null;
  totalAmount: string | number | null;
  currencyCode: string | null;
  suggestedCategoryId: string | null;
  suggestedCategoryKey: string | null;
  confidence: string | number | null;
  rawText: string | null;
}

export interface Receipt {
  id: string;
  status: ReceiptStatus;
  mimeType: string;
  fileSizeBytes: number;
  createdAt: string;
  draft: ReceiptDraft | null;
  fileUrl: string;
}

@Injectable({ providedIn: 'root' })
export class ReceiptsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/receipts`;

  scan(file: Blob | File, filename?: string): Observable<Receipt> {
    const formData = new FormData();
    const resolvedName = filename ?? (file instanceof File ? file.name : 'receipt.jpg');
    formData.append('file', file, resolvedName);
    return this.http.post<Receipt>(`${this.baseUrl}/scan`, formData);
  }

  get(id: string): Observable<Receipt> {
    return this.http.get<Receipt>(`${this.baseUrl}/${encodeURIComponent(id)}`);
  }
}
