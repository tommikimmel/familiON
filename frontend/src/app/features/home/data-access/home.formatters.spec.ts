import {
  capDonutSlices,
  formatRelativeIsoDate,
  formatShortIsoDate,
  formatWholeMoney,
  initialsOf,
} from './home.formatters';

describe('formatWholeMoney', () => {
  it('formats ARS as a compact peso amount without decimals', () => {
    expect(formatWholeMoney('1426000.4', 'ARS')).toBe('$1.426.000');
  });

  it('keeps the sign before the currency prefix', () => {
    expect(formatWholeMoney(-45600, 'ARS')).toBe('-$45.600');
  });

  it('uses the US$ prefix for dollars', () => {
    expect(formatWholeMoney('320', 'USD')).toBe('US$320');
  });

  it('falls back to the money pipe for non-currency units', () => {
    expect(formatWholeMoney('75', '%')).toBe('75%');
  });
});

describe('capDonutSlices', () => {
  const slice = (id: string, value: number) => ({ id, label: id.toUpperCase(), value });

  it('returns the slices sorted by value when there are five or fewer', () => {
    const result = capDonutSlices([slice('a', 1), slice('b', 5), slice('c', 3)]);
    expect(result.map((s) => s.id)).toEqual(['b', 'c', 'a']);
  });

  it('keeps the top four and groups the rest as Otros', () => {
    const result = capDonutSlices([
      slice('a', 10),
      slice('b', 9),
      slice('c', 8),
      slice('d', 7),
      slice('e', 2),
      slice('f', 1),
    ]);
    expect(result).toHaveLength(5);
    expect(result[4]).toEqual({ id: 'others', label: 'Otros', value: 3 });
  });
});

describe('formatRelativeIsoDate', () => {
  const today = new Date(2026, 10, 25, 15, 0);

  it('labels today and yesterday like the mock', () => {
    expect(formatRelativeIsoDate('2026-11-25', today)).toBe('Hoy');
    expect(formatRelativeIsoDate('2026-11-24', today)).toBe('Ayer');
  });

  it('falls back to the short date for older days', () => {
    expect(formatRelativeIsoDate('2026-11-05', today)).toBe(formatShortIsoDate('2026-11-05'));
  });
});

describe('initialsOf', () => {
  it('takes the first two letters of the first name in upper case', () => {
    expect(initialsOf('Laura Gómez')).toBe('LA');
    expect(initialsOf('  ')).toBe('?');
  });
});
