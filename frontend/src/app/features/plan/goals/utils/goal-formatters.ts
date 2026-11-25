import { toNumber } from '../../../../shared/utils/decimal';
import { TargetUnit } from '../data-access/goals.models';

const LOCALE = 'es-AR';

const numberFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatTargetUnit(unit: TargetUnit): string {
  switch (unit) {
    case 'ARS_IPC':
      return '$ de hoy';
    case 'UVA':
      return 'UVA';
    case 'USD':
      return 'USD';
    case 'EUR':
      return 'EUR';
    case 'ARS':
      return 'ARS';
    default:
      return unit;
  }
}

export function formatUnitAmount(
  value: string | number | null | undefined,
  unit: TargetUnit,
): string {
  const amount = typeof value === 'number' ? (Number.isFinite(value) ? value : 0) : toNumber(value);
  const formattedNumber = numberFormatter.format(amount);

  switch (unit) {
    case 'ARS_IPC':
      return `$ ${formattedNumber} $ de hoy`;
    case 'UVA':
      return `${formattedNumber} UVA`;
    case 'USD':
      return `US$ ${formattedNumber}`;
    case 'EUR':
      return `€ ${formattedNumber}`;
    case 'ARS':
      return `$ ${formattedNumber}`;
    default:
      return `${formattedNumber} ${unit}`;
  }
}

export function formatSuggestedSavings(
  monthsLeft: number | null | undefined,
  suggestedMonthlyInUnit: string | number | null | undefined,
  unit: TargetUnit,
): string | null {
  if (monthsLeft == null || monthsLeft < 0) {
    return null;
  }

  const prefix =
    monthsLeft === 0
      ? 'Vence este mes'
      : monthsLeft === 1
        ? 'Falta 1 mes'
        : `Faltan ${monthsLeft} meses`;

  if (suggestedMonthlyInUnit == null) {
    return prefix;
  }

  const suggestedNumber =
    typeof suggestedMonthlyInUnit === 'number'
      ? suggestedMonthlyInUnit
      : toNumber(suggestedMonthlyInUnit);

  if (suggestedNumber <= 0) {
    return prefix;
  }

  const formattedAmount = formatUnitAmount(suggestedNumber, unit);
  return `${prefix} · Ahorro sugerido: ${formattedAmount} por mes`;
}

export function clampProgressPct(value: string | number | null | undefined): number {
  const num = typeof value === 'number' ? value : toNumber(value);
  if (!Number.isFinite(num) || num < 0) {
    return 0;
  }
  if (num > 100) {
    return 100;
  }
  return Math.round(num * 10) / 10;
}
