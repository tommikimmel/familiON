import { ParamMap, Params } from '@angular/router';
import {
  TransactionItemType,
  TransactionSort,
  TransactionStatus,
} from '../../../core/finance/transactions.models';
import { Granularity } from '../../../core/state/view-context.store';

export type TransactionTypeGroup =
  | 'EXPENSE'
  | 'INCOME'
  | 'TRANSFER'
  | 'EXCHANGE'
  | 'ALLOWANCE';

export type TransactionStatusFilter = 'ALL' | 'POSTED' | 'PROJECTED' | 'CANCELLED';

export interface TypeGroupOption {
  readonly value: TransactionTypeGroup;
  readonly label: string;
  readonly apiTypes: readonly TransactionItemType[];
}

export interface StatusFilterOption {
  readonly value: TransactionStatusFilter;
  readonly label: string;
}

export interface DateRange {
  readonly from: string;
  readonly to: string;
}

export interface TransactionsFilterState {
  readonly from: string | null;
  readonly to: string | null;
  readonly customDateRange: boolean;
  readonly types: readonly TransactionTypeGroup[];
  readonly categoryId: string | null;
  readonly accountId: string | null;
  readonly attributedToUserId: string | null;
  readonly currency: string | null;
  readonly status: TransactionStatusFilter;
  readonly q: string;
  readonly sort: TransactionSort;
}

export const DEFAULT_SORT: TransactionSort = 'transactionDate,desc';

export const TRANSACTION_TYPE_GROUP_OPTIONS: readonly TypeGroupOption[] = [
  { value: 'EXPENSE', label: 'Gastos', apiTypes: ['EXPENSE'] },
  { value: 'INCOME', label: 'Ingresos', apiTypes: ['INCOME'] },
  { value: 'TRANSFER', label: 'Transferencias', apiTypes: ['TRANSFER_OUT', 'TRANSFER_IN'] },
  { value: 'EXCHANGE', label: 'Divisas', apiTypes: ['EXCHANGE_OUT', 'EXCHANGE_IN'] },
  { value: 'ALLOWANCE', label: 'Mensualidades', apiTypes: ['ALLOWANCE_OUT', 'ALLOWANCE_IN'] },
];

export const TRANSACTION_STATUS_OPTIONS: readonly StatusFilterOption[] = [
  { value: 'ALL', label: 'Confirmadas y proyectadas' },
  { value: 'POSTED', label: 'Solo confirmadas' },
  { value: 'PROJECTED', label: 'Proyectadas' },
  { value: 'CANCELLED', label: 'Canceladas' },
];

const VALID_SORTS = new Set<TransactionSort>([
  'transactionDate,desc',
  'transactionDate,asc',
  'amount,desc',
  'amount,asc',
]);

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const TYPE_GROUP_BY_TOKEN: Record<string, TransactionTypeGroup> = {
  EXPENSE: 'EXPENSE',
  INCOME: 'INCOME',
  TRANSFER: 'TRANSFER',
  TRANSFER_OUT: 'TRANSFER',
  TRANSFER_IN: 'TRANSFER',
  EXCHANGE: 'EXCHANGE',
  EXCHANGE_OUT: 'EXCHANGE',
  EXCHANGE_IN: 'EXCHANGE',
  ALLOWANCE: 'ALLOWANCE',
  ALLOWANCE_OUT: 'ALLOWANCE',
  ALLOWANCE_IN: 'ALLOWANCE',
};

const API_TYPES_BY_GROUP: Record<TransactionTypeGroup, readonly TransactionItemType[]> = {
  EXPENSE: ['EXPENSE'],
  INCOME: ['INCOME'],
  TRANSFER: ['TRANSFER_OUT', 'TRANSFER_IN'],
  EXCHANGE: ['EXCHANGE_OUT', 'EXCHANGE_IN'],
  ALLOWANCE: ['ALLOWANCE_OUT', 'ALLOWANCE_IN'],
};

