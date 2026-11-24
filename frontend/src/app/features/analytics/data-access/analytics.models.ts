import { Granularity, Lens, View } from '../../../core/state/view-context.store';

export type Decimal = string;

export type AnalyticsTab = 'OVERVIEW' | 'CALENDAR';

export interface CalendarYearMonth {
  year: number;
  month: number; // 1..12
}

export interface Period {
  granularity: Granularity;
  from: string;
  to: string;
  label: string;
}

export interface AnalyticsSummaryPrevious {
  income: Decimal;
  expense: Decimal;
}

export interface AnalyticsSummary {
  period: Period;
  view: View;
  lens: Lens;
  unit: string;
  income: Decimal;
  expense: Decimal;
  balance: Decimal;
  savingsRatePct: Decimal | null;
  installmentCommitmentPct: Decimal | null;
  previous: AnalyticsSummaryPrevious;
  realVariationPct: Decimal | null;
  lensApproximated: boolean;
}

export interface SeriesPoint {
  periodStart: string;
  label: string;
  income: Decimal;
  expense: Decimal;
  balance: Decimal;
}

export interface Series {
  lens: Lens;
  unit: string;
  granularity: Granularity;
  points: SeriesPoint[];
}

export interface CategorySlice {
  categoryId: string | null;
  name: string;
  colorHex: string | null;
  icon: string | null;
  amount: Decimal;
  pct: Decimal;
  variationPct: Decimal | null;
}

export interface CategoryBreakdown {
  period: Period;
  lens: Lens;
  unit: string;
  total: Decimal;
  slices: CategorySlice[];
}

export interface PersonSlice {
  userId: string;
  fullName: string;
  accentColor: string;
  amount: Decimal;
  pct: Decimal;
}

export interface PersonBreakdown {
  period: Period;
  lens: Lens;
  unit: string;
  total: Decimal;
  slices: PersonSlice[];
}

export type CalendarItemKind =
  | 'TRANSACTION'
  | 'INSTALLMENT'
  | 'RECURRING_DUE'
  | 'VEHICLE_DUE'
  | 'ALLOWANCE';

export interface CalendarItem {
  kind: CalendarItemKind;
  refId: string;
  title: string;
  amount: Decimal | null;
  currencyCode: string | null;
}

export interface CalendarDay {
  date: string;
  income: Decimal;
  expense: Decimal;
  items: CalendarItem[];
}

export interface CalendarView {
  from: string;
  to: string;
  days: CalendarDay[];
}
