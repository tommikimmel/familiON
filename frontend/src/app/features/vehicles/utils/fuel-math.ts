export type FuelCalcField = 'liters' | 'pricePerLiter' | 'totalAmount';

export interface FuelCalcState {
  liters: string;
  pricePerLiter: string;
  totalAmount: string;
  /** Up to 2 most recently edited fields with positive numeric values (oldest to newest). */
  manualOrder: readonly FuelCalcField[];
  /** Currently auto-calculated third field, or null when fewer than 2 valid fields exist. */
  computedField: FuelCalcField | null;
}

export interface FuelPayloadAmounts {
  liters?: string;
  pricePerLiter?: string;
  totalAmount?: string;
}

const ALL_FUEL_FIELDS: readonly FuelCalcField[] = ['liters', 'pricePerLiter', 'totalAmount'];
const POSITIVE_DECIMAL_PATTERN = /^\d+([.,]\d{1,4})?$/;

export function createInitialFuelCalcState(): FuelCalcState {
  return {
    liters: '',
    pricePerLiter: '',
    totalAmount: '',
    manualOrder: [],
    computedField: null,
  };
}

export function parsePositiveDecimal(raw: string | null | undefined): number | null {
  if (raw == null) {
    return null;
  }
  const trimmed = raw.trim();
  if (!POSITIVE_DECIMAL_PATTERN.test(trimmed)) {
    return null;
  }
  const value = Number(trimmed.replace(',', '.'));
  return Number.isFinite(value) && value > 0 ? value : null;
}

export function normalizeDecimalString(raw: string): string {
  return raw.trim().replace(',', '.');
}

function formatRounded(value: number, decimals: number): string {
  if (!Number.isFinite(value) || value <= 0) {
    return '';
  }
  const factor = 10 ** decimals;
  const rounded = Math.round(value * factor) / factor;
  if (rounded <= 0) {
    return '';
  }
  return rounded.toFixed(decimals).replace(/\.?0+$/, '');
}

export function updateFuelField(
  state: FuelCalcState,
  field: FuelCalcField,
  rawValue: string,
): FuelCalcState {
  const next: Record<FuelCalcField, string> = {
    liters: state.liters,
    pricePerLiter: state.pricePerLiter,
    totalAmount: state.totalAmount,
  };
  next[field] = rawValue;

  const parsed = parsePositiveDecimal(rawValue);
  let manualOrder = state.manualOrder.filter((item) => item !== field);

  if (parsed !== null) {
    manualOrder = [...manualOrder, field].slice(-2);
  } else if (state.computedField && state.computedField !== field) {
    next[state.computedField] = '';
  }

  // Keep only manual fields that currently have valid positive numbers.
  manualOrder = manualOrder.filter((item) => parsePositiveDecimal(next[item]) !== null);

  if (manualOrder.length < 2) {
    return {
      liters: next.liters,
      pricePerLiter: next.pricePerLiter,
      totalAmount: next.totalAmount,
      manualOrder,
      computedField: null,
    };
  }

  const target = ALL_FUEL_FIELDS.find((candidate) => !manualOrder.includes(candidate)) ?? null;
  if (!target) {
    return {
      liters: next.liters,
      pricePerLiter: next.pricePerLiter,
      totalAmount: next.totalAmount,
      manualOrder,
      computedField: null,
    };
  }

  const liters = parsePositiveDecimal(next.liters);
  const pricePerLiter = parsePositiveDecimal(next.pricePerLiter);
  const totalAmount = parsePositiveDecimal(next.totalAmount);

  if (target === 'totalAmount' && liters !== null && pricePerLiter !== null) {
    next.totalAmount = formatRounded(liters * pricePerLiter, 2);
  } else if (target === 'liters' && totalAmount !== null && pricePerLiter !== null) {
    next.liters = formatRounded(totalAmount / pricePerLiter, 3);
  } else if (target === 'pricePerLiter' && totalAmount !== null && liters !== null) {
    next.pricePerLiter = formatRounded(totalAmount / liters, 2);
  }

  return {
    liters: next.liters,
    pricePerLiter: next.pricePerLiter,
    totalAmount: next.totalAmount,
    manualOrder,
    computedField: target,
  };
}

export function hasCompleteFuelAmounts(state: FuelCalcState): boolean {
  return (
    state.manualOrder.length === 2 &&
    state.computedField !== null &&
    parsePositiveDecimal(state[state.computedField]) !== null
  );
}

/**
 * Returns the two authoritative user-entered fields for the API payload so the backend
 * computes the third with exact BigDecimal precision without rounding conflicts.
 */
export function buildFuelPayloadAmounts(state: FuelCalcState): FuelPayloadAmounts {
  const result: FuelPayloadAmounts = {};
  for (const field of state.manualOrder) {
    const raw = state[field];
    if (parsePositiveDecimal(raw) !== null) {
      result[field] = normalizeDecimalString(raw);
    }
  }
  return result;
}
