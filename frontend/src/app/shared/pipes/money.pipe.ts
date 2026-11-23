import { Pipe, PipeTransform } from '@angular/core';
import { toNumber } from '../utils/decimal';

const NON_ISO_UNITS = new Set(['UVA', 'UVI', '%']);
const LOCALE = 'es-AR';

const numberFormatter = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 2,
});

const currencyFormatters = new Map<string, Intl.NumberFormat>();

function getCurrencyFormatter(currency: string): Intl.NumberFormat | null {
  const existing = currencyFormatters.get(currency);
  if (existing) {
    return existing;
  }
  try {
    const formatter = new Intl.NumberFormat(LOCALE, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    });
    currencyFormatters.set(currency, formatter);
    return formatter;
  } catch {
    return null;
  }
}

@Pipe({
  name: 'money',
  standalone: true,
})
export class MoneyPipe implements PipeTransform {
  transform(value: string | number | null | undefined, currency = 'ARS'): string {
    const amount = typeof value === 'number' ? (Number.isFinite(value) ? value : 0) : toNumber(value);
    const normalizedUnit = currency.trim().toUpperCase();

    if (normalizedUnit === '%') {
      return `${numberFormatter.format(amount)}%`;
    }

    if (NON_ISO_UNITS.has(normalizedUnit)) {
      return `${numberFormatter.format(amount)} ${normalizedUnit}`;
    }

    const formatter = getCurrencyFormatter(normalizedUnit);
    if (formatter) {
      return formatter.format(amount);
    }

    return `${numberFormatter.format(amount)} ${normalizedUnit}`.trim();
  }
}
