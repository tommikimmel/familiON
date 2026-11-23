import { Granularity, Lens, View } from '../../../core/state/view-context.store';

export type Decimal = string;

export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER';
export type TransactionScope = 'PERSONAL' | 'FAMILY';

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

export interface IndexValue {
  indexCode: string;
  effectiveDate: string;
  value: Decimal;
  source: 'API' | 'MANUAL';
}

export interface TransactionCategoryRef {
  id: string;
  name: string;
  icon: string | null;
  colorHex: string | null;
}

export interface TransactionAccountRef {
  id: string;
  name: string;
  accountType: string;
}

export interface TransactionUserRef {
  id: string;
  fullName: string;
}

export interface TransactionInstallmentRef {
  planId: string;
  number: number;
  total: number;
}

export interface TransactionItem {
  id: string;
  type: TransactionType;
  scope: TransactionScope;
  status: string;
  amount: Decimal;
  currencyCode: string;
  transactionDate: string;
  description: string | null;
  notes: string | null;
  category: TransactionCategoryRef | null;
  account: TransactionAccountRef;
  paidBy: TransactionUserRef;
  attributedTo: TransactionUserRef;
  installment: TransactionInstallmentRef | null;
  lens: Lens;
  lensAmount: Decimal | null;
  lensUnit: string | null;
  lensApproximated: boolean;
}

export interface HomePayload {
  summary: AnalyticsSummary | null;
  byCategory: CategoryBreakdown | null;
  byPerson: PersonBreakdown | null;
  recentTransactions: TransactionItem[] | null;
  latestRates: IndexValue[] | null;
  unreadNotifications: number | null;
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
