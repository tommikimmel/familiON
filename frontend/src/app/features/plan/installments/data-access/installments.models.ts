import { Decimal } from '../../../../core/finance/finance.models';

export type InstallmentPlanStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type InstallmentStatus = 'POSTED' | 'PROJECTED' | 'CANCELLED';
export type CancelRemainingMode = 'CANCEL' | 'PREPAY';

export interface InstallmentCategorySummary {
  id: string;
  name: string;
  icon: string | null;
  colorHex: string | null;
}

export interface InstallmentAccountSummary {
  id: string;
  name: string;
}

export interface NextInstallmentSummary {
  number: number;
  date: string;
  amount: Decimal;
}

export interface InstallmentMemberSummary {
  id: string;
  fullName: string;
}

export interface InstallmentPlan {
  id: string;
  description: string;
  category: InstallmentCategorySummary;
  creditCardAccount: InstallmentAccountSummary;
  currencyCode: string;
  totalInstallments: number;
  paidInstallments: number;
  hasInterest: boolean;
  cashPrice: Decimal | null;
  financedTotal: Decimal;
  installmentAmount: Decimal;
  surchargeAmount: Decimal | null;
  surchargePct: Decimal | null;
  purchaseDate: string;
  firstBillingDate: string;
  lastBillingDate: string;
  remainingAmount: Decimal;
  realRemainingAmount: Decimal | null;
  nextInstallment: NextInstallmentSummary | null;
  status: InstallmentPlanStatus;
  attributedTo: InstallmentMemberSummary;
}

export interface InstallmentScheduleItem {
  number: number;
  date: string;
  amount: Decimal;
  status: InstallmentStatus;
  transactionId: string | null;
}

export interface InstallmentPlanDetail extends InstallmentPlan {
  installments: InstallmentScheduleItem[];
}

export interface InstallmentCommitment {
  month: string;
  amount: Decimal;
  currencyCode: string;
}

export interface CancelRemainingPayload {
  mode: CancelRemainingMode;
  prepayDate?: string;
}

export interface CurrencyPendingTotal {
  currencyCode: string;
  amount: number;
}
