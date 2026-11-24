import {
  TransactionItem,
  TransactionItemType,
  TransactionPatch,
  TransactionScope,
} from '../../../core/finance/transactions.models';
import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { FoIconName } from '../../../shared/ui/icon/icon.component';
import { toNumber } from '../../../shared/utils/decimal';

const WEEKDAYS_ES = [
  'Domingo',
  'Lunes',
  'Martes',
  'Miércoles',
  'Jueves',
  'Viernes',
  'Sábado',
] as const;

const SHORT_MONTHS_ES = [
  'ene',
  'feb',
  'mar',
  'abr',
  'may',
  'jun',
  'jul',
  'ago',
  'sep',
  'oct',
  'nov',
  'dic',
] as const;

const moneyPipe = new MoneyPipe();

const twoDecimalFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export interface DayTotalEntry {
  readonly unit: string;
  readonly amount: number;
  readonly approximated: boolean;
}

export interface TransactionDayGroup {
  readonly date: string;
  readonly label: string;
  readonly items: TransactionItem[];
  readonly totals: DayTotalEntry[];
}

export interface TransactionEditCapabilities {
  readonly canEditAmount: boolean;
  readonly canEditDate: boolean;
  readonly canEditCategory: boolean;
  readonly canEditAccount: boolean;
  readonly canEditAttribution: boolean;
  readonly canEditScope: boolean;
  readonly isInstallment: boolean;
  readonly isExchange: boolean;
  readonly isAllowance: boolean;
  readonly isTransfer: boolean;
  readonly restrictionNotice: string | null;
}

export interface TransactionEditDraft {
  readonly amount: string;
  readonly transactionDate: string;
  readonly categoryId: string | null;
  readonly description: string;
  readonly notes: string;
  readonly accountId: string;
  readonly attributedToUserId: string;
  readonly scope: TransactionScope;
}

function parseIsoParts(isoDate: string): [number, number, number] | null {
  const parts = isoDate.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    return null;
  }
  return [parts[0], parts[1], parts[2]];
}

