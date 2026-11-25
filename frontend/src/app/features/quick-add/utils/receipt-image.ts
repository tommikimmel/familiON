export const RECEIPT_RESIZE_THRESHOLD_BYTES = 2 * 1024 * 1024;
export const RECEIPT_MAX_DIMENSION_PX = 2000;
export const RECEIPT_JPEG_QUALITY = 0.85;

export interface LoadedImageSource {
  readonly source: CanvasImageSource;
  readonly width: number;
  readonly height: number;
  readonly cleanup?: () => void;
}

export interface CanvasLike {
  width: number;
  height: number;
  getContext(contextId: '2d'): {
    drawImage(
      image: CanvasImageSource,
      dx: number,
      dy: number,
      dw: number,
      dh: number,
    ): void;
  } | null;
  toBlob(callback: BlobCallback, type?: string, quality?: number): void;
}

export interface ReceiptResizeDeps {
  createCanvas?: () => CanvasLike;
  loadImage?: (file: File) => Promise<LoadedImageSource>;
}

export function computeScaledDimensions(
  width: number,
  height: number,
  maxSide: number = RECEIPT_MAX_DIMENSION_PX,
): { width: number; height: number } {
  if (width <= 0 || height <= 0) {
    return { width: 1, height: 1 };
  }
  const longest = Math.max(width, height);
  if (longest <= maxSide) {
    return { width: Math.round(width), height: Math.round(height) };
  }
  const scale = maxSide / longest;
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

export function toJpegFilename(originalName: string): string {
  const trimmed = originalName.trim();
  if (!trimmed) {
    return 'receipt.jpg';
  }
  const withoutExt = trimmed.replace(/\.[^.]+$/, '');
  return `${withoutExt || 'receipt'}.jpg`;
}

function defaultLoadImage(file: File): Promise<LoadedImageSource> {
  return new Promise<LoadedImageSource>((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      resolve({
        source: img,
        width: img.naturalWidth || img.width,
        height: img.naturalHeight || img.height,
        cleanup: () => URL.revokeObjectURL(objectUrl),
      });
    };
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error('Failed to decode receipt image'));
    };
    img.src = objectUrl;
  });
}

function defaultCreateCanvas(): CanvasLike {
  return document.createElement('canvas');
}

/**
 * Resizes images larger than 2 MB on the client to at most 2000 px on the longest side
 * and encodes as JPEG (quality 0.85). Non-image files (e.g. PDF) and images <= 2 MB
 * are returned untouched.
 */
export async function prepareReceiptFile(
  file: File,
  deps: ReceiptResizeDeps = {},
): Promise<File> {
  if (!file.type.startsWith('image/') || file.size <= RECEIPT_RESIZE_THRESHOLD_BYTES) {
    return file;
  }

  const loadImage = deps.loadImage ?? defaultLoadImage;
  const createCanvas = deps.createCanvas ?? defaultCreateCanvas;

  const loaded = await loadImage(file);
  try {
    const target = computeScaledDimensions(
      loaded.width,
      loaded.height,
      RECEIPT_MAX_DIMENSION_PX,
    );
    const canvas = createCanvas();
    canvas.width = target.width;
    canvas.height = target.height;

    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return file;
    }

    ctx.drawImage(loaded.source, 0, 0, target.width, target.height);

    const resizedBlob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob((blob) => resolve(blob), 'image/jpeg', RECEIPT_JPEG_QUALITY);
    });

    if (!resizedBlob) {
      return file;
    }

    return new File([resizedBlob], toJpegFilename(file.name), {
      type: 'image/jpeg',
      lastModified: file.lastModified,
    });
  } finally {
    loaded.cleanup?.();
  }
}
