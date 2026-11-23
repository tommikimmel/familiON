import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../../../core/api/problem-detail';

export function extractQuickAddProblemMessage(err: unknown, fallback: string): string {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (err.status === 0) {
    return 'No se pudo conectar con el servidor. Verificá tu conexión e intentá de nuevo.';
  }
  const body = err.error as Partial<ProblemDetail> | null | undefined;
  if (body && typeof body === 'object') {
    if (typeof body.detail === 'string' && body.detail.trim().length > 0) {
      return body.detail;
    }
    if (Array.isArray(body.errors) && body.errors.length > 0) {
      const firstMessage = body.errors[0]?.message;
      if (typeof firstMessage === 'string' && firstMessage.trim().length > 0) {
        return firstMessage;
      }
    }
  }
  return fallback;
}
