import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../../../core/api/problem-detail';

export interface ParsedProblemError {
  status: number;
  message: string;
  fieldErrors: Record<string, string>;
}

const ODOMETER_PATTERN = /(od[oó]metr|odometer|kilometraje|\bkm\b)/i;

export function parseProblemError(err: unknown, fallback: string): ParsedProblemError {
  if (!(err instanceof HttpErrorResponse)) {
    return { status: 0, message: fallback, fieldErrors: {} };
  }

  if (err.status === 0) {
    return {
      status: 0,
      message: 'No se pudo conectar con el servidor. Verificá tu conexión e intentá de nuevo.',
      fieldErrors: {},
    };
  }

  const body = err.error as Partial<ProblemDetail> | null | undefined;
  const fieldErrors: Record<string, string> = {};
  let message = fallback;

  if (body && typeof body === 'object') {
    if (Array.isArray(body.errors)) {
      for (const item of body.errors) {
        if (
          item &&
          typeof item.field === 'string' &&
          item.field.trim().length > 0 &&
          typeof item.message === 'string' &&
          item.message.trim().length > 0
        ) {
          fieldErrors[item.field.trim()] = item.message.trim();
        }
      }
    }

    if (typeof body.detail === 'string' && body.detail.trim().length > 0) {
      message = body.detail.trim();
    } else if (Object.keys(fieldErrors).length > 0) {
      const firstKey = Object.keys(fieldErrors)[0];
      message = fieldErrors[firstKey];
    } else if (typeof body.title === 'string' && body.title.trim().length > 0) {
      message = body.title.trim();
    }
  }

  // Backend business rules (422) for incoherent odometer may arrive either in `errors`
  // or as a top-level RFC 7807 `detail`. Attach it to `odometerKm` so the field displays it.
  if (
    err.status === 422 &&
    !fieldErrors['odometerKm'] &&
    !fieldErrors['currentOdometerKm'] &&
    ODOMETER_PATTERN.test(message)
  ) {
    fieldErrors['odometerKm'] = message;
    fieldErrors['currentOdometerKm'] = message;
  }

  return {
    status: err.status,
    message,
    fieldErrors,
  };
}
