import { toNumber } from '../../../../shared/utils/decimal';
import { ExchangeMode, IndexValue } from '../data-access/exchanges.models';

export type ExchangeField = 'source' | 'target' | 'rate';

export type ComparisonTone = 'positive' | 'negative' | 'neutral';

export interface ExchangeCalculationState {
  mode: ExchangeMode;
  sourceAmount: number | null;
  targetAmount: number | null;
  rate: number | null;
  /**
   * Most recently manually edited distinct fields, ordered oldest -> newest (max length 2).
   * The last field edited wins and is combined with the previous manual field to derive the third.
   */
  editOrder: readonly ExchangeField[];
  derivedField: ExchangeField | null;
}

export interface ReferenceComparison {
  diffPct: number;
  tone: ComparisonTone;
  summary: string;
}

export interface ReferenceLine {
  text: string;
  tone: ComparisonTone;
}

const LOCALE = 'es-AR';
const AMOUNT_DISPLAY_SCALE = 2;
const AMOUNT_MAX_SCALE = 4;
const RATE_SCALE = 6;
const MAX_ROUNDING_DEVIATION = 0.001;
const BACKEND_RATE_TOLERANCE = 0.005;

const CURRENCY_PREFIX: Record<string, string> = {
  ARS: '$',
  USD: 'US$',
  EUR: '€',
};

const INDEX_SHORT_LABELS: Record<string, string> = {
  USD_MEP: 'MEP',
  USD_BLUE: 'Blue',
  USD_OFICIAL: 'Oficial',
  EUR_OFICIAL: 'Euro Oficial',
  EUR_BLUE: 'Euro Blue',
};

const integerFormatter = new Intl.NumberFormat(LOCALE, {
  maximumFractionDigits: 0,
});

const twoDecimalFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const fourDecimalFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

const flexibleAmountFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const percentFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 0,
  maximumFractionDigits: 1,
});

function isPositive(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0;
}

/**
 * Rounds a finite number to `scale` decimal places using round-half-to-even (banker's rounding),
 * matching the backend Money / BigDecimal rounding policy.
 */
export function roundHalfEven(value: number, scale: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }
  const safeScale = Math.max(0, Math.floor(scale));
  const factor = 10 ** safeScale;
  const sign = value < 0 ? -1 : 1;
  const absScaled = Number((Math.abs(value) * factor).toPrecision(12));
  const floorVal = Math.floor(absScaled);
  const fraction = absScaled - floorVal;

  const EPSILON = 1e-9;
  let roundedInt: number;
  if (Math.abs(fraction - 0.5) <= EPSILON) {
    roundedInt = floorVal % 2 === 0 ? floorVal : floorVal + 1;
  } else {
    roundedInt = Math.round(absScaled);
  }

  const result = (sign * roundedInt) / factor;
  return Object.is(result, -0) ? 0 : result;
}

/**
 * Rounds a computed exchange amount to 2 decimals when that stays within 0.1% of the exact value,
 * otherwise preserves up to 4 decimals so small foreign amounts stay well within backend rate tolerance.
 */
export function roundComputedAmount(value: number): number {
  if (!isPositive(value)) {
    return 0;
  }
  const rounded2 = roundHalfEven(value, AMOUNT_DISPLAY_SCALE);
  if (rounded2 > 0 && Math.abs(rounded2 - value) / value <= MAX_ROUNDING_DEVIATION) {
    return rounded2;
  }
  return roundHalfEven(value, AMOUNT_MAX_SCALE);
}

/**
 * Parses a user-typed non-negative decimal string supporting both dot and comma decimal separators
 * as well as grouped thousands (e.g. "1.185,50" or "1,185.50").
 */
