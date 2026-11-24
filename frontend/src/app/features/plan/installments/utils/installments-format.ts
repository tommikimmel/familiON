import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../../../../core/api/problem-detail';
import { toNumber } from '../../../../shared/utils/decimal';
import { CurrencyPendingTotal, InstallmentCommitment } from '../data-access/installments.models';

const LOCALE = 'es-AR';

const shortDateFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

const shortMonthFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'short',
  timeZone: 'UTC',
});

const longMonthYearFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

const pctFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

export interface CommitmentBarBucket {
  month: string;
  shortLabel: string;
  fullLabel: string;
  amount: number;
  heightPct: number;
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

export function formatIsoDate(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '';
  }
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return isoDate;
  }
  const date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
  return shortDateFormatter.format(date);
}

export function formatSurchargePct(raw: string | number | null | undefined): string {
  const value = typeof raw === 'number' ? raw : toNumber(raw);
  return `${pctFormatter.format(value)}%`;
}

export function formatTodayIso(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatYearMonth(year: number, monthIndexZeroBased: number): string {
  const normalizedDate = new Date(Date.UTC(year, monthIndexZeroBased, 1));
  const y = normalizedDate.getUTCFullYear();
  const m = String(normalizedDate.getUTCMonth() + 1).padStart(2, '0');
  return `${y}-${m}`;
}

export function parseYearMonth(ym: string): { year: number; monthIndex: number } | null {
  const match = /^(\d{4})-(\d{2})$/.exec(ym.trim());
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const monthIndex = Number(match[2]) - 1;
  if (!Number.isFinite(year) || monthIndex < 0 || monthIndex > 11) {
    return null;
  }
  return { year, monthIndex };
}

function capitalize(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function formatYearMonthShort(ym: string): string {
  const parsed = parseYearMonth(ym);
  if (!parsed) {
    return ym;
  }
  const date = new Date(Date.UTC(parsed.year, parsed.monthIndex, 1));
  return shortMonthFormatter.format(date).replace(/\.$/, '');
}

export function formatYearMonthLong(ym: string): string {
  const parsed = parseYearMonth(ym);
  if (!parsed) {
    return ym;
  }
  const date = new Date(Date.UTC(parsed.year, parsed.monthIndex, 1));
  return capitalize(longMonthYearFormatter.format(date));
}

export function buildCommitmentBuckets(
  items: readonly InstallmentCommitment[],
  currencyCode: string,
  months = 12,
  referenceDate: Date = new Date(),
): CommitmentBarBucket[] {
  const normalizedCurrency = currencyCode.trim().toUpperCase();
  const byMonth = new Map<string, number>();

  for (const item of items) {
    if (item.currencyCode.trim().toUpperCase() !== normalizedCurrency) {
      continue;
    }
    const prev = byMonth.get(item.month) ?? 0;
    byMonth.set(item.month, prev + toNumber(item.amount));
  }

  let startYear = referenceDate.getFullYear();
  let startMonthIndex = referenceDate.getMonth();

  const sortedMonths = Array.from(byMonth.keys()).sort();
  if (sortedMonths.length > 0) {
    const first = parseYearMonth(sortedMonths[0]);
    if (first) {
      const firstVal = first.year * 12 + first.monthIndex;
      const refVal = startYear * 12 + startMonthIndex;
      if (firstVal < refVal) {
        startYear = first.year;
        startMonthIndex = first.monthIndex;
      }
    }
  }

  const rawBuckets: { month: string; amount: number }[] = [];
  let maxAmount = 0;

  for (let i = 0; i < months; i++) {
    const ym = formatYearMonth(startYear, startMonthIndex + i);
    const amount = byMonth.get(ym) ?? 0;
    if (amount > maxAmount) {
      maxAmount = amount;
    }
    rawBuckets.push({ month: ym, amount });
  }

  return rawBuckets.map(({ month, amount }) => ({
    month,
    shortLabel: formatYearMonthShort(month),
    fullLabel: formatYearMonthLong(month),
    amount,
    heightPct: maxAmount > 0 && amount > 0 ? Math.max(8, Math.round((amount / maxAmount) * 100)) : 0,
  }));
}

export function sumCommitmentByCurrency(
  items: readonly InstallmentCommitment[],
): CurrencyPendingTotal[] {
  const totals = new Map<string, number>();
  for (const item of items) {
    const code = item.currencyCode.trim().toUpperCase();
    totals.set(code, (totals.get(code) ?? 0) + toNumber(item.amount));
  }
  return Array.from(totals.entries())
    .filter(([, amount]) => amount > 0)
    .map(([currencyCode, amount]) => ({ currencyCode, amount }));
}
