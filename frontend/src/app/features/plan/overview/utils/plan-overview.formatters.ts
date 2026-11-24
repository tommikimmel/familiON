import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { toNumber } from '../../../../shared/utils/decimal';
import { Decimal } from '../data-access/plan-overview.models';

const LOCALE = 'es-AR';

const UNITS_FORMATTER = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const PERCENT_FORMATTER = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

const SHORT_DATE_FORMATTER = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
});

const SHORT_MONTH_FORMATTER = new Intl.DateTimeFormat(LOCALE, {
  month: 'short',
  timeZone: 'UTC',
});

const LONG_MONTH_FORMATTER = new Intl.DateTimeFormat(LOCALE, {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

const moneyPipe = new MoneyPipe();

export interface CurrencyTotal {
  currencyCode: string;
  total: number;
}

export function currentLocalYearMonth(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}

export function extractYearMonth(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return currentLocalYearMonth();
  }
  const trimmed = isoDate.trim();
  const match = /^(\d{4}-\d{2})/.exec(trimmed);
  return match ? match[1] : currentLocalYearMonth();
}

function capitalize(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function parseIsoDateUtc(isoDate: string | null | undefined): Date | null {
  if (!isoDate) {
    return null;
  }
  const parts = isoDate.trim().split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return null;
  }
  return new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
}

function parseYearMonthUtc(yearMonth: string | null | undefined): Date | null {
  if (!yearMonth) {
    return null;
  }
  const parts = yearMonth.trim().split('-').map(Number);
  if (parts.length < 2 || !Number.isFinite(parts[0]) || !Number.isFinite(parts[1])) {
    return null;
  }
  return new Date(Date.UTC(parts[0], parts[1] - 1, 1));
}

export function formatShortDate(isoDate: string | null | undefined): string {
  const date = parseIsoDateUtc(isoDate);
  if (!date) {
    return isoDate ?? '';
  }
  return SHORT_DATE_FORMATTER.format(date).replace(/\.$/, '');
}

export function formatYearMonthShort(yearMonth: string | null | undefined): string {
  const date = parseYearMonthUtc(yearMonth);
  if (!date) {
    return yearMonth ?? '';
  }
  return capitalize(SHORT_MONTH_FORMATTER.format(date).replace(/\.$/, ''));
}

export function formatYearMonthLong(yearMonth: string | null | undefined): string {
  const date = parseYearMonthUtc(yearMonth);
  if (!date) {
    return yearMonth ?? '';
  }
  return capitalize(LONG_MONTH_FORMATTER.format(date));
}

export function formatSignedPercent(value: Decimal | number | null | undefined): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  const numeric = typeof value === 'number' ? value : toNumber(value);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  const sign = numeric > 0.04 ? '+' : numeric < -0.04 ? '-' : '';
  return `${sign}${PERCENT_FORMATTER.format(Math.abs(numeric))}%`;
}

export function formatPositionUnits(units: Decimal | number, currencyCode: string): string {
  const numeric = typeof units === 'number' ? units : toNumber(units);
  const code = currencyCode.trim().toUpperCase();
  return `${UNITS_FORMATTER.format(numeric)} ${code}`.trim();
}

export function resolveGoalDisplayUnit(targetUnit: string): { unit: string; adjusted: boolean } {
  const normalized = targetUnit.trim().toUpperCase();
  if (normalized === 'ARS_IPC') {
    return { unit: 'ARS', adjusted: true };
  }
  return { unit: normalized, adjusted: false };
}

export function groupAmountsByCurrency(
  entries: readonly { amount: Decimal; currencyCode: string }[],
  preferredCurrency = 'ARS',
): CurrencyTotal[] {
  const totals = new Map<string, number>();
  for (const entry of entries) {
    const code = entry.currencyCode.trim().toUpperCase() || preferredCurrency;
    totals.set(code, (totals.get(code) ?? 0) + toNumber(entry.amount));
  }

  const preferred = preferredCurrency.trim().toUpperCase();
  return Array.from(totals.entries())
    .map(([currencyCode, total]) => ({ currencyCode, total }))
    .sort((a, b) => {
      if (a.currencyCode === preferred) {
        return -1;
      }
      if (b.currencyCode === preferred) {
        return 1;
      }
      return b.total - a.total;
    });
}

export function formatCurrencyTotals(totals: readonly CurrencyTotal[]): string {
  return totals.map((item) => moneyPipe.transform(item.total, item.currencyCode)).join(' + ');
}

export function getChildInitials(fullName: string): string {
  const parts = fullName
    .trim()
    .split(/\s+/)
    .filter((part) => part.length > 0);
  if (parts.length === 0) {
    return '?';
  }
  if (parts.length === 1) {
    return parts[0].charAt(0).toUpperCase();
  }
  return `${parts[0].charAt(0)}${parts[parts.length - 1].charAt(0)}`.toUpperCase();
}

export function sanitizeAccentHex(hex: string | null | undefined, fallback = '#0F766E'): string {
  if (!hex) {
    return fallback;
  }
  const cleaned = hex.trim();
  return /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(cleaned) ? cleaned : fallback;
}
