export type Decimal = string;

export type RecurringMonthStatus = 'PENDING' | 'PAID' | 'OVERDUE' | 'NOT_APPLICABLE';

export interface PlanPositionItem {
  currencyCode: string;
  units: Decimal;
  weightedAvgRate: Decimal | null;
  currentRate: Decimal | null;
  unrealizedResultPct: Decimal | null;
}

export interface PlanNextInstallment {
  number: number;
  date: string;
  amount: Decimal;
}

export interface PlanInstallmentItem {
  id: string;
  description: string;
  totalInstallments: number;
  paidInstallments: number;
  remainingAmount: Decimal;
  currencyCode: string;
  nextInstallment: PlanNextInstallment | null;
}

export interface PlanCommitmentItem {
  month: string;
  amount: Decimal;
  currencyCode: string;
}

export interface PlanRecurringMonth {
  dueDate: string;
  estimatedAmount: Decimal;
  status: RecurringMonthStatus;
}

export interface PlanRecurringItem {
  id: string;
  name: string;
  month: PlanRecurringMonth;
  currencyCode: string;
}

export interface PlanGoalItem {
  id: string;
  name: string;
  progressPct: Decimal;
  targetUnit: string;
  accumulatedInUnit: Decimal;
  targetAmount: Decimal;
}

export interface PlanAllowanceChild {
  id: string;
  fullName: string;
  accentColor: string;
}

export interface PlanAllowanceItem {
  id: string;
  child: PlanAllowanceChild;
  currentAmount: Decimal;
  currencyCode: string;
  nextDisbursementDate: string;
}

export interface PlanPayload {
  positions: PlanPositionItem[] | null;
  installments: PlanInstallmentItem[] | null;
  commitment: PlanCommitmentItem[] | null;
  recurring: PlanRecurringItem[] | null;
  goals: PlanGoalItem[] | null;
  allowances: PlanAllowanceItem[] | null;
}
