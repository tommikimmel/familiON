import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ReceiptsApi } from '../../../core/finance/receipts.api';
import {
  CanvasLike,
  RECEIPT_JPEG_QUALITY,
  RECEIPT_RESIZE_THRESHOLD_BYTES,
  computeScaledDimensions,
  prepareReceiptFile,
  toJpegFilename,
} from './receipt-image';

function makeFileWithSize(name: string, type: string, sizeBytes: number): File {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: sizeBytes });
  return file;
}

describe('receipt-image & ReceiptsApi', () => {
  it('scales dimensions so the longest side is at most 2000 px while preserving aspect ratio', () => {
    expect(computeScaledDimensions(4000, 3000)).toEqual({ width: 2000, height: 1500 });
    expect(computeScaledDimensions(1500, 3000)).toEqual({ width: 1000, height: 2000 });
    expect(computeScaledDimensions(1200, 800)).toEqual({ width: 1200, height: 800 });
  });

  it('converts original filename to .jpg extension', () => {
    expect(toJpegFilename('ticket.png')).toBe('ticket.jpg');
    expect(toJpegFilename('foto.super.webp')).toBe('foto.super.jpg');
    expect(toJpegFilename('   ')).toBe('receipt.jpg');
  });

  it('resizes images larger than 2 MB on a canvas to max 2000 px and JPEG 0.85', async () => {
    const largeImage = makeFileWithSize(
      'ticket.png',
      'image/png',
      RECEIPT_RESIZE_THRESHOLD_BYTES + 1024,
    );

    const drawImage = vi.fn();
    const cleanup = vi.fn();
    let capturedType: string | undefined;
    let capturedQuality: number | undefined;
    const canvas: CanvasLike = {
      width: 0,
      height: 0,
      getContext: () => ({ drawImage }),
      toBlob: (cb, type, quality) => {
        capturedType = type;
        capturedQuality = quality;
        cb(new Blob(['jpeg-bytes'], { type: 'image/jpeg' }));
      },
    };
    const fakeSource = {} as CanvasImageSource;

    const result = await prepareReceiptFile(largeImage, {
      loadImage: async () => ({
        source: fakeSource,
        width: 4000,
        height: 2000,
        cleanup,
      }),
      createCanvas: () => canvas,
    });

    expect(canvas.width).toBe(2000);
    expect(canvas.height).toBe(1000);
    expect(drawImage).toHaveBeenCalledWith(fakeSource, 0, 0, 2000, 1000);
    expect(capturedType).toBe('image/jpeg');
    expect(capturedQuality).toBe(RECEIPT_JPEG_QUALITY);
    expect(cleanup).toHaveBeenCalledOnce();
    expect(result.name).toBe('ticket.jpg');
    expect(result.type).toBe('image/jpeg');
  });

  it('skips resizing for images <= 2 MB and for PDFs', async () => {
    const smallImage = makeFileWithSize('small.jpg', 'image/jpeg', RECEIPT_RESIZE_THRESHOLD_BYTES);
    const largePdf = makeFileWithSize(
      'factura.pdf',
      'application/pdf',
      RECEIPT_RESIZE_THRESHOLD_BYTES + 500_000,
    );

    const loadImage = vi.fn();
    const createCanvas = vi.fn();

    const smallResult = await prepareReceiptFile(smallImage, { loadImage, createCanvas });
    const pdfResult = await prepareReceiptFile(largePdf, { loadImage, createCanvas });

    expect(smallResult).toBe(smallImage);
    expect(pdfResult).toBe(largePdf);
    expect(loadImage).not.toHaveBeenCalled();
    expect(createCanvas).not.toHaveBeenCalled();
  });

  it('builds multipart FormData with field "file" when calling ReceiptsApi.scan', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    const api = TestBed.inject(ReceiptsApi);
    const http = TestBed.inject(HttpTestingController);

    const file = new File(['img-bytes'], 'ticket.jpg', { type: 'image/jpeg' });
    const promise = firstValueFrom(api.scan(file));

    const req = http.expectOne('/bff/api/receipts/scan');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    const formData = req.request.body as FormData;
    const uploaded = formData.get('file');
    expect(uploaded instanceof File).toBe(true);
    expect((uploaded as File).name).toBe('ticket.jpg');

    req.flush({
      id: 'rec-1',
      status: 'UPLOADED',
      mimeType: 'image/jpeg',
      fileSizeBytes: 9,
      createdAt: '2026-11-25T12:00:00Z',
      draft: null,
      fileUrl: '/api/v1/receipts/rec-1/file',
    });

    const receipt = await promise;
    expect(receipt.id).toBe('rec-1');

    const getPromise = firstValueFrom(api.get('rec-1'));
    const getReq = http.expectOne('/bff/api/receipts/rec-1');
    expect(getReq.request.method).toBe('GET');
    getReq.flush({ ...receipt, status: 'PROCESSING' });

    const polled = await getPromise;
    expect(polled.status).toBe('PROCESSING');
    http.verify();
  });
});
