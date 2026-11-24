export type Decimal = string;

export type ExchangeOperation = 'BUY' | 'SELL' | 'SWAP';

export type ExchangeMode = 'BUY' | 'SELL';

export interface ExchangeCreate {
  sourceAccountId: string;
  targetAccountId: string;
  sourceAmount: Decimal;
  targetAmount: Decimal;
  appliedExchangeRate?: Decimal;
  feeAmount?: Decimal;
  feeCurrency?: string;
  exchangeDate?: string;
  notes?: string;
}

export interface ExchangeAccountSummary {
  id: string;
  name: string;
}

export interface ExchangeUserSummary {
  id: string;
  fullName: string;
}

export interface Exchange {
  id: string;
  exchangeDate: string;
  sourceAccount: ExchangeAccountSummary;
  targetAccount: ExchangeAccountSummary;
  sourceAmount: Decimal;
  sourceCurrency: string;
  targetAmount: Decimal;
  targetCurrency: string;
  appliedExchangeRate: Decimal;
  feeAmount: Decimal;
  feeCurrency: string | null;
  notes: string | null;
  operation: ExchangeOperation;
  createdBy: ExchangeUserSummary;
}

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface ExchangePageResponse {
  content: Exchange[];
  page: PageMetadata;
}

export interface CurrencyPosition {
  currencyCode: string;
  units: Decimal;
  weightedAvgRate: Decimal | null;
  totalCostBase: Decimal;
  currentRate: Decimal | null;
  currentValueBase: Decimal | null;
  unrealizedResultBase: Decimal | null;
  unrealizedResultPct: Decimal | null;
}

export interface IndexValue {
  indexCode: string;
  effectiveDate: string;
  value: Decimal;
  source: string;
}
