import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import {
  ExportsApi,
  filenameFromContentDisposition,
} from '../../../core/finance/exports.api';

describe('transactions export download', () => {
  describe('filenameFromContentDisposition', () => {
    it('reads a quoted filename', () => {
      expect(
        filenameFromContentDisposition('attachment; filename="movimientos_2026-11.csv"'),
      ).toBe('movimientos_2026-11.csv');
    });

    it('reads an unquoted filename', () => {
      expect(filenameFromContentDisposition('attachment; filename=resumen.xlsx')).toBe(
        'resumen.xlsx',
      );
    });

    it('prefers the RFC 5987 filename* parameter', () => {
      expect(
        filenameFromContentDisposition(
          "attachment; filename=\"fallback.csv\"; filename*=UTF-8''movimientos%20a%C3%B1o.csv",
        ),
      ).toBe('movimientos año.csv');
    });

    it('returns null when the header is missing or has no filename', () => {
      expect(filenameFromContentDisposition(null)).toBeNull();
      expect(filenameFromContentDisposition('inline')).toBeNull();
    });
  });

  describe('ExportsApi', () => {
    let api: ExportsApi;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      api = TestBed.inject(ExportsApi);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('uses the Content-Disposition filename for transaction exports', async () => {
      const result = firstValueFrom(
        api.transactions({
          format: 'CSV',
          from: '2026-11-01',
          to: '2026-11-30',
          view: 'PERSONAL',
          lens: 'NOMINAL',
        }),
      );

      const req = httpMock.expectOne((r) => r.url === '/bff/api/exports/transactions');
      expect(req.request.params.toString()).toBe(
        'format=CSV&from=2026-11-01&to=2026-11-30&view=PERSONAL&lens=NOMINAL',
      );
      req.flush(new Blob(['a,b']), {
        headers: { 'Content-Disposition': 'attachment; filename="tx-nov.csv"' },
      });

      const file = await result;
      expect(file.filename).toBe('tx-nov.csv');
      expect(file.blob).toBeInstanceOf(Blob);
    });

    it('falls back to a generated filename when Content-Disposition is absent', async () => {
      const result = firstValueFrom(
        api.monthlySummary({ format: 'XLSX', year: 2026, view: 'FAMILY', lens: 'CONSTANT_BASE' }),
      );

      const req = httpMock.expectOne((r) => r.url === '/bff/api/exports/monthly-summary');
      expect(req.request.params.toString()).toBe(
        'format=XLSX&year=2026&view=FAMILY&lens=CONSTANT_BASE',
      );
      req.flush(new Blob(['x']));

      const file = await result;
      expect(file.filename).toBe('resumen-mensual_2026.xlsx');
    });
  });
});
