import {
  buildCalendarGrid,
  buildExpenseComparison,
  buildIncomeComparison,
  buildInstallmentCommitment,
  evaluateRealVariation,
  formatCalendarMonthTitle,
  getCalendarItemMeta,
  getLensExplanation,
  getLensOptions,
  getMonthDateRange,
  shiftCalendarYearMonth,
} from './analytics.logic';
import { CalendarDay } from './analytics.models';

describe('analytics.logic', () => {
  describe('lens labels and explanations', () => {
    it('builds human labels including reference currency', () => {
      const usdOptions = getLensOptions('USD');
      expect(usdOptions.map((o) => [o.value, o.label])).toEqual([
        ['NOMINAL', 'Pesos del día'],
        ['CONSTANT_BASE', 'A plata de hoy'],
        ['REFERENCE_CURRENCY', 'En dólares'],
        ['INCOME_PCT', '% del ingreso'],
      ]);

      const eurOptions = getLensOptions('EUR');
      expect(eurOptions.find((o) => o.value === 'REFERENCE_CURRENCY')?.label).toBe('En euros');
      expect(getLensExplanation('REFERENCE_CURRENCY', 'EUR')).toContain('euros');
      expect(getLensExplanation('CONSTANT_BASE')).toContain('IPC');
    });
  });

  describe('previous period comparisons', () => {
    it('formats expense reduction vs previous month with minus sign and success tone', () => {
      const result = buildExpenseComparison('95000.0000', '100000.0000', 'MONTH', '2026-11-01');
      expect(result).not.toBeNull();
      expect(result?.pct).toBe(-5);
      expect(result?.tone).toBe('success');
      expect(result?.label).toBe('Gastos \u22125% vs. octubre');
    });

    it('formats expense increase with plus sign and danger tone', () => {
      const result = buildExpenseComparison('112500', '100000', 'YEAR', '2026-01-01');
      expect(result).not.toBeNull();
      expect(result?.pct).toBe(12.5);
      expect(result?.tone).toBe('danger');
      expect(result?.label).toBe('Gastos +12,5% vs. 2025');
    });

    it('returns null when previous period total is zero', () => {
      expect(buildExpenseComparison('50000', '0', 'MONTH', '2026-11-01')).toBeNull();
      expect(buildIncomeComparison('50000', '0', 'MONTH', '2026-11-01')).toBeNull();
    });

    it('formats income comparison with success tone when increasing', () => {
      const result = buildIncomeComparison('110000', '100000', 'WEEK', '2026-11-09');
      expect(result?.tone).toBe('success');
      expect(result?.label).toBe('Ingresos +10% vs. semana anterior');
    });
  });

  describe('realVariationPct traffic light', () => {
    it('classifies <= 0 as green', () => {
      const zero = evaluateRealVariation('0', 'MONTH', 'FAMILY');
      expect(zero?.tone).toBe('green');
      expect(zero?.message).toBe(
        'Este mes gastaron 0% real respecto al promedio de los últimos 3 meses',
      );

      const negative = evaluateRealVariation('-4.2', 'MONTH', 'PERSONAL');
      expect(negative?.tone).toBe('green');
      expect(negative?.message).toBe(
        'Este mes gastaste \u22124,2% real respecto al promedio de los últimos 3 meses',
      );
    });

    it('classifies > 0 and <= 10 as amber', () => {
      const eight = evaluateRealVariation('8', 'MONTH', 'FAMILY');
      expect(eight?.tone).toBe('amber');
      expect(eight?.message).toBe(
        'Este mes gastaron +8% real respecto al promedio de los últimos 3 meses',
      );

      const ten = evaluateRealVariation('10', 'MONTH', 'FAMILY');
      expect(ten?.tone).toBe('amber');
    });

    it('classifies > 10 as red', () => {
      const high = evaluateRealVariation('14.6', 'MONTH', 'FAMILY');
      expect(high?.tone).toBe('red');
      expect(high?.message).toBe(
        'Este mes gastaron +14,6% real respecto al promedio de los últimos 3 meses',
      );
    });

    it('returns null when realVariationPct is null or empty', () => {
      expect(evaluateRealVariation(null, 'MONTH', 'PERSONAL')).toBeNull();
      expect(evaluateRealVariation('', 'MONTH', 'PERSONAL')).toBeNull();
    });
  });

  describe('installment commitment', () => {
    it('formats commitment text and tone thresholds', () => {
      const low = buildInstallmentCommitment('18');
      expect(low?.message).toBe('Ya comprometido en cuotas: 18% del ingreso');
      expect(low?.tone).toBe('accent');
      expect(low?.barWidth).toBe(18);

      expect(buildInstallmentCommitment('32')?.tone).toBe('warning');
      expect(buildInstallmentCommitment('55')?.tone).toBe('danger');
      expect(buildInstallmentCommitment('120')?.barWidth).toBe(100);
      expect(buildInstallmentCommitment(null)).toBeNull();
    });
  });

  describe('calendar grid (Monday-first)', () => {
    it('builds November 2026 with 6 leading empty cells because Nov 1 is Sunday', () => {
      const days: CalendarDay[] = [
        {
          date: '2026-11-10',
          income: '0',
          expense: '45000',
          items: [
            {
              kind: 'INSTALLMENT',
              refId: 'inst-1',
              title: 'Cuota 3/12 Notebook',
              amount: '45000',
              currencyCode: 'ARS',
            },
          ],
        },
        {
          date: '2026-11-15',
          income: '0',
          expense: '12000',
          items: [
            {
              kind: 'TRANSACTION',
              refId: 'tx-1',
              title: 'Supermercado',
              amount: '12000',
              currencyCode: 'ARS',
            },
          ],
        },
        {
          date: '2026-11-20',
          income: '0',
          expense: '0',
          items: [
            {
              kind: 'VEHICLE_DUE',
              refId: 'veh-1',
              title: 'VTV Auto',
              amount: null,
              currencyCode: null,
            },
          ],
        },
      ];

      const weeks = buildCalendarGrid(2026, 11, days, '2026-11-24');

      // Nov 2026 has 30 days: 6 leading + 30 days = 36 slots -> 6 weeks (42 slots)
      expect(weeks).toHaveLength(6);
      expect(weeks[0].slice(0, 6)).toEqual([null, null, null, null, null, null]);
      expect(weeks[0][6]?.date).toBe('2026-11-01');
      expect(weeks[0][6]?.dayNumber).toBe(1);

      // Last week starts with Nov 30 (Monday) followed by 6 nulls
      expect(weeks[5][0]?.date).toBe('2026-11-30');
      expect(weeks[5].slice(1)).toEqual([null, null, null, null, null, null]);

      const flatCells = weeks.flat().filter((cell) => cell !== null);
      expect(flatCells).toHaveLength(30);

      const nov10 = flatCells.find((c) => c.date === '2026-11-10');
      expect(nov10?.hasDueItems).toBe(true);
      expect(nov10?.dueCount).toBe(1);
      expect(nov10?.expense).toBe(45000);

      const nov15 = flatCells.find((c) => c.date === '2026-11-15');
      expect(nov15?.hasDueItems).toBe(false);
      expect(nov15?.items).toHaveLength(1);

      const nov20 = flatCells.find((c) => c.date === '2026-11-20');
      expect(nov20?.hasDueItems).toBe(true);
      expect(nov20?.dueCount).toBe(1);

      const nov24 = flatCells.find((c) => c.date === '2026-11-24');
      expect(nov24?.isToday).toBe(true);
    });

    it('computes month date range, month shifts across year boundaries, and item kind labels', () => {
      expect(getMonthDateRange(2026, 11)).toEqual({ from: '2026-11-01', to: '2026-11-30' });
      expect(getMonthDateRange(2028, 2)).toEqual({ from: '2028-02-01', to: '2028-02-29' });
      expect(shiftCalendarYearMonth({ year: 2026, month: 1 }, -1)).toEqual({
        year: 2025,
        month: 12,
      });
      expect(shiftCalendarYearMonth({ year: 2026, month: 12 }, 1)).toEqual({
        year: 2027,
        month: 1,
      });
      expect(formatCalendarMonthTitle(2026, 11)).toMatch(/Noviembre.*2026/i);

      expect(getCalendarItemMeta('INSTALLMENT').label).toBe('Cuota');
      expect(getCalendarItemMeta('RECURRING_DUE').label).toBe('Gasto fijo');
      expect(getCalendarItemMeta('VEHICLE_DUE').label).toBe('Vencimiento auto');
      expect(getCalendarItemMeta('ALLOWANCE').label).toBe('Mensualidad');
      expect(getCalendarItemMeta('TRANSACTION').isDue).toBe(false);
    });
  });
});