export function parseDecimalInput(raw: string | null | undefined): number | null {
  if (raw === null || raw === undefined) {
    return null;
  }
  const trimmed = raw.trim().replace(/\s+/g, '');
  if (trimmed === '') {
    return null;
  }
  if (!/^[\d.,]+$/.test(trimmed)) {
    return null;
  }

  const dotCount = (trimmed.match(/\./g) ?? []).length;
  const commaCount = (trimmed.match(/,/g) ?? []).length;

  let canonical = trimmed;
  if (dotCount > 0 && commaCount > 0) {
    const lastDot = trimmed.lastIndexOf('.');
    const lastComma = trimmed.lastIndexOf(',');
    if (lastComma > lastDot) {
      // Spanish / es-AR format: "1.185,50"
      canonical = trimmed.replace(/\./g, '').replace(',', '.');
    } else {
      // US format: "1,185.50"
      canonical = trimmed.replace(/,/g, '');
    }
  } else if (commaCount > 1) {
    canonical = trimmed.replace(/,/g, '');
  } else if (dotCount > 1) {
    canonical = trimmed.replace(/\./g, '');
  } else if (commaCount === 1) {
    canonical = trimmed.replace(',', '.');
  }

  if (!/^\d+(\.\d+)?$/.test(canonical)) {
    return null;
  }

  const parsed = Number(canonical);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

/**
 * Formats a numeric value as a plain decimal string (no scientific notation) for API payloads.
 */
export function formatDecimalForApi(value: number, maxScale = AMOUNT_MAX_SCALE): string {
  const rounded = roundHalfEven(value, maxScale);
  const fixed = rounded.toFixed(maxScale);
  if (!fixed.includes('.')) {
    return fixed;
  }
  return fixed.replace(/\.?0+$/, '');
}

/**
 * Formats a numeric value for an editable input field using a comma decimal separator (es-AR).
 */
export function formatEditableNumber(value: number | null, maxDecimals = AMOUNT_MAX_SCALE): string {
  if (value === null || !Number.isFinite(value)) {
    return '';
  }
  const plain = formatDecimalForApi(value, maxDecimals);
  return plain.replace('.', ',');
}

/**
 * Computes the exchange rate in units of base currency per 1 unit of foreign currency.
 * - BUY (base -> foreign): sourceAmount / targetAmount
 * - SELL (foreign -> base): targetAmount / sourceAmount
 */
export function computeRate(
  mode: ExchangeMode,
  sourceAmount: number | null,
  targetAmount: number | null,
): number | null {
  if (!isPositive(sourceAmount) || !isPositive(targetAmount)) {
    return null;
  }
  const raw = mode === 'BUY' ? sourceAmount / targetAmount : targetAmount / sourceAmount;
  const rounded = roundHalfEven(raw, RATE_SCALE);
  return rounded > 0 ? rounded : null;
}

/**
 * Computes the target ("Recibo") amount from source ("Pago") and rate.
 * - BUY: targetAmount = sourceAmount / rate
 * - SELL: targetAmount = sourceAmount * rate
 */
export function computeTargetAmount(
  mode: ExchangeMode,
  sourceAmount: number | null,
  rate: number | null,
): number | null {
  if (!isPositive(sourceAmount) || !isPositive(rate)) {
    return null;
  }
  const raw = mode === 'BUY' ? sourceAmount / rate : sourceAmount * rate;
  const rounded = roundComputedAmount(raw);
  return rounded > 0 ? rounded : null;
}

/**
 * Computes the source ("Pago") amount from target ("Recibo") and rate.
 * - BUY: sourceAmount = targetAmount * rate
 * - SELL: sourceAmount = targetAmount / rate
 */
export function computeSourceAmount(
  mode: ExchangeMode,
  targetAmount: number | null,
  rate: number | null,
): number | null {
  if (!isPositive(targetAmount) || !isPositive(rate)) {
    return null;
  }
  const raw = mode === 'BUY' ? targetAmount * rate : targetAmount / rate;
  const rounded = roundComputedAmount(raw);
  return rounded > 0 ? rounded : null;
}

/**
 * Checks whether an applied exchange rate stays within the backend tolerance (0.5%) of the implicit rate.
 */
export function isRateWithinTolerance(
  mode: ExchangeMode,
  sourceAmount: number | null,
  targetAmount: number | null,
  appliedRate: number | null,
  maxDeviationRatio = BACKEND_RATE_TOLERANCE,
): boolean {
  if (!isPositive(sourceAmount) || !isPositive(targetAmount) || !isPositive(appliedRate)) {
    return false;
  }
  const numerator = mode === 'SELL' ? targetAmount : sourceAmount;
  const denominator = mode === 'SELL' ? sourceAmount : targetAmount;
  const diff = Math.abs(appliedRate * denominator - numerator);
  return diff <= numerator * maxDeviationRatio + 1e-12;
}

export function createInitialCalculationState(mode: ExchangeMode = 'BUY'): ExchangeCalculationState {
  return {
    mode,
    sourceAmount: null,
    targetAmount: null,
    rate: null,
    editOrder: [],
    derivedField: null,
  };
}

function getFieldValue(state: ExchangeCalculationState, field: ExchangeField): number | null {
  switch (field) {
    case 'source':
      return state.sourceAmount;
    case 'target':
      return state.targetAmount;
    case 'rate':
      return state.rate;
  }
}

function pickAnchorField(
  state: ExchangeCalculationState,
  editedField: ExchangeField,
): ExchangeField | null {
  // Check previously manually edited field first ("last edited wins").
  for (let i = state.editOrder.length - 1; i >= 0; i--) {
    const candidate = state.editOrder[i];
    if (candidate !== editedField && isPositive(getFieldValue(state, candidate))) {
      return candidate;
    }
  }

  // Fallback priority if there is a populated non-edited field.
  const fallbackOrder: Record<ExchangeField, readonly ExchangeField[]> = {
    source: ['target', 'rate'],
    target: ['source', 'rate'],
    rate: ['source', 'target'],
  };
  for (const candidate of fallbackOrder[editedField]) {
    if (isPositive(getFieldValue(state, candidate))) {
      return candidate;
    }
  }

  return null;
}

function remainingField(a: ExchangeField, b: ExchangeField): ExchangeField {
  const fields: readonly ExchangeField[] = ['source', 'target', 'rate'];
  return fields.find((f) => f !== a && f !== b) ?? 'rate';
}

function nextEditOrder(
  current: readonly ExchangeField[],
  editedField: ExchangeField,
  hasValue: boolean,
): readonly ExchangeField[] {
  const withoutEdited = current.filter((f) => f !== editedField);
  if (!hasValue) {
    return withoutEdited;
  }
  return [...withoutEdited, editedField].slice(-2);
}

/**
 * Pure bidirectional reducer for the three linked exchange fields (source, target, rate).
 * The two most recently manually edited fields determine the third field.
 */
export function recalculateExchange(
  state: ExchangeCalculationState,
  editedField: ExchangeField,
  nextValue: number | null,
): ExchangeCalculationState {
  const sanitized = isPositive(nextValue) ? nextValue : null;

  const updated: ExchangeCalculationState = {
    ...state,
    sourceAmount: editedField === 'source' ? sanitized : state.sourceAmount,
    targetAmount: editedField === 'target' ? sanitized : state.targetAmount,
    rate: editedField === 'rate' ? sanitized : state.rate,
  };

  const editOrder = nextEditOrder(state.editOrder, editedField, sanitized !== null);

  if (sanitized === null) {
    // Clear only the previously derived field so manual inputs are never lost.
    if (state.derivedField && state.derivedField !== editedField) {
      return {
        ...updated,
        sourceAmount: state.derivedField === 'source' ? null : updated.sourceAmount,
        targetAmount: state.derivedField === 'target' ? null : updated.targetAmount,
        rate: state.derivedField === 'rate' ? null : updated.rate,
        editOrder,
        derivedField: null,
      };
    }
    return {
      ...updated,
      editOrder,
      derivedField: null,
    };
  }

  const anchor = pickAnchorField(updated, editedField);
  if (!anchor) {
    return {
      ...updated,
      editOrder,
      derivedField: null,
    };
  }

  const derived = remainingField(editedField, anchor);
  let sourceAmount = updated.sourceAmount;
  let targetAmount = updated.targetAmount;
  let rate = updated.rate;

  if (derived === 'rate') {
    rate = computeRate(state.mode, sourceAmount, targetAmount);
  } else if (derived === 'target') {
    targetAmount = computeTargetAmount(state.mode, sourceAmount, rate);
  } else {
    sourceAmount = computeSourceAmount(state.mode, targetAmount, rate);
  }

  return {
    mode: state.mode,
    sourceAmount,
    targetAmount,
    rate,
    editOrder,
    derivedField: derived,
  };
}

/**
 * Formats a base-currency unit price with its currency prefix (e.g. "$1.185,00" or "$1.190").
 */
export function formatBaseUnitPrice(
  value: number,
  baseCurrency: string,
  options: { compactInteger?: boolean } = {},
): string {
  const code = baseCurrency.trim().toUpperCase();
  const prefix = CURRENCY_PREFIX[code] ?? `${code} `;
  if (options.compactInteger && Math.abs(value - Math.round(value)) < 0.005) {
    return `${prefix}${integerFormatter.format(Math.round(value))}`;
  }
  const formatter = value > 0 && value < 1 ? fourDecimalFormatter : twoDecimalFormatter;
  return `${prefix}${formatter.format(value)}`;
}

/**
 * Formats a rate expression such as "1 USD = $1.185,00".
 */
export function formatRateExpression(
  foreignCurrency: string,
  baseCurrency: string,
  rate: number,
): string {
  const foreign = foreignCurrency.trim().toUpperCase();
  return `1 ${foreign} = ${formatBaseUnitPrice(rate, baseCurrency)}`;
}

/**
 * Formats foreign currency holdings compactly (e.g. "USD 800" or "USD 800,50").
 */
export function formatCurrencyUnits(units: string | number, currencyCode: string): string {
  const numeric = typeof units === 'number' ? units : toNumber(units);
  const code = currencyCode.trim().toUpperCase();
  const isWhole = Math.abs(numeric - Math.round(numeric)) < 0.0005;
  const formatted = isWhole
    ? integerFormatter.format(Math.round(numeric))
    : flexibleAmountFormatter.format(numeric);
  return `${code} ${formatted}`;
}

/**
 * Resolves the best matching reference index for a foreign currency, mirroring the backend rule.
 */
export function pickReferenceIndex(
  indices: readonly IndexValue[],
  foreignCurrency: string,
  preferredSource?: string | null,
): IndexValue | null {
  if (indices.length === 0) {
    return null;
  }
  const currency = foreignCurrency.trim().toUpperCase();
  const byCode = (code: string): IndexValue | undefined =>
    indices.find((item) => item.indexCode.trim().toUpperCase() === code);

  if (currency === 'USD') {
    if (preferredSource) {
      const normalized = preferredSource.trim().toUpperCase();
      const candidate = normalized.startsWith('USD_') ? normalized : `USD_${normalized}`;
      const match = byCode(candidate);
      if (match) {
        return match;
      }
    }
    return (
      byCode('USD_MEP') ??
      indices.find((item) => item.indexCode.trim().toUpperCase().startsWith('USD_')) ??
      null
    );
  }

  if (currency === 'EUR') {
    return (
      byCode('EUR_OFICIAL') ??
      indices.find((item) => item.indexCode.trim().toUpperCase().startsWith('EUR_')) ??
      null
    );
  }

  return null;
}

export function formatIndexShortName(indexCode: string): string {
  const normalized = indexCode.trim().toUpperCase();
  return INDEX_SHORT_LABELS[normalized] ?? normalized.replace(/_/g, ' ');
}

/**
 * Compares the applied exchange rate against today's reference rate for BUY or SELL.
 */
export function compareAgainstReference(
  appliedRate: number,
  referenceRate: number,
  mode: ExchangeMode,
): ReferenceComparison | null {
  if (!isPositive(appliedRate) || !isPositive(referenceRate)) {
    return null;
  }
  const rawDiffPct = ((appliedRate - referenceRate) / referenceRate) * 100;
  const absRounded = roundHalfEven(Math.abs(rawDiffPct), 1);

  if (absRounded < 0.1) {
    return {
      diffPct: 0,
      tone: 'neutral',
      summary: 'igual que la referencia',
    };
  }

  const pctText = `${percentFormatter.format(absRounded)}%`;
  const paidMore = rawDiffPct > 0;

  if (mode === 'BUY') {
    return {
      diffPct: absRounded,
      tone: paidMore ? 'negative' : 'positive',
      summary: paidMore ? `pagaste ${pctText} más` : `pagaste ${pctText} menos`,
    };
  }

  return {
    diffPct: absRounded,
    tone: paidMore ? 'positive' : 'negative',
    summary: paidMore ? `cobraste ${pctText} más` : `cobraste ${pctText} menos`,
  };
}

/**
 * Builds the reference comparison text, e.g. "MEP hoy: $1.190 · pagaste 0,4% menos".
 */
export function buildReferenceLine(
  index: IndexValue | null,
  baseCurrency: string,
  appliedRate: number | null,
  mode: ExchangeMode,
): ReferenceLine | null {
  if (!index) {
    return null;
  }
  const refValue = toNumber(index.value);
  if (refValue <= 0) {
    return null;
  }
  const label = formatIndexShortName(index.indexCode);
  const price = formatBaseUnitPrice(refValue, baseCurrency, { compactInteger: true });
  const baseText = `${label} hoy: ${price}`;

  if (!isPositive(appliedRate)) {
    return { text: baseText, tone: 'neutral' };
  }

  const comparison = compareAgainstReference(appliedRate, refValue, mode);
  if (!comparison) {
    return { text: baseText, tone: 'neutral' };
  }

  return {
    text: `${baseText} · ${comparison.summary}`,
    tone: comparison.tone,
  };
}
