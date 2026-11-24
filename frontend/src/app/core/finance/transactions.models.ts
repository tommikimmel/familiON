import { Lens, View } from '../state/view-context.store';
import { AccountType, Decimal } from './finance.models';

export type TransactionType = 'EXPENSE' | 'INCOME';
export type TransactionItemType =
  | 'EXPENSE'
  | 'INCOME'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'EXCHANGE_OUT'
  | 'EXCHANGE_IN'
  | 'ALLOWANCE_OUT'
  | 'ALLOWANCE_IN';
export type TransactionStatus = 'POSTED' | 'PROJECTED' | 'CANCELLED';
export type TransactionScope = 'PERSONAL' | 'FAMILY';
export type TransactionSortField = 'transactionDate' | 'amount';
export type SortDirection = 'asc' | 'desc';
export type TransactionSort = `${TransactionSortField},${SortDirection}`;
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
  type: TransactionItemType;
  scope: TransactionScope | string;
  status: TransactionStatus | string;
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
  lensAmount: Decimal | null;
  lensUnit: string | null;
  lensApproximated: boolean;
}

export interface TransactionListParams {
  view: View;
  lens: Lens;
  from?: string | null;
  to?: string | null;
  type?: readonly TransactionItemType[] | null;
  categoryId?: string | null;
  accountId?: string | null;
  attributedToUserId?: string | null;
  currency?: string | null;
  status?: readonly TransactionStatus[] | null;
  q?: string | null;
  page?: number;
  size?: number;
  sort?: string | null;
}

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface TransactionPage {
  content: TransactionItem[];
  page: PageMetadata;
}

export interface TransactionPatch {
  amount?: Decimal;
  transactionDate?: string;
  categoryId?: string | null;
  description?: string | null;
  notes?: string | null;
  accountId?: string;
  attributedToUserId?: string;
  scope?: TransactionScope;
}

export interface TransactionGetOptions {
  lens?: Lens;
  view?: View;
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
