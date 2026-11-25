import { describe, expect, it } from 'vitest';
import {
  clampProgressPct,
  formatSuggestedSavings,
  formatTargetUnit,
  formatUnitAmount,
} from './goal-formatters';

describe('goal-formatters', () => {
  describe('formatTargetUnit', () => {
    it('formats ARS_IPC as "$ de hoy"', () => {
      expect(formatTargetUnit('ARS_IPC')).toBe('$ de hoy');
    });

    it('formats UVA as "UVA"', () => {
      expect(formatTargetUnit('UVA')).toBe('UVA');
    });

    it('formats standard currencies cleanly', () => {
      expect(formatTargetUnit('USD')).toBe('USD');
      expect(formatTargetUnit('EUR')).toBe('EUR');
      expect(formatTargetUnit('ARS')).toBe('ARS');
    });
  });

  describe('formatUnitAmount', () => {
    it('formats ARS_IPC with dollar prefix and "$ de hoy" suffix', () => {
      const formatted = formatUnitAmount('150000', 'ARS_IPC');
      // Normalize non-breaking spaces if any
      expect(formatted.replace(/\s+/g, ' ')).toBe('$ 150.000 $ de hoy');
    });

    it('formats UVA with UVA suffix', () => {
      const formatted = formatUnitAmount('1250.5', 'UVA');
      expect(formatted.replace(/\s+/g, ' ')).toBe('1.250,5 UVA');
    });

    it('formats USD with US$ prefix', () => {
      const formatted = formatUnitAmount('2500', 'USD');
      expect(formatted.replace(/\s+/g, ' ')).toBe('US$ 2.500');
    });

    it('handles zero and null amounts gracefully', () => {
      expect(formatUnitAmount(null, 'ARS').replace(/\s+/g, ' ')).toBe('$ 0');
      expect(formatUnitAmount(0, 'UVA').replace(/\s+/g, ' ')).toBe('0 UVA');
    });
  });

  describe('formatSuggestedSavings', () => {
    it('returns null when monthsLeft is null or negative', () => {
      expect(formatSuggestedSavings(null, '500', 'USD')).toBeNull();
      expect(formatSuggestedSavings(-1, '500', 'USD')).toBeNull();
    });

    it('formats singular month correctly', () => {
      const res = formatSuggestedSavings(1, '200', 'USD');
      expect(res?.replace(/\s+/g, ' ')).toBe('Falta 1 mes · Ahorro sugerido: US$ 200 por mes');
    });

    it('formats plural months correctly with suggested amount', () => {
      const res = formatSuggestedSavings(6, '50000', 'ARS_IPC');
      expect(res?.replace(/\s+/g, ' ')).toBe('Faltan 6 meses · Ahorro sugerido: $ 50000 $ de hoy por mes'.replace('50000', '50.000'));
    });

    it('formats month with UVA unit', () => {
      const res = formatSuggestedSavings(12, '100', 'UVA');
      expect(res?.replace(/\s+/g, ' ')).toBe('Faltan 12 meses · Ahorro sugerido: 100 UVA por mes');
    });

    it('formats 0 months left as "Vence este mes"', () => {
      const res = formatSuggestedSavings(0, '150', 'USD');
      expect(res?.replace(/\s+/g, ' ')).toBe('Vence este mes · Ahorro sugerido: US$ 150 por mes');
    });

    it('omits suggested text when suggested amount is null or zero', () => {
      expect(formatSuggestedSavings(4, null, 'USD')).toBe('Faltan 4 meses');
      expect(formatSuggestedSavings(4, '0', 'USD')).toBe('Faltan 4 meses');
    });
  });

  describe('clampProgressPct', () => {
    it('clamps negative values to 0 and excessive values to 100', () => {
      expect(clampProgressPct(-5)).toBe(0);
      expect(clampProgressPct(120)).toBe(100);
      expect(clampProgressPct('67.44')).toBe(67.4);
      expect(clampProgressPct(null)).toBe(0);
    });
  });
});
