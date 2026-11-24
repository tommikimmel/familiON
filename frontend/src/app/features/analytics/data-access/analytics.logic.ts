import { Granularity, Lens, View } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { FoIconName } from '../../../shared/ui/icon/icon.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  CalendarDay,
  CalendarItem,
  CalendarItemKind,
  CalendarYearMonth,
  Decimal,
} from './analytics.models';

const LOCALE = 'es-AR';
const MINUS_SIGN = '\u2212';

const moneyPipe = new MoneyPipe();

const percentFormatter = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 1,
});

const integerFormatter = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 0,
});

const monthNameFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'long',
  timeZone: 'UTC',
});

const monthYearFormatter = new Intl.DateTimeFormat(LOCALE, {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

const fullDayFormatter = new Intl.DateTimeFormat(LOCALE, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  timeZone: 'UTC',
});

const REFERENCE_CURRENCY_PLURAL: Record<string, string> = {
  USD: 'dólares',
  EUR: 'euros',
  BRL: 'reales',
  ARS: 'pesos',
  UYU: 'pesos uruguayos',
  CLP: 'pesos chilenos',
  GBP: 'libras',
};

export interface LensOptionMeta {
  value: Lens;
  label: string;
  explanation: string;
}

export type ComparisonTone = 'success' | 'danger' | 'neutral';

export interface PeriodComparison {
  pct: number;
  label: string;
  tone: ComparisonTone;
}

export type TrafficLightTone = 'green' | 'amber' | 'red';

export interface RealVariationStatus {
  pct: number;
  tone: TrafficLightTone;
  badge: string;
  message: string;
}

export type InstallmentTone = 'accent' | 'warning' | 'danger';

export interface InstallmentCommitment {
  pct: number;
  barWidth: number;
  tone: InstallmentTone;
  message: string;
}

export interface CalendarItemKindMeta {
  label: string;
  icon: FoIconName;
  isDue: boolean;
}

export interface CalendarGridCell {
  date: string;
  dayNumber: number;
  isToday: boolean;
  income: number;
  expense: number;
  items: readonly CalendarItem[];
  hasDueItems: boolean;
  dueCount: number;
}

export const WEEKDAY_LABELS: readonly string[] = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

function capitalize(value: string): string {
  if (!value) {
    return value;
  }
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function parseIsoParts(isoDate: string): { year: number; month: number; day: number } | null {
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return null;
  }
  return { year: parts[0], month: parts[1], day: parts[2] };
}

function formatIsoUtc(year: number, month: number, day: number): string {
  const y = String(year).padStart(4, '0');
  const m = String(month).padStart(2, '0');
  const d = String(day).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function formatLocalIsoDate(date: Date): string {
  return formatIsoUtc(date.getFullYear(), date.getMonth() + 1, date.getDate());
}

export function getReferenceCurrencyDisplayName(referenceCurrency = 'USD'): string {
  const normalized = referenceCurrency.trim().toUpperCase();
  return REFERENCE_CURRENCY_PLURAL[normalized] ?? (normalized || 'dólares');
}

export function getLensOptions(referenceCurrency = 'USD'): readonly LensOptionMeta[] {
  const refName = getReferenceCurrencyDisplayName(referenceCurrency);
  return [
    {
      value: 'NOMINAL',
      label: 'Pesos del día',
      explanation: 'Montos históricos tal como se registraron, sin ajustar por inflación.',
    },
    {
      value: 'CONSTANT_BASE',
      label: 'A plata de hoy',
      explanation: 'Ajustado por IPC a poder adquisitivo actual para comparar períodos reales.',
    },
    {
      value: 'REFERENCE_CURRENCY',
      label: `En ${refName}`,
      explanation: `Convertido a ${refName} según la cotización vigente de cada fecha.`,
    },
    {
      value: 'INCOME_PCT',
      label: '% del ingreso',
      explanation: 'Cada monto expresado como porcentaje del ingreso total del período.',
    },
  ];
}

export function getLensExplanation(lens: Lens, referenceCurrency = 'USD'): string {
  const found = getLensOptions(referenceCurrency).find((opt) => opt.value === lens);
  return found?.explanation ?? '';
}

export function resolveDisplayUnit(unit: string | null | undefined, lens: Lens): string {
  if (lens === 'INCOME_PCT') {
    return '%';
  }
  const normalized = unit?.trim();
  return normalized && normalized.length > 0 ? normalized : 'ARS';
}

export function formatMoneyValue(value: Decimal | number | null | undefined, unit: string): string {
  return moneyPipe.transform(value, unit);
}

export function formatPercentValue(value: Decimal | number | null | undefined): string {
  const num = typeof value === 'number' ? value : toNumber(value);
  if (!Number.isFinite(num)) {
    return '0%';
  }
  const rounded = Math.round(num * 10) / 10;
  const normalized = Object.is(rounded, -0) ? 0 : rounded;
  return `${percentFormatter.format(normalized)}%`;
}

export function formatSignedPercent(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  const normalized = Object.is(rounded, -0) ? 0 : rounded;
  if (normalized > 0) {
    return `+${percentFormatter.format(normalized)}%`;
  }
  if (normalized < 0) {
    return `${MINUS_SIGN}${percentFormatter.format(Math.abs(normalized))}%`;
  }
  return '0%';
}

export function formatSignedRealVariation(value: Decimal | null | undefined): string | null {
  if (value === null || value === undefined || value.trim() === '') {
    return null;
  }
  const num = toNumber(value);
  return `${formatSignedPercent(num)} real`;
}

export function formatCompactAxisValue(value: number, unit: string): string {
  const normalizedUnit = unit.trim().toUpperCase();
  const abs = Math.abs(value);
  const sign = value < 0 ? MINUS_SIGN : '';

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

export function getPreviousPeriodReferenceLabel(
  granularity: Granularity,
  periodFrom: string,
): string {
  const parts = parseIsoParts(periodFrom);
  switch (granularity) {
    case 'DAY':
      return 'vs. día anterior';
    case 'WEEK':
      return 'vs. semana anterior';
    case 'MONTH': {
      if (!parts) {
        return 'vs. mes anterior';
      }
      const prevDate = new Date(Date.UTC(parts.year, parts.month - 2, 1));
      const monthName = monthNameFormatter.format(prevDate).toLowerCase();
      return `vs. ${monthName}`;
    }
    case 'YEAR': {
      if (!parts) {
        return 'vs. año anterior';
      }
      return `vs. ${parts.year - 1}`;
    }
  }
}

export function buildExpenseComparison(
  currentExpense: Decimal,
  previousExpense: Decimal,
  granularity: Granularity,
  periodFrom: string,
): PeriodComparison | null {
  const prev = toNumber(previousExpense);
  if (prev <= 0) {
    return null;
  }
  const curr = toNumber(currentExpense);
  const pct = ((curr - prev) / prev) * 100;
  const rounded = Math.round(pct * 10) / 10;
  const normalized = Object.is(rounded, -0) ? 0 : rounded;
  const refLabel = getPreviousPeriodReferenceLabel(granularity, periodFrom);
  const tone: ComparisonTone =
    normalized < 0 ? 'success' : normalized > 0 ? 'danger' : 'neutral';

  return {
    pct: normalized,
    label: `Gastos ${formatSignedPercent(normalized)} ${refLabel}`,
    tone,
  };
}

export function buildIncomeComparison(
  currentIncome: Decimal,
  previousIncome: Decimal,
  granularity: Granularity,
  periodFrom: string,
): PeriodComparison | null {
  const prev = toNumber(previousIncome);
  if (prev <= 0) {
    return null;
  }
  const curr = toNumber(currentIncome);
  const pct = ((curr - prev) / prev) * 100;
  const rounded = Math.round(pct * 10) / 10;
  const normalized = Object.is(rounded, -0) ? 0 : rounded;
  const refLabel = getPreviousPeriodReferenceLabel(granularity, periodFrom);
  const tone: ComparisonTone =
    normalized > 0 ? 'success' : normalized < 0 ? 'danger' : 'neutral';

  return {
    pct: normalized,
    label: `Ingresos ${formatSignedPercent(normalized)} ${refLabel}`,
    tone,
  };
}

export function evaluateRealVariation(
  realVariationPct: Decimal | null | undefined,
  granularity: Granularity,
  view: View,
): RealVariationStatus | null {
  if (realVariationPct === null || realVariationPct === undefined || realVariationPct.trim() === '') {
    return null;
  }

  const raw = toNumber(realVariationPct);
  const rounded = Math.round(raw * 10) / 10;
  const pct = Object.is(rounded, -0) ? 0 : rounded;

  let tone: TrafficLightTone;
  let badge: string;
  if (pct <= 0) {
    tone = 'green';
    badge = 'Bajo control';
  } else if (pct <= 10) {
    tone = 'amber';
    badge = 'Atención';
  } else {
    tone = 'red';
    badge = 'Por encima del promedio';
  }

  const periodPrefix: Record<Granularity, string> = {
    DAY: 'Hoy',
    WEEK: 'Esta semana',
    MONTH: 'Este mes',
    YEAR: 'Este año',
  };

  const avgReference: Record<Granularity, string> = {
    DAY: 'los últimos 3 días',
    WEEK: 'las últimas 3 semanas',
    MONTH: 'los últimos 3 meses',
    YEAR: 'los últimos 3 años',
  };

  const verb = view === 'FAMILY' ? 'gastaron' : 'gastaste';

  return {
    pct,
    tone,
    badge,
    message: `${periodPrefix[granularity]} ${verb} ${formatSignedPercent(pct)} real respecto al promedio de ${avgReference[granularity]}`,
  };
}

export function buildInstallmentCommitment(
  installmentCommitmentPct: Decimal | null | undefined,
): InstallmentCommitment | null {
  if (
    installmentCommitmentPct === null ||
    installmentCommitmentPct === undefined ||
    installmentCommitmentPct.trim() === ''
  ) {
    return null;
  }

  const pct = Math.max(0, Math.round(toNumber(installmentCommitmentPct) * 10) / 10);
  const tone: InstallmentTone = pct <= 25 ? 'accent' : pct <= 40 ? 'warning' : 'danger';

  return {
    pct,
    barWidth: Math.min(100, pct),
    tone,
    message: `Ya comprometido en cuotas: ${formatPercentValue(pct)} del ingreso`,
  };
}

export function getEvolutionHeading(granularity: Granularity, count = 12): {
  title: string;
  subtitle: string;
} {
  switch (granularity) {
    case 'DAY':
      return {
        title: `Evolución últimos ${count} días`,
        subtitle: 'Ingresos, gastos y balance diario',
      };
    case 'WEEK':
      return {
        title: `Evolución últimas ${count} semanas`,
        subtitle: 'Ingresos, gastos y balance semanal',
      };
    case 'YEAR':
      return {
        title: `Evolución últimos ${count} años`,
        subtitle: 'Ingresos, gastos y balance anual',
      };
    default:
      return {
        title: `Evolución últimos ${count} meses`,
        subtitle: 'Ingresos, gastos y balance mensual',
      };
  }
}

export function getCalendarItemMeta(kind: CalendarItemKind): CalendarItemKindMeta {
  switch (kind) {
    case 'INSTALLMENT':
      return { label: 'Cuota', icon: 'credit-card', isDue: true };
    case 'RECURRING_DUE':
      return { label: 'Gasto fijo', icon: 'repeat', isDue: true };
    case 'VEHICLE_DUE':
      return { label: 'Vencimiento auto', icon: 'car', isDue: true };
    case 'ALLOWANCE':
      return { label: 'Mensualidad', icon: 'wallet', isDue: true };
    case 'TRANSACTION':
    default:
      return { label: 'Movimiento', icon: 'arrow-left-right', isDue: false };
  }
}

export function isDueCalendarKind(kind: CalendarItemKind): boolean {
  return kind !== 'TRANSACTION';
}

export function shiftCalendarYearMonth(
  current: CalendarYearMonth,
  delta: -1 | 1,
): CalendarYearMonth {
  const total = current.year * 12 + (current.month - 1) + delta;
  const year = Math.floor(total / 12);
  const month = (((total % 12) + 12) % 12) + 1;
  return { year, month };
}

export function getMonthDateRange(year: number, month: number): { from: string; to: string } {
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return {
    from: formatIsoUtc(year, month, 1),
    to: formatIsoUtc(year, month, daysInMonth),
  };
}

export function formatCalendarMonthTitle(year: number, month: number): string {
  const date = new Date(Date.UTC(year, month - 1, 1));
  return capitalize(monthYearFormatter.format(date));
}

export function formatCalendarFullDate(isoDate: string): string {
  const parts = parseIsoParts(isoDate);
  if (!parts) {
    return isoDate;
  }
  const date = new Date(Date.UTC(parts.year, parts.month - 1, parts.day));
  return capitalize(fullDayFormatter.format(date));
}

/**
 * Builds a Monday-first calendar grid (weeks of 7 slots, with `null` for padding cells).
 * Example: November 2026 starts on Sunday 1 -> first row has 6 leading `null` cells.
 */
export function buildCalendarGrid(
  year: number,
  month: number,
  days: readonly CalendarDay[] = [],
  todayIso?: string,
): (CalendarGridCell | null)[][] {
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const firstDayUtc = new Date(Date.UTC(year, month - 1, 1)).getUTCDay(); // 0=Sun..6=Sat
  const leadingEmpty = (firstDayUtc + 6) % 7; // Monday=0 .. Sunday=6

  const byDate = new Map<string, CalendarDay>();
  for (const day of days) {
    byDate.set(day.date, day);
  }

  const flat: (CalendarGridCell | null)[] = [];
  for (let i = 0; i < leadingEmpty; i++) {
    flat.push(null);
  }

  for (let dayNum = 1; dayNum <= daysInMonth; dayNum++) {
    const iso = formatIsoUtc(year, month, dayNum);
    const data = byDate.get(iso);
    const items = data?.items ?? [];
    const dueCount = items.filter((item) => isDueCalendarKind(item.kind)).length;

    flat.push({
      date: iso,
      dayNumber: dayNum,
      isToday: todayIso === iso,
      income: toNumber(data?.income),
      expense: toNumber(data?.expense),
      items,
      hasDueItems: dueCount > 0,
      dueCount,
    });
  }

  const trailingEmpty = (7 - (flat.length % 7)) % 7;
  for (let i = 0; i < trailingEmpty; i++) {
    flat.push(null);
  }

  const weeks: (CalendarGridCell | null)[][] = [];
  for (let i = 0; i < flat.length; i += 7) {
    weeks.push(flat.slice(i, i + 7));
  }

  return weeks;
}