export function formatLocalTodayIso(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function shiftIsoDays(isoDate: string, deltaDays: number): string | null {
  const parts = parseIsoParts(isoDate);
  if (!parts) {
    return null;
  }
  const shifted = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2] + deltaDays));
  const year = shifted.getUTCFullYear();
  const month = String(shifted.getUTCMonth() + 1).padStart(2, '0');
  const day = String(shifted.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatDayGroupLabel(
  isoDate: string,
  referenceTodayIso: string = formatLocalTodayIso(),
): string {
  if (isoDate === referenceTodayIso) {
    return 'Hoy';
  }
  const yesterdayIso = shiftIsoDays(referenceTodayIso, -1);
  if (yesterdayIso && isoDate === yesterdayIso) {
    return 'Ayer';
  }

  const parts = parseIsoParts(isoDate);
  const todayParts = parseIsoParts(referenceTodayIso);
  if (!parts) {
    return isoDate;
  }

  const [year, month, day] = parts;
  const date = new Date(Date.UTC(year, month - 1, day));
  const weekday = WEEKDAYS_ES[date.getUTCDay()];
  const monthLabel = SHORT_MONTHS_ES[month - 1];
  const includeYear = todayParts !== null && todayParts[0] !== year;

  return includeYear
    ? `${weekday} ${day} ${monthLabel} ${year}`
    : `${weekday} ${day} ${monthLabel}`;
}

export function formatShortDate(isoDate: string): string {
  const parts = parseIsoParts(isoDate);
  if (!parts) {
    return isoDate;
  }
  const [, month, day] = parts;
  return `${day} ${SHORT_MONTHS_ES[month - 1]}`;
}

export function formatLongDate(isoDate: string): string {
  const parts = parseIsoParts(isoDate);
  if (!parts) {
    return isoDate;
  }
  const [year, month, day] = parts;
  const date = new Date(Date.UTC(year, month - 1, day));
  return `${WEEKDAYS_ES[date.getUTCDay()]} ${day} ${SHORT_MONTHS_ES[month - 1]} ${year}`;
}

export function resolveLensDisplayUnit(
  lensUnit: string | null | undefined,
  lens: Lens,
  fallbackCurrency = 'ARS',
): string {
  if (lens === 'INCOME_PCT') {
    return '%';
  }
  const normalized = lensUnit?.trim();
  return normalized && normalized.length > 0 ? normalized : fallbackCurrency;
}

export function dayTotalSign(type: TransactionItemType): 1 | -1 | 0 {
  switch (type) {
    case 'INCOME':
    case 'ALLOWANCE_IN':
      return 1;
    case 'EXPENSE':
    case 'ALLOWANCE_OUT':
      return -1;
    default:
      return 0;
  }
}

export function groupTransactionsByDay(
  items: readonly TransactionItem[],
  lens: Lens,
  referenceTodayIso: string = formatLocalTodayIso(),
): TransactionDayGroup[] {
  const groups = new Map<
    string,
    {
      items: TransactionItem[];
      totalsByUnit: Map<string, { amount: number; approximated: boolean }>;
    }
  >();

  for (const item of items) {
    const key = item.transactionDate;
    let bucket = groups.get(key);
    if (!bucket) {
      bucket = { items: [], totalsByUnit: new Map() };
      groups.set(key, bucket);
    }
    bucket.items.push(item);

    const sign = dayTotalSign(item.type);
    if (sign === 0) {
      continue;
    }

    const useLens = lens !== 'NOMINAL' && item.lensAmount !== null;
    if (lens !== 'NOMINAL' && item.lensAmount === null) {
      continue;
    }

    const unit = useLens
      ? resolveLensDisplayUnit(item.lensUnit, lens, item.currencyCode)
      : item.currencyCode.trim().toUpperCase();
    const rawValue = Math.abs(toNumber(useLens ? item.lensAmount : item.amount));
    const entry = bucket.totalsByUnit.get(unit) ?? { amount: 0, approximated: false };
    entry.amount += sign * rawValue;
    entry.approximated = entry.approximated || (useLens && item.lensApproximated);
    bucket.totalsByUnit.set(unit, entry);
  }

  const result: TransactionDayGroup[] = [];
  for (const [date, bucket] of groups.entries()) {
    const totals: DayTotalEntry[] = [];
    for (const [unit, entry] of bucket.totalsByUnit.entries()) {
      const rounded = Math.round(entry.amount * 100) / 100;
      totals.push({
        unit,
        amount: Object.is(rounded, -0) ? 0 : rounded,
        approximated: entry.approximated,
      });
    }
    result.push({
      date,
      label: formatDayGroupLabel(date, referenceTodayIso),
      items: bucket.items,
      totals,
    });
  }
  return result;
}

export function transactionTypeLabel(type: TransactionItemType): string {
  switch (type) {
    case 'EXPENSE':
      return 'Gasto';
    case 'INCOME':
      return 'Ingreso';
    case 'TRANSFER_OUT':
    case 'TRANSFER_IN':
      return 'Transferencia';
    case 'EXCHANGE_OUT':
    case 'EXCHANGE_IN':
      return 'Divisa';
    case 'ALLOWANCE_OUT':
    case 'ALLOWANCE_IN':
      return 'Mensualidad';
  }
}

export function transactionStatusLabel(status: string): string {
  switch (status.toUpperCase()) {
    case 'PROJECTED':
      return 'Proyectada';
    case 'CANCELLED':
      return 'Cancelada';
    default:
      return 'Confirmada';
  }
}

export function fallbackTransactionIcon(type: TransactionItemType): FoIconName {
  switch (type) {
    case 'INCOME':
      return 'briefcase';
    case 'TRANSFER_OUT':
    case 'TRANSFER_IN':
      return 'arrow-left-right';
    case 'EXCHANGE_OUT':
    case 'EXCHANGE_IN':
      return 'repeat';
    case 'ALLOWANCE_OUT':
    case 'ALLOWANCE_IN':
      return 'gift';
    default:
      return 'circle-ellipsis';
  }
}

export function transactionAmountTone(
  type: TransactionItemType,
): 'positive' | 'negative' | 'neutral' {
  switch (type) {
    case 'INCOME':
    case 'ALLOWANCE_IN':
      return 'positive';
    case 'EXPENSE':
    case 'ALLOWANCE_OUT':
      return 'negative';
    default:
      return 'neutral';
  }
}

export function formatSignedAmount(
  amount: string | number,
  currencyCode: string,
  type: TransactionItemType,
): string {
  const numeric = typeof amount === 'number' ? Math.abs(amount) : Math.abs(toNumber(amount));
  const formatted = moneyPipe.transform(numeric, currencyCode);
  switch (type) {
    case 'INCOME':
    case 'TRANSFER_IN':
    case 'EXCHANGE_IN':
    case 'ALLOWANCE_IN':
      return `+${formatted}`;
    case 'EXPENSE':
    case 'TRANSFER_OUT':
    case 'EXCHANGE_OUT':
    case 'ALLOWANCE_OUT':
      return `-${formatted}`;
  }
}

export function formatSignedDayTotal(
  amount: number,
  unit: string,
  approximated = false,
): string {
  const prefix = approximated ? '~' : '';
  const formatted = moneyPipe.transform(Math.abs(amount), unit);
  if (amount > 0) {
    return `${prefix}+${formatted}`;
  }
  if (amount < 0) {
    return `${prefix}-${formatted}`;
  }
  return `${prefix}${formatted}`;
}

export function formatReferenceSnapshot(snapshot: TransactionItem | null): string | null {
  if (!snapshot || snapshot.lensAmount === null) {
    return null;
  }
  const unit = snapshot.lensUnit?.trim().toUpperCase();
  if (!unit) {
    return null;
  }
  if (unit === snapshot.currencyCode.trim().toUpperCase()) {
    return null;
  }
  const numeric = Math.abs(toNumber(snapshot.lensAmount));
  const formatted = twoDecimalFormatter.format(numeric);
  const approx = snapshot.lensApproximated ? '~' : '';
  return `Equivalía a ${approx}${unit} ${formatted} ese día`;
}

export function getTransactionEditCapabilities(
  tx: TransactionItem,
  isAdult: boolean,
): TransactionEditCapabilities {
  const isInstallment = tx.installment !== null;
  const isExchange = tx.type === 'EXCHANGE_OUT' || tx.type === 'EXCHANGE_IN';
  const isAllowance = tx.type === 'ALLOWANCE_OUT' || tx.type === 'ALLOWANCE_IN';
  const isTransfer = tx.type === 'TRANSFER_OUT' || tx.type === 'TRANSFER_IN';

  if (isInstallment) {
    return {
      canEditAmount: false,
      canEditDate: false,
      canEditCategory: true,
      canEditAccount: false,
      canEditAttribution: false,
      canEditScope: false,
      isInstallment,
      isExchange,
      isAllowance,
      isTransfer,
      restrictionNotice:
        'En una cuota no se pueden modificar el monto, la fecha ni la cuenta. Gestioná el plan desde Plan → Cuotas.',
    };
  }

  if (isExchange) {
    return {
      canEditAmount: false,
      canEditDate: false,
      canEditCategory: false,
      canEditAccount: false,
      canEditAttribution: false,
      canEditScope: false,
      isInstallment,
      isExchange,
      isAllowance,
      isTransfer,
      restrictionNotice:
        'En operaciones de cambio de divisa no se pueden modificar el monto ni la fecha. Solo podés editar la descripción y las notas.',
    };
  }

  if (isAllowance) {
    return {
      canEditAmount: false,
      canEditDate: false,
      canEditCategory: false,
      canEditAccount: false,
      canEditAttribution: false,
      canEditScope: false,
      isInstallment,
      isExchange,
      isAllowance,
      isTransfer,
      restrictionNotice:
        'En mensualidades no se pueden modificar el monto ni la fecha. Solo podés editar la descripción y las notas.',
    };
  }

  if (isTransfer) {
    return {
      canEditAmount: true,
      canEditDate: true,
      canEditCategory: false,
      canEditAccount: false,
      canEditAttribution: false,
      canEditScope: false,
      isInstallment,
      isExchange,
      isAllowance,
      isTransfer,
      restrictionNotice:
        'En transferencias podés editar monto, fecha, descripción y notas; los cambios se aplican a ambos movimientos.',
    };
  }

  return {
    canEditAmount: true,
    canEditDate: true,
    canEditCategory: true,
    canEditAccount: true,
    canEditAttribution: isAdult,
    canEditScope: isAdult,
    isInstallment,
    isExchange,
    isAllowance,
    isTransfer,
    restrictionNotice: null,
  };
}

export function normalizeEditableAmount(raw: string): string | null {
  const cleaned = raw.trim().replace(',', '.');
  if (cleaned === '') {
    return null;
  }
  if (!/^\d+(\.\d{1,4})?$/.test(cleaned)) {
    return null;
  }
  const numeric = Number(cleaned);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null;
  }
  return numeric.toFixed(4);
}

