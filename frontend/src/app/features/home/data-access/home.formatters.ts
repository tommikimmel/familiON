import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { toNumber } from '../../../shared/utils/decimal';
import { Decimal, IndexValue, TransactionType } from './home.models';

const LOCALE = 'es-AR';

const moneyPipe = new MoneyPipe();

const percentFormatter = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 1,
});

const integerFormatter = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 0,
});

const shortDateFormatter = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
});

const RATE_LABELS: Record<string, string> = {
  USD_MEP: 'Dólar MEP',
  MEP: 'Dólar MEP',
  USD_OFICIAL: 'Dólar Oficial',
  OFICIAL: 'Dólar Oficial',
  USD_BLUE: 'Dólar Blue',
  BLUE: 'Dólar Blue',
  USD_CCL: 'Dólar CCL',
  CCL: 'Dólar CCL',
  USD_TARJETA: 'Dólar Tarjeta',
  EUR_OFICIAL: 'Euro Oficial',
  IPC: 'IPC',
  UVA: 'UVA',
  UVI: 'UVI',
};

export function resolveDisplayUnit(unit: string | null | undefined, lens: Lens): string {
  if (lens === 'INCOME_PCT') {
    return '%';
  }
  const normalized = unit?.trim();
  return normalized && normalized.length > 0 ? normalized : 'ARS';
}

export function formatMoneyValue(
  value: string | number | null | undefined,
  unit: string,
): string {
  return moneyPipe.transform(value, unit);
}

export function formatPercentValue(value: Decimal | number | null | undefined): string {
  const num = typeof value === 'number' ? value : toNumber(value);
  if (!Number.isFinite(num)) {
    return '0%';
  }
  return `${percentFormatter.format(num)}%`;
}

export function formatSignedRealVariation(value: Decimal | null | undefined): string | null {
  if (value === null || value === undefined || value.trim() === '') {
    return null;
  }
  const num = toNumber(value);
  const rounded = Math.round(num * 10) / 10;
  const sign = rounded > 0 ? '+' : '';
  return `${sign}${percentFormatter.format(rounded)}% real`;
}

export function formatRateLabel(indexCode: string): string {
  const normalized = indexCode.trim().toUpperCase();
  return RATE_LABELS[normalized] ?? normalized.replace(/_/g, ' ');
}

export function formatRatePrice(rate: IndexValue, baseCurrency = 'ARS'): string {
  const amount = toNumber(rate.value);
  if (rate.indexCode.toUpperCase() === 'IPC') {
    return `${percentFormatter.format(amount)}%`;
  }
  if (Number.isInteger(amount) || Math.abs(amount - Math.round(amount)) < 0.005) {
    const currencyPrefix = baseCurrency.toUpperCase() === 'ARS' ? '$' : `${baseCurrency} `;
    return `${currencyPrefix}${integerFormatter.format(Math.round(amount))}`;
  }
  return moneyPipe.transform(amount, baseCurrency);
}

export function pickPrimaryRate(
  rates: readonly IndexValue[] | null | undefined,
  preferredSource?: string | null,
): IndexValue | null {
  if (!rates || rates.length === 0) {
    return null;
  }
  if (preferredSource) {
    const normalized = preferredSource.trim().toUpperCase();
    const direct = rates.find(
      (rate) =>
        rate.indexCode.toUpperCase() === normalized ||
        rate.indexCode.toUpperCase() === `USD_${normalized}`,
    );
    if (direct) {
      return direct;
    }
  }
  return (
    rates.find((rate) => rate.indexCode.toUpperCase().startsWith('USD')) ??
    rates[0] ??
    null
  );
}

export function formatShortIsoDate(isoDate: string): string {
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return isoDate;
  }
  const date = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
  return shortDateFormatter.format(date);
}

export function formatCompactAxisValue(value: number, unit: string): string {
  const normalizedUnit = unit.trim().toUpperCase();
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';

  if (normalizedUnit === '%') {
    return `${sign}${percentFormatter.format(abs)}%`;
  }

  const prefix = normalizedUnit === 'ARS' ? '$' : normalizedUnit === 'USD' ? 'US$' : '';
  const suffix = prefix === '' ? ` ${normalizedUnit}` : '';

  if (abs >= 1_000_000) {
    return `${sign}${prefix}${percentFormatter.format(abs / 1_000_000)}M${suffix}`;
  }
  if (abs >= 1_000) {
    return `${sign}${prefix}${percentFormatter.format(abs / 1_000)}k${suffix}`;
  }
  return `${sign}${prefix}${integerFormatter.format(Math.round(abs))}${suffix}`;
}

export function fallbackTransactionIcon(type: TransactionType): string {
  switch (type) {
    case 'INCOME':
      return 'briefcase';
    case 'TRANSFER':
      return 'arrow-left-right';
    default:
      return 'circle-ellipsis';
  }
}

export function transactionTypeLabel(type: TransactionType): string {
  switch (type) {
    case 'INCOME':
      return 'Ingreso';
    case 'TRANSFER':
      return 'Transferencia';
    default:
      return 'Gasto';
  }
}

export function formatSignedTransactionAmount(
  amount: Decimal,
  currencyCode: string,
  type: TransactionType,
): string {
  const formatted = moneyPipe.transform(Math.abs(toNumber(amount)), currencyCode);
  if (type === 'INCOME') {
    return `+${formatted}`;
  }
  if (type === 'EXPENSE') {
    return `-${formatted}`;
  }
  return formatted;
}
