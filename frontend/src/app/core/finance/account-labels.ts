import { FoIconName } from '../../shared/ui/icon/icon.component';
import { AccountType } from './finance.models';

export const ACCOUNT_TYPE_LABEL: Record<AccountType, string> = {
  CASH: 'Efectivo',
  BANK: 'Cuenta bancaria',
  WALLET: 'Billetera virtual',
  DEBIT_CARD: 'Tarjeta de débito',
  CREDIT_CARD: 'Tarjeta de crédito',
  SAVINGS: 'Ahorro',
};

export const ACCOUNT_TYPE_ICON: Record<AccountType, FoIconName> = {
  CASH: 'banknote',
  BANK: 'landmark',
  WALLET: 'smartphone',
  DEBIT_CARD: 'wallet',
  CREDIT_CARD: 'credit-card',
  SAVINGS: 'piggy-bank',
};

export const ACCOUNT_TYPE_ORDER: readonly AccountType[] = [
  'CASH',
  'BANK',
  'WALLET',
  'DEBIT_CARD',
  'CREDIT_CARD',
  'SAVINGS',
];
