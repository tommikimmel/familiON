import { Lens } from '../state/view-context.store';
import { AccountType, Decimal } from './finance.models';

export type TransactionType = 'EXPENSE' | 'INCOME';
export type InstallmentAmountMode = 'TOTAL' | 'PER_INSTALLMENT';

export interface TransactionCreate {
  type: TransactionType;
  amount: Decimal;
  currencyCode: string;
  transactionDate: string;
  accountId: string;
  categoryId?: string;
  description?: string;
  attributedToUserId?: string;
}

export interface TransactionCategorySummary {
  id: string;
  name: string;
  icon: string | null;
  colorHex: string | null;
}

export interface TransactionAccountSummary {
  id: string;
  name: string;
  accountType: AccountType;
}

export interface TransactionUserSummary {
  id: string;
  fullName: string;
}

export interface TransactionInstallmentSummary {
  planId: string;
  number: number;
  total: number;
}

export interface TransactionItem {
  id: string;
  type: TransactionType;
  scope: string;
  status: string;
  amount: Decimal;
  currencyCode: string;
  transactionDate: string;
  description: string | null;
  notes: string | null;
  category: TransactionCategorySummary | null;
  account: TransactionAccountSummary;
  paidBy: TransactionUserSummary;
  attributedTo: TransactionUserSummary;
  installment: TransactionInstallmentSummary | null;
  lens: Lens | string;
  lensAmount: Decimal;
  lensUnit: string;
  lensApproximated: boolean;
}

export interface InstallmentPlanCreate {
  description: string;
  categoryId: string;
  creditCardAccountId: string;
  currencyCode: string;
  totalInstallments: number;
  amountMode: InstallmentAmountMode;
  amount: Decimal;
  hasInterest: boolean;
  cashPrice?: Decimal;
  purchaseDate: string;
  attributedToUserId?: string;
}