export function buildTransactionPatch(
  original: TransactionItem,
  draft: TransactionEditDraft,
  capabilities: TransactionEditCapabilities,
): TransactionPatch {
  const patch: TransactionPatch = {};

  if (capabilities.canEditAmount) {
    const normalizedDraft = normalizeEditableAmount(draft.amount);
    const originalNumeric = toNumber(original.amount);
    if (normalizedDraft !== null && Math.abs(Number(normalizedDraft) - originalNumeric) > 1e-6) {
      patch.amount = normalizedDraft;
    }
  }

  if (capabilities.canEditDate && draft.transactionDate !== original.transactionDate) {
    patch.transactionDate = draft.transactionDate;
  }

  if (capabilities.canEditCategory) {
    const nextCategoryId = draft.categoryId && draft.categoryId.trim() !== '' ? draft.categoryId : null;
    const prevCategoryId = original.category?.id ?? null;
    if (nextCategoryId !== prevCategoryId) {
      patch.categoryId = nextCategoryId;
    }
  }

  const nextDescription = draft.description.trim() === '' ? null : draft.description.trim();
  const prevDescription =
    original.description === null || original.description.trim() === ''
      ? null
      : original.description.trim();
  if (nextDescription !== prevDescription) {
    patch.description = nextDescription;
  }

  const nextNotes = draft.notes.trim() === '' ? null : draft.notes.trim();
  const prevNotes =
    original.notes === null || original.notes.trim() === '' ? null : original.notes.trim();
  if (nextNotes !== prevNotes) {
    patch.notes = nextNotes;
  }

  if (capabilities.canEditAccount && draft.accountId && draft.accountId !== original.account.id) {
    patch.accountId = draft.accountId;
  }

  if (
    capabilities.canEditAttribution &&
    draft.attributedToUserId &&
    draft.attributedToUserId !== original.attributedTo.id
  ) {
    patch.attributedToUserId = draft.attributedToUserId;
  }

  if (capabilities.canEditScope && draft.scope !== original.scope) {
    patch.scope = draft.scope;
  }

  return patch;
}
