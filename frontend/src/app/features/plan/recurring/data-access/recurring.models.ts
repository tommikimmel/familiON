import { Decimal } from '../../../../core/finance/finance.models';

export type RecurringScope = 'PERSONAL' | 'FAMILY';
export type AdjustmentRule = 'MANUAL' | 'IPC' | 'REFERENCE_CURRENCY' | 'LAST_PAID';
export type RecurringMonthStatus = 'PENDING' | 'PAID' | 'OVERDUE' | 'NOT_APPLICABLE';

export interface RecurringCategorySummary {
  id: string;
  name: string;
  icon: string | null;
  colorHex: string | null;
}

export interface RecurringAccountSummary {
  id: string;
  name: string;
}

export interface RecurringMonthInfo {
  month: string;
  dueDate: string;
  estimatedAmount: Decimal;
  status: RecurringMonthStatus;
  paidTransactionId: string | null;
  paidAmount: Decimal | null;
  paidDate: string | null;
}

export interface RecurringExpense {
  id: string;
  name: string;
  category: RecurringCategorySummary;
  account: RecurringAccountSummary | null;
  scope: RecurringScope;
  baseAmount: Decimal;
  currencyCode: string;
  dueDay: number;
  adjustmentRule: AdjustmentRule;
  startDate: string;
  endDate: string | null;
  isActive: boolean;
  month: RecurringMonthInfo;
}

export interface RecurringExpenseCreate {
  name: string;
  categoryId: string;
  accountId?: string;
  scope?: RecurringScope;
  baseAmount: Decimal;
  currencyCode: string;
  dueDay: number;
  adjustmentRule: AdjustmentRule;
  startDate: string;
  endDate?: string;
}

export interface RecurringExpensePatch {
  name?: string;
  categoryId?: string;
  accountId?: string | null;
  scope?: RecurringScope;
  baseAmount?: Decimal;
  currencyCode?: string;
  dueDay?: number;
  adjustmentRule?: AdjustmentRule;
  startDate?: string;
  endDate?: string | null;
  isActive?: boolean;
}

export interface ConfirmPaymentPayload {
  month: string;
  amount: Decimal;
  accountId?: string;
  paymentDate?: string;
}

export interface RecurringCurrencySummary {
  currencyCode: string;
  estimatedTotal: number;
  paidTotal: number;
  pendingTotal: number;
}
