import { HttpErrorResponse } from '@angular/common/http';
import { ThemePreference } from '../../../core/api/models/me.model';
import { ProblemDetail } from '../../../core/api/problem-detail';

export interface AccentPreset {
  readonly hex: string;
  readonly label: string;
}

export const ACCENT_PRESETS: readonly AccentPreset[] = [
  { hex: '#0F766E', label: 'Verde petróleo' },
  { hex: '#2563EB', label: 'Azul' },
  { hex: '#7C3AED', label: 'Violeta' },
  { hex: '#DB2777', label: 'Rosa' },
  { hex: '#EA580C', label: 'Naranja' },
  { hex: '#CA8A04', label: 'Ámbar' },
  { hex: '#16A34A', label: 'Verde' },
  { hex: '#475569', label: 'Pizarra' },
];

export interface UpdatePreferencesRequest {
  themePreference?: ThemePreference;
  accentColor?: string;
}

export function normalizeHexColor(value: string | null | undefined): string {
  if (!value) {
    return ACCENT_PRESETS[0].hex;
  }
  const trimmed = value.trim().toUpperCase();
  if (/^#[0-9A-F]{6}$/.test(trimmed)) {
    return trimmed;
  }
  if (/^#[0-9A-F]{3}$/.test(trimmed)) {
    const [r, g, b] = trimmed.slice(1).split('');
    return `#${r}${r}${g}${g}${b}${b}`;
  }
  return ACCENT_PRESETS[0].hex;
}

export function extractApiErrorMessage(err: unknown, fallback: string): string {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (err.status === 0) {
    return 'No se pudo conectar con el servidor. Verifica tu conexión e intenta de nuevo.';
  }
  const body = err.error as Partial<ProblemDetail> | null | undefined;
  if (body && typeof body === 'object') {
    if (typeof body.detail === 'string' && body.detail.trim().length > 0) {
      return body.detail.trim();
    }
    if (Array.isArray(body.errors) && body.errors.length > 0) {
      const first = body.errors[0];
      if (first && typeof first.message === 'string' && first.message.trim().length > 0) {
        return first.message.trim();
      }
    }
  }
  return fallback;
}
