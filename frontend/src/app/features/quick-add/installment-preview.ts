import { InstallmentAmountMode } from '../../core/finance/transactions.models';
import { toNumber } from '../../shared/utils/decimal';

const LOCALE = 'es-AR';

const monthFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

export interface InstallmentPreviewInput {
  amount: number | string;
  totalInstallments: number;
  amountMode: InstallmentAmountMode;
  purchaseDate: string;
  closingDay?: number | null;
  currencyCode?: string;
}

export interface InstallmentPreviewResult {
  totalInstallments: number;
  perInstallmentAmount: number;
  totalAmount: number;
  firstMonthLabel: string;
  lastMonthLabel: string;
  summary: string;
}

interface ParsedIsoDate {
  year: number;
  monthIndex: number;
  day: number;
}

function roundToTwoDecimals(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function parseIsoDate(isoDate: string): ParsedIsoDate | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate.trim());
  if (!match) {
    return null;
  }
  const year = Number.parseInt(match[1], 10);
  const month = Number.parseInt(match[2], 10);
  const day = Number.parseInt(match[3], 10);
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null;
  }
  return { year, monthIndex: month - 1, day };
}

function addMonths(year: number, monthIndex: number, offset: number): { year: number; monthIndex: number } {
  const totalMonths = year * 12 + monthIndex + offset;
  return {
    year: Math.floor(totalMonths / 12),
    monthIndex: ((totalMonths % 12) + 12) % 12,
  };
}

function formatMonthLabel(year: number, monthIndex: number): string {
  const raw = monthFormatter.format(new Date(Date.UTC(year, monthIndex, 15)));
  const cleaned = raw
    .replace(/\./g, '')
    .replace(/\s+de\s+/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (!cleaned) {
    return '';
  }
  return cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
}

function formatCurrencyValue(value: number, currencyCode: string): string {
  const normalizedCurrency = currencyCode.trim().toUpperCase() || 'ARS';
  const hasFraction = !Number.isInteger(value);
  const fractionDigits = hasFraction ? 2 : 0;

  try {
    return new Intl.NumberFormat(LOCALE, {
      style: 'currency',
      currency: normalizedCurrency,
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    const formattedNumber = new Intl.NumberFormat(LOCALE, {
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: 2,
    }).format(value);
    return `${formattedNumber} ${normalizedCurrency}`;
  }
}

/**
 * Pure calculator for credit-card installment summary and estimated billing window.
 */
export function installmentPreview(input: InstallmentPreviewInput): InstallmentPreviewResult | null {
  const rawAmount =
    typeof input.amount === 'number'
      ? Number.isFinite(input.amount)
        ? input.amount
        : 0
      : toNumber(input.amount);

  const count = Math.trunc(input.totalInstallments);
  if (rawAmount <= 0 || !Number.isFinite(count) || count < 2) {
    return null;
  }

  const parsedDate = parseIsoDate(input.purchaseDate);
  if (!parsedDate) {
    return null;
  }

  const perInstallmentAmount =
    input.amountMode === 'TOTAL'
      ? roundToTwoDecimals(rawAmount / count)
      : roundToTwoDecimals(rawAmount);

  const totalAmount =
    input.amountMode === 'TOTAL'
      ? roundToTwoDecimals(rawAmount)
      : roundToTwoDecimals(rawAmount * count);

  const hasValidClosingDay =
    typeof input.closingDay === 'number' &&
    Number.isInteger(input.closingDay) &&
    input.closingDay >= 1 &&
    input.closingDay <= 31;

  const firstMonthOffset =
    hasValidClosingDay && parsedDate.day > (input.closingDay as number) ? 2 : 1;

  const first = addMonths(parsedDate.year, parsedDate.monthIndex, firstMonthOffset);
  const last = addMonths(parsedDate.year, parsedDate.monthIndex, firstMonthOffset + count - 1);

  const firstMonthLabel = formatMonthLabel(first.year, first.monthIndex);
  const lastMonthLabel = formatMonthLabel(last.year, last.monthIndex);

  const currency = input.currencyCode ?? 'ARS';
  const formattedPerInstallment = formatCurrencyValue(perInstallmentAmount, currency);
  const formattedTotal = formatCurrencyValue(totalAmount, currency);

  const summary = `${count} cuotas de ${formattedPerInstallment}/mes · Total ${formattedTotal} (${firstMonthLabel} → ${lastMonthLabel})`;

  return {
    totalInstallments: count,
    perInstallmentAmount,
    totalAmount,
    firstMonthLabel,
    lastMonthLabel,
    summary,
  };
}

export interface InstallmentSummaryLines {
  /** `6 cuotas de $30.000 / mes` */
  main: string;
  /** Formatted total to pay. */
  total: string;
  /** `Dic 2026 → May 2027` */
  range: string;
  /** Interest line (mock `sum-tag`). */
  tag: string;
}

const MOCK_PREFIXES: Record<string, string> = { ARS: '$', USD: 'US$', EUR: '€' };

/** Mock money style: prefix glued to the amount (`$30.000`), decimals only when present. */
function formatMockMoney(value: number, currencyCode: string): string {
  const code = currencyCode.trim().toUpperCase() || 'ARS';
  const prefix = MOCK_PREFIXES[code];
  if (!prefix) {
    return formatCurrencyValue(value, code);
  }
  const number = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: Number.isInteger(value) ? 0 : 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(value));
  return `${value < 0 ? '-' : ''}${prefix}${number}`;
}

/** Lines of the installments live summary, mirroring the installments mock. */
export function installmentSummaryLines(
  preview: InstallmentPreviewResult,
  options: { currencyCode: string; hasInterest: boolean; cashPrice: string },
): InstallmentSummaryLines {
  const { currencyCode, hasInterest } = options;
  const cash = toNumber(options.cashPrice);
  let tag = 'Sin interés';
  if (hasInterest && cash > 0 && preview.totalAmount > cash) {
    const diff = roundToTwoDecimals(preview.totalAmount - cash);
    const pct = ((diff / cash) * 100).toFixed(1).replace('.', ',');
    tag = `Interés real: +${formatMockMoney(diff, currencyCode)} (+${pct}% sobre contado)`;
  } else if (hasInterest) {
    tag = 'Con interés · completá precio contado para ver el % de recargo';
  }
  return {
    main: `${preview.totalInstallments} cuotas de ${formatMockMoney(preview.perInstallmentAmount, currencyCode)} / mes`,
    total: formatMockMoney(preview.totalAmount, currencyCode),
    range: `${preview.firstMonthLabel} → ${preview.lastMonthLabel}`,
    tag,
  };
}
