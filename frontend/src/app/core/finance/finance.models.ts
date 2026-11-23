export type Decimal = string;

export type AccountType =
  | 'CASH'
  | 'BANK'
  | 'WALLET'
  | 'DEBIT_CARD'
  | 'CREDIT_CARD'
  | 'SAVINGS';

export interface Account {
  id: string;
  name: string;
  accountType: AccountType;
  currencyCode: string;
  initialBalance: Decimal;
  currentBalance: Decimal;
  ownerUserId: string | null;
  creditLimit: Decimal | null;
  closingDay: number | null;
  dueDay: number | null;
  isActive: boolean;
}

export interface AccountCreate {
  name: string;
  accountType: AccountType;
  currencyCode: string;
  initialBalance?: Decimal;
  ownerUserId?: string | null;
  creditLimit?: Decimal;
  closingDay?: number;
  dueDay?: number;
}

export type CategoryKind = 'EXPENSE' | 'INCOME';

export interface Category {
  id: string;
  parentCategoryId: string | null;
  name: string;
  kind: CategoryKind;
  icon: string | null;
  colorHex: string | null;
  isSystem: boolean;
  systemKey: string | null;
  isActive?: boolean;
}

export interface CategoryCreate {
  name: string;
  kind: CategoryKind;
  parentCategoryId?: string | null;
  icon?: string | null;
  colorHex?: string | null;
}

export interface CategoryUpdate {
  name?: string;
  icon?: string | null;
  colorHex?: string | null;
  isActive?: boolean;
}

export type CategoryNode = Category & { children: Category[] };
