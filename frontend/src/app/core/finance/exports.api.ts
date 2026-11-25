import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import type { Lens, View } from '../state/view-context.store';
import { API } from '../api/api-config';

export type ExportFormat = 'CSV' | 'XLSX';

export interface TransactionsExportQuery {
  format: ExportFormat;
  from: string;
  to: string;
  view: View;
  lens: Lens;
}

export interface MonthlySummaryExportQuery {
  format: ExportFormat;
  year: number;
  view: View;
  lens: Lens;
}

export interface ExportFile {
  blob: Blob;
  filename: string;
}

const EXTENSION_BY_FORMAT: Record<ExportFormat, string> = {
  CSV: 'csv',
  XLSX: 'xlsx',
};

/**
 * Extracts the file name from a Content-Disposition header.
 * Prefers the RFC 5987 `filename*` parameter and falls back to `filename`.
 */
export function filenameFromContentDisposition(header: string | null): string | null {
  if (!header) {
    return null;
  }

  const extended = /filename\*\s*=\s*([^;]+)/i.exec(header);
  if (extended) {
    const value = extended[1].trim().replace(/^"(.*)"$/, '$1');
    const encoded = value.includes("''") ? value.slice(value.indexOf("''") + 2) : value;
    try {
      const decoded = decodeURIComponent(encoded).trim();
      if (decoded) {
        return decoded;
      }
    } catch {
      // Malformed percent-encoding: fall back to the plain filename parameter.
    }
  }

  const plain = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(header);
  if (plain) {
    const value = (plain[2] ?? plain[1]).trim();
    return value || null;
  }

  return null;
}

/** Triggers a browser download for the given blob using a temporary anchor element. */
export function saveExportFile(file: ExportFile, doc: Document = document): void {
  const url = URL.createObjectURL(file.blob);
  const anchor = doc.createElement('a');
  anchor.href = url;
  anchor.download = file.filename;
  anchor.rel = 'noopener';
  anchor.style.display = 'none';
  doc.body.appendChild(anchor);
  try {
    anchor.click();
  } finally {
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

@Injectable({ providedIn: 'root' })
export class ExportsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/exports`;

  transactions(query: TransactionsExportQuery): Observable<ExportFile> {
    const params = new HttpParams()
      .set('format', query.format)
      .set('from', query.from)
      .set('to', query.to)
      .set('view', query.view)
      .set('lens', query.lens);
    const fallback = `movimientos_${query.from}_${query.to}.${EXTENSION_BY_FORMAT[query.format]}`;
    return this.download(`${this.baseUrl}/transactions`, params, fallback);
  }

  monthlySummary(query: MonthlySummaryExportQuery): Observable<ExportFile> {
    const params = new HttpParams()
      .set('format', query.format)
      .set('year', String(query.year))
      .set('view', query.view)
      .set('lens', query.lens);
    const fallback = `resumen-mensual_${query.year}.${EXTENSION_BY_FORMAT[query.format]}`;
    return this.download(`${this.baseUrl}/monthly-summary`, params, fallback);
  }

  private download(url: string, params: HttpParams, fallbackName: string): Observable<ExportFile> {
    return this.http
      .get(url, { params, responseType: 'blob', observe: 'response' })
      .pipe(map((response: HttpResponse<Blob>) => toExportFile(response, fallbackName)));
  }
}

function toExportFile(response: HttpResponse<Blob>, fallbackName: string): ExportFile {
  const filename =
    filenameFromContentDisposition(response.headers.get('Content-Disposition')) ?? fallbackName;
  return { blob: response.body ?? new Blob([]), filename };
}
