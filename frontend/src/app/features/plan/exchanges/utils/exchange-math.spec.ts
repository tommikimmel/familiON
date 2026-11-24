import { IndexValue } from '../data-access/exchanges.models';
import {
  buildReferenceLine,
  compareAgainstReference,
  computeRate,
  computeSourceAmount,
  computeTargetAmount,
  createInitialCalculationState,
  formatCurrencyUnits,
  formatDecimalForApi,
  formatRateExpression,
  isRateWithinTolerance,
  parseDecimalInput,
  pickReferenceIndex,
  recalculateExchange,
  roundComputedAmount,
  roundHalfEven,
} from './exchange-math';

describe('exchange-math', () => {
  describe('roundHalfEven', () => {
    it('rounds half values to the nearest even digit', () => {
      expect(roundHalfEven(2.5, 0)).toBe(2);
      expect(roundHalfEven(3.5, 0)).toBe(4);
      expect(roundHalfEven(1.005, 2)).toBe(1);
      expect(roundHalfEven(1.015, 2)).toBe(1.02);
    });

    it('rounds standard fractional numbers accurately', () => {
      expect(roundHalfEven(1185.4567, 2)).toBe(1185.46);
      expect(roundHalfEven(1185.454, 2)).toBe(1185.45);
      expect(roundHalfEven(1185.1234567, 6)).toBe(1185.123457);
    });
  });

  describe('parseDecimalInput & formatDecimalForApi', () => {
    it('parses dot, comma, and grouped es-AR / US decimal inputs', () => {
      expect(parseDecimalInput('1185')).toBe(1185);
      expect(parseDecimalInput('1185.50')).toBe(1185.5);
      expect(parseDecimalInput('1185,50')).toBe(1185.5);
      expect(parseDecimalInput('1.185,50')).toBe(1185.5);
      expect(parseDecimalInput('1,185.50')).toBe(1185.5);
      expect(parseDecimalInput('1.185.000')).toBe(1185000);
      expect(parseDecimalInput('')).toBeNull();
      expect(parseDecimalInput('-50')).toBeNull();
      expect(parseDecimalInput('abc')).toBeNull();
    });

    it('formats numbers for API without trailing zeros or scientific notation', () => {
      expect(formatDecimalForApi(1185, 4)).toBe('1185');
      expect(formatDecimalForApi(1185.5, 4)).toBe('1185.5');
      expect(formatDecimalForApi(0.0001, 4)).toBe('0.0001');
      expect(formatDecimalForApi(1185.123456, 6)).toBe('1185.123456');
    });
  });

  describe('core formulas and rounding', () => {
    it('computes BUY rate as source / target and SELL rate as target / source', () => {
      expect(computeRate('BUY', 1_185_000, 1000)).toBe(1185);
      expect(computeRate('SELL', 500, 595_000)).toBe(1190);
      expect(computeRate('BUY', 1000, 0)).toBeNull();
    });

    it('computes amounts bidirectionally for BUY and SELL', () => {
      expect(computeTargetAmount('BUY', 1_185_000, 1185)).toBe(1000);
      expect(computeSourceAmount('BUY', 1000, 1185)).toBe(1_185_000);

      expect(computeTargetAmount('SELL', 500, 1190)).toBe(595_000);
      expect(computeSourceAmount('SELL', 595_000, 1190)).toBe(500);
    });

    it('keeps 4-decimal precision for small amounts so backend 0.5% tolerance passes', () => {
      const target = computeTargetAmount('BUY', 100, 1190);
      expect(target).toBe(0.084);
      expect(isRateWithinTolerance('BUY', 100, target, 1190)).toBe(true);
    });

    it('rounds normal amounts to 2 decimals when within 0.1% deviation', () => {
      expect(roundComputedAmount(1008.40336)).toBe(1008.4);
      expect(isRateWithinTolerance('BUY', 1_200_000, 1008.4, 1190)).toBe(true);
    });
  });

  describe('recalculateExchange (last edited wins)', () => {
    it('computes rate when source and target are entered in BUY mode', () => {
      let state = createInitialCalculationState('BUY');
      state = recalculateExchange(state, 'source', 948_000);
      expect(state.rate).toBeNull();

      state = recalculateExchange(state, 'target', 800);
      expect(state.rate).toBe(1185);
      expect(state.derivedField).toBe('rate');
    });

    it('recomputes source when rate is edited after target in BUY mode', () => {
      let state = createInitialCalculationState('BUY');
      state = recalculateExchange(state, 'source', 948_000);
      state = recalculateExchange(state, 'target', 800);
      // Last manually edited field before rate was target (800).
      state = recalculateExchange(state, 'rate', 1200);

      expect(state.targetAmount).toBe(800);
      expect(state.sourceAmount).toBe(960_000);
      expect(state.derivedField).toBe('source');
    });

    it('recomputes target when rate is edited after source in BUY mode', () => {
      let state = createInitialCalculationState('BUY');
      state = recalculateExchange(state, 'target', 800);
      state = recalculateExchange(state, 'source', 1_200_000);
      // Last manually edited field before rate was source (1,200,000).
      state = recalculateExchange(state, 'rate', 1200);

      expect(state.sourceAmount).toBe(1_200_000);
      expect(state.targetAmount).toBe(1000);
      expect(state.derivedField).toBe('target');
    });

    it('keeps rate fixed and recalculates target when source is edited after rate in SELL mode', () => {
      let state = createInitialCalculationState('SELL');
      state = recalculateExchange(state, 'rate', 1190);
      state = recalculateExchange(state, 'source', 200);

      expect(state.targetAmount).toBe(238_000);
      expect(state.derivedField).toBe('target');

      state = recalculateExchange(state, 'source', 300);
      expect(state.rate).toBe(1190);
      expect(state.targetAmount).toBe(357_000);
      expect(state.derivedField).toBe('target');
    });

    it('clears the derived field without losing manual inputs when a field is cleared', () => {
      let state = createInitialCalculationState('BUY');
      state = recalculateExchange(state, 'source', 948_000);
      state = recalculateExchange(state, 'target', 800);
      expect(state.rate).toBe(1185);

      state = recalculateExchange(state, 'target', null);
      expect(state.sourceAmount).toBe(948_000);
      expect(state.targetAmount).toBeNull();
      expect(state.rate).toBeNull();
    });
  });

  describe('reference comparison & display helpers', () => {
    const indices: IndexValue[] = [
      { indexCode: 'USD_MEP', effectiveDate: '2026-11-24', value: '1190.000000', source: 'DOLARAPI' },
      { indexCode: 'USD_BLUE', effectiveDate: '2026-11-24', value: '1220.000000', source: 'DOLARAPI' },
      { indexCode: 'EUR_OFICIAL', effectiveDate: '2026-11-24', value: '1300.000000', source: 'DOLARAPI' },
    ];

    it('resolves preferred USD index and EUR fallback index', () => {
      expect(pickReferenceIndex(indices, 'USD', 'MEP')?.indexCode).toBe('USD_MEP');
      expect(pickReferenceIndex(indices, 'USD', 'USD_BLUE')?.indexCode).toBe('USD_BLUE');
      expect(pickReferenceIndex(indices, 'EUR', 'MEP')?.indexCode).toBe('EUR_OFICIAL');
      expect(pickReferenceIndex(indices, 'BRL', 'MEP')).toBeNull();
    });

    it('compares BUY and SELL rates against reference correctly', () => {
      const cheapBuy = compareAgainstReference(1185, 1190, 'BUY');
      expect(cheapBuy).toEqual({
        diffPct: 0.4,
        tone: 'positive',
        summary: 'pagaste 0,4% menos',
      });

      const expensiveBuy = compareAgainstReference(1200, 1190, 'BUY');
      expect(expensiveBuy).toEqual({
        diffPct: 0.8,
        tone: 'negative',
        summary: 'pagaste 0,8% más',
      });

      const goodSell = compareAgainstReference(1200, 1190, 'SELL');
      expect(goodSell).toEqual({
        diffPct: 0.8,
        tone: 'positive',
        summary: 'cobraste 0,8% más',
      });

      const equalRate = compareAgainstReference(1190.2, 1190, 'BUY');
      expect(equalRate?.tone).toBe('neutral');
    });

    it('builds reference line and rate expression matching es-AR conventions', () => {
      const line = buildReferenceLine(indices[0], 'ARS', 1185, 'BUY');
      expect(line).toEqual({
        text: 'MEP hoy: $1.190 · pagaste 0,4% menos',
        tone: 'positive',
      });

      expect(formatRateExpression('USD', 'ARS', 1185)).toBe('1 USD = $1.185,00');
      expect(formatCurrencyUnits('800.0000', 'USD')).toBe('USD 800');
      expect(formatCurrencyUnits('800.5000', 'USD')).toBe('USD 800,5');
    });
  });
});
