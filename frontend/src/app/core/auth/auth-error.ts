import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../api/problem-detail';

export interface ParsedAuthError {
  status: number;
  generalMessage: string | null;
  fieldErrors: Record<string, string>;
  retryAfterSeconds: number | null;
}

const DEFAULT_FALLBACK_MESSAGE = 'Ocurrió un error inesperado. Intenta de nuevo.';

export function parseRetryAfterSeconds(header: string | null | undefined): number | null {
  if (!header) {
    return null;
  }
  const trimmed = header.trim();
  if (/^\d+$/.test(trimmed)) {
    const seconds = Number.parseInt(trimmed, 10);
    return seconds > 0 ? seconds : 1;
  }
  const parsedDate = Date.parse(trimmed);
  if (!Number.isNaN(parsedDate)) {
    const diffSeconds = Math.ceil((parsedDate - Date.now()) / 1000);
    return Math.max(1, diffSeconds);
  }
  return null;
}

export function formatTooManyAttemptsMessage(retryAfterSeconds: number | null): string {
  const minutes = Math.max(1, Math.ceil((retryAfterSeconds ?? 60) / 60));
  const unit = minutes === 1 ? 'minuto' : 'minutos';
  return `Demasiados intentos. Intenta de nuevo en ${minutes} ${unit}.`;
}

function extractProblemDetail(errorBody: unknown): Partial<ProblemDetail> | null {
  if (!errorBody || typeof errorBody !== 'object') {
    return null;
  }
  return errorBody as Partial<ProblemDetail>;
}

export function parseAuthError(
  err: unknown,
  fallbackMessage = DEFAULT_FALLBACK_MESSAGE,
): ParsedAuthError {
  if (!(err instanceof HttpErrorResponse)) {
    return {
      status: 0,
      generalMessage: fallbackMessage,
      fieldErrors: {},
      retryAfterSeconds: null,
    };
  }

  const status = err.status;
  const retryAfterSeconds = parseRetryAfterSeconds(err.headers?.get('Retry-After'));
  const problem = extractProblemDetail(err.error);
  const fieldErrors: Record<string, string> = {};

  if (Array.isArray(problem?.errors)) {
    for (const entry of problem.errors) {
      if (
        entry &&
        typeof entry.field === 'string' &&
        typeof entry.message === 'string' &&
        entry.field.trim().length > 0 &&
        !(entry.field in fieldErrors)
      ) {
        fieldErrors[entry.field] = entry.message;
      }
    }
  }

  const hasFieldErrors = Object.keys(fieldErrors).length > 0;
  let generalMessage: string | null = null;

  if (status === 0) {
    generalMessage = 'No se pudo conectar con el servidor. Verifica tu conexión e intenta de nuevo.';
  } else if (typeof problem?.detail === 'string' && problem.detail.trim().length > 0) {
    generalMessage = hasFieldErrors ? null : problem.detail;
  } else if (!hasFieldErrors) {
    generalMessage = fallbackMessage;
  }

  return {
    status,
    generalMessage,
    fieldErrors,
    retryAfterSeconds,
  };
}
