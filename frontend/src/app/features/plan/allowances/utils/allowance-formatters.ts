import { AllowanceAdjustmentRule, AllowanceFrequency } from '../data-access/allowances.models';

const DAY_NAMES_WEEKLY: Record<number, string> = {
  1: 'lunes',
  2: 'martes',
  3: 'miércoles',
  4: 'jueves',
  5: 'viernes',
  6: 'sábado',
  7: 'domingo',
};

const MONTH_NAMES_ES = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

export function formatFrequency(frequency: AllowanceFrequency, dayOfPeriod: number): string {
  if (frequency === 'WEEKLY') {
    const day = DAY_NAMES_WEEKLY[dayOfPeriod] ?? `día ${dayOfPeriod}`;
    return `Semanal (los ${day})`;
  }

  return `Mensual (día ${dayOfPeriod} de cada mes)`;
}

export function formatAdjustmentRule(rule: AllowanceAdjustmentRule): string {
  switch (rule) {
    case 'IPC':
      return 'Ajuste por inflación (IPC)';
    case 'REFERENCE_CURRENCY':
      return 'Ajuste por divisa';
    case 'MANUAL':
    default:
      return 'Monto fijo (manual)';
  }
}

export function formatShortDate(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return 'Sin fecha';
  }

  const parts = isoDate.split('-');
  if (parts.length !== 3) {
    return isoDate;
  }

  const year = Number(parts[0]);
  const month = Number(parts[1]) - 1;
  const day = Number(parts[2]);

  if (month < 0 || month > 11 || Number.isNaN(day) || Number.isNaN(year)) {
    return isoDate;
  }

  return `${day} de ${MONTH_NAMES_ES[month]} de ${year}`;
}

export function getInitials(fullName: string | null | undefined): string {
  if (!fullName) {
    return '?';
  }

  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}
