import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../../../../core/api/problem-detail';
import { AdjustmentRule } from '../data-access/recurring.models';

const LOCALE = 'es-AR';

const monthYearFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

const dayMonthFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
});

export interface AdjustmentRuleOption {
  value: AdjustmentRule;
  label: string;
  description: string;
}

export const ADJUSTMENT_RULE_OPTIONS: readonly AdjustmentRuleOption[] = [
  {
    value: 'MANUAL',
    label: 'Manual',
    description: 'Monto fijo',
  },
  {
    value: 'IPC',
    label: 'IPC',
    description: 'Se ajusta por inflación',
  },
  {
    value: 'REFERENCE_CURRENCY',
    label: 'Referencia',
    description: 'Está en dólares/UVA',
  },
  {
    value: 'LAST_PAID',
    label: 'Último pago',
    description: 'Usa lo último que pagaste + inflación',
  },
];

export function adjustmentRuleLabel(rule: AdjustmentRule): string {
  return ADJUSTMENT_RULE_OPTIONS.find((opt) => opt.value === rule)?.label ?? rule;
}

export function extractProblemMessage(err: unknown, fallback: string): string {
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

export function currentYearMonth(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

export function formatTodayIso(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function shiftYearMonth(ym: string, delta: -1 | 1): string {
  const match = /^(\d{4})-(\d{2})$/.exec(ym.trim());
  if (!match) {
    return currentYearMonth();
  }
  const year = Number(match[1]);
  const monthIndex = Number(match[2]) - 1;
  const date = new Date(Date.UTC(year, monthIndex + delta, 1));
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

function capitalize(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function formatMonthLabel(ym: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(ym.trim());
  if (!match) {
    return ym;
  }
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1));
  return capitalize(monthYearFormatter.format(date));
}

export function formatShortDayMonth(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '';
  }
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return isoDate;
  }
  const date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
  return dayMonthFormatter.format(date);
}

/**
 * Parses a positive decimal input supporting both dot and comma decimal separators.
 * Returns normalized string (dot separator) or null if invalid / <= 0.
 */
export function parsePositiveDecimalInput(raw: string): string | null {
  const cleaned = raw.trim().replace(/\s+/g, '');
  if (!cleaned) {
    return null;
  }

  let normalized = cleaned;
  if (cleaned.includes(',') && !cleaned.includes('.')) {
    normalized = cleaned.replace(',', '.');
  } else if (cleaned.includes(',') && cleaned.includes('.')) {
    const lastComma = cleaned.lastIndexOf(',');
    const lastDot = cleaned.lastIndexOf('.');
    if (lastComma > lastDot) {
      normalized = cleaned.replace(/\./g, '').replace(',', '.');
    } else {
      normalized = cleaned.replace(/,/g, '');
    }
  }

  if (!/^\d+(\.\d+)?$/.test(normalized)) {
    return null;
  }

  const numeric = Number(normalized);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null;
  }

  return normalized;
}

export function formatEditableDecimal(decimal: string | null | undefined): string {
  if (!decimal) {
    return '';
  }
  const parsed = Number(decimal);
  if (!Number.isFinite(parsed)) {
    return decimal;
  }
  return String(Number(parsed.toFixed(2)));
}
