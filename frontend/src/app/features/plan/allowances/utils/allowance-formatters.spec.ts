import { describe, expect, it } from 'vitest';
import {
  formatAdjustmentRule,
  formatFrequency,
  formatShortDate,
  getInitials,
} from './allowance-formatters';

describe('allowance-formatters', () => {
  describe('formatFrequency', () => {
    it('formats weekly frequency with correct weekday name', () => {
      expect(formatFrequency('WEEKLY', 1)).toBe('Semanal (los lunes)');
      expect(formatFrequency('WEEKLY', 5)).toBe('Semanal (los viernes)');
    });

    it('formats monthly frequency with day of month', () => {
      expect(formatFrequency('MONTHLY', 1)).toBe('Mensual (día 1 de cada mes)');
      expect(formatFrequency('MONTHLY', 15)).toBe('Mensual (día 15 de cada mes)');
    });
  });

  describe('formatAdjustmentRule', () => {
    it('returns clear Spanish labels for each rule', () => {
      expect(formatAdjustmentRule('IPC')).toBe('Ajuste por inflación (IPC)');
      expect(formatAdjustmentRule('REFERENCE_CURRENCY')).toBe('Ajuste por divisa');
      expect(formatAdjustmentRule('MANUAL')).toBe('Monto fijo (manual)');
    });
  });

  describe('formatShortDate', () => {
    it('formats ISO dates into readable Spanish dates', () => {
      expect(formatShortDate('2026-11-24')).toBe('24 de noviembre de 2026');
      expect(formatShortDate('2026-01-05')).toBe('5 de enero de 2026');
    });

    it('handles empty or invalid dates gracefully', () => {
      expect(formatShortDate(null)).toBe('Sin fecha');
      expect(formatShortDate('')).toBe('Sin fecha');
    });
  });

  describe('getInitials', () => {
    it('extracts first and last initials', () => {
      expect(getInitials('Tomás Kimmel')).toBe('TK');
      expect(getInitials('Ana María García')).toBe('AG');
      expect(getInitials('Pedro')).toBe('PE');
      expect(getInitials(null)).toBe('?');
    });
  });
});