function formatUtcIsoDate(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function isValidIsoDate(value: string | null | undefined): value is string {
  if (!value || !ISO_DATE_RE.test(value)) {
    return false;
  }
  const [y, m, d] = value.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return (
    date.getUTCFullYear() === y &&
    date.getUTCMonth() === m - 1 &&
    date.getUTCDate() === d
  );
}

export function periodRangeFor(anchorDate: string, granularity: Granularity): DateRange {
  const safeIso = isValidIsoDate(anchorDate) ? anchorDate : formatUtcIsoDate(new Date());
  const [year, month, day] = safeIso.split('-').map(Number);

  switch (granularity) {
    case 'DAY':
      return { from: safeIso, to: safeIso };
    case 'WEEK': {
      const anchor = new Date(Date.UTC(year, month - 1, day));
      const dayOfWeek = anchor.getUTCDay();
      const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
      const start = new Date(Date.UTC(year, month - 1, day + mondayOffset));
      const end = new Date(Date.UTC(year, month - 1, day + mondayOffset + 6));
      return { from: formatUtcIsoDate(start), to: formatUtcIsoDate(end) };
    }
    case 'MONTH': {
      const start = new Date(Date.UTC(year, month - 1, 1));
      const end = new Date(Date.UTC(year, month, 0));
      return { from: formatUtcIsoDate(start), to: formatUtcIsoDate(end) };
    }
    case 'YEAR':
      return { from: `${year}-01-01`, to: `${year}-12-31` };
  }
}

export function createDefaultFilterState(defaultRange: DateRange): TransactionsFilterState {
  return {
    from: defaultRange.from,
    to: defaultRange.to,
    customDateRange: false,
    types: [],
    categoryId: null,
    accountId: null,
    attributedToUserId: null,
    currency: null,
    status: 'ALL',
    q: '',
    sort: DEFAULT_SORT,
  };
}

function normalizeOptionalId(value: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed && trimmed.length > 0 ? trimmed : null;
}

function parseTypesFromParams(params: ParamMap): readonly TransactionTypeGroup[] {
  const rawValues = params.getAll('type');
  if (rawValues.length === 0) {
    return [];
  }
  const seen = new Set<TransactionTypeGroup>();
  const result: TransactionTypeGroup[] = [];
  for (const raw of rawValues) {
    for (const token of raw.split(',')) {
      const normalized = token.trim().toUpperCase();
      const group = TYPE_GROUP_BY_TOKEN[normalized];
      if (group && !seen.has(group)) {
        seen.add(group);
        result.push(group);
      }
    }
  }
  return result;
}

function parseStatusFromParams(params: ParamMap): TransactionStatusFilter {
  const raw = params.get('status')?.trim().toUpperCase();
  if (raw === 'POSTED' || raw === 'PROJECTED' || raw === 'CANCELLED') {
    return raw;
  }
  return 'ALL';
}

function parseSortFromParams(params: ParamMap): TransactionSort {
  const raw = params.get('sort')?.trim() as TransactionSort | undefined;
  return raw && VALID_SORTS.has(raw) ? raw : DEFAULT_SORT;
}

export function parseFiltersFromQueryParams(
  params: ParamMap,
  defaultRange: DateRange,
): TransactionsFilterState {
  const rawFrom = params.get('from');
  const rawTo = params.get('to');
  const hasFromParam = isValidIsoDate(rawFrom);
  const hasToParam = isValidIsoDate(rawTo);
  const customDateRange = hasFromParam || hasToParam;

  const rawCurrency = normalizeOptionalId(params.get('currency'));

  return {
    from: customDateRange ? (hasFromParam ? rawFrom : null) : defaultRange.from,
    to: customDateRange ? (hasToParam ? rawTo : null) : defaultRange.to,
    customDateRange,
    types: parseTypesFromParams(params),
    categoryId: normalizeOptionalId(params.get('categoryId')),
    accountId: normalizeOptionalId(params.get('accountId')),
    attributedToUserId: normalizeOptionalId(params.get('attributedToUserId')),
    currency: rawCurrency ? rawCurrency.toUpperCase() : null,
    status: parseStatusFromParams(params),
    q: params.get('q')?.trim() ?? '',
    sort: parseSortFromParams(params),
  };
}

export function serializeFiltersToQueryParams(state: TransactionsFilterState): Params {
  const params: Params = {};

  if (state.customDateRange) {
    if (state.from && isValidIsoDate(state.from)) {
      params['from'] = state.from;
    }
    if (state.to && isValidIsoDate(state.to)) {
      params['to'] = state.to;
    }
  }

  if (state.types.length > 0) {
    params['type'] = state.types.join(',');
  }
  if (state.categoryId) {
    params['categoryId'] = state.categoryId;
  }
  if (state.accountId) {
    params['accountId'] = state.accountId;
  }
  if (state.attributedToUserId) {
    params['attributedToUserId'] = state.attributedToUserId;
  }
  if (state.currency) {
    params['currency'] = state.currency.toUpperCase();
  }
  if (state.status !== 'ALL') {
    params['status'] = state.status;
  }
  if (state.q.trim().length > 0) {
    params['q'] = state.q.trim();
  }
  if (state.sort !== DEFAULT_SORT) {
    params['sort'] = state.sort;
  }

  return params;
}

export function mapTypeGroupsToApiTypes(
  groups: readonly TransactionTypeGroup[],
): TransactionItemType[] | undefined {
  if (groups.length === 0) {
    return undefined;
  }
  const result: TransactionItemType[] = [];
  for (const group of groups) {
    result.push(...API_TYPES_BY_GROUP[group]);
  }
  return result;
}

export function mapStatusFilterToApiStatus(
  status: TransactionStatusFilter,
): TransactionStatus[] | undefined {
  if (status === 'ALL') {
    return undefined;
  }
  return [status];
}

export function typeGroupLabel(group: TransactionTypeGroup): string {
  return TRANSACTION_TYPE_GROUP_OPTIONS.find((opt) => opt.value === group)?.label ?? group;
}

export function statusFilterLabel(status: TransactionStatusFilter): string {
  return TRANSACTION_STATUS_OPTIONS.find((opt) => opt.value === status)?.label ?? status;
}

export function filtersEqual(a: TransactionsFilterState, b: TransactionsFilterState): boolean {
  if (
    a.from !== b.from ||
    a.to !== b.to ||
    a.customDateRange !== b.customDateRange ||
    a.categoryId !== b.categoryId ||
    a.accountId !== b.accountId ||
    a.attributedToUserId !== b.attributedToUserId ||
    a.currency !== b.currency ||
    a.status !== b.status ||
    a.q !== b.q ||
    a.sort !== b.sort ||
    a.types.length !== b.types.length
  ) {
    return false;
  }
  return a.types.every((type, index) => type === b.types[index]);
}

export function countActiveFilters(state: TransactionsFilterState): number {
  let count = 0;
  if (state.customDateRange) count += 1;
  count += state.types.length;
  if (state.categoryId) count += 1;
  if (state.accountId) count += 1;
  if (state.attributedToUserId) count += 1;
  if (state.currency) count += 1;
  if (state.status !== 'ALL') count += 1;
  if (state.q.trim().length > 0) count += 1;
  return count;
}
