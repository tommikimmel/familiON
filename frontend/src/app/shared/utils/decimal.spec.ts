import { toNumber } from './decimal';

describe('toNumber', () => {
  it('parses backend decimal strings into numbers', () => {
    expect(toNumber('36000.0000')).toBe(36000);
    expect(toNumber('-1250.5000')).toBe(-1250.5);
    expect(toNumber('0.0000')).toBe(0);
  });

  it('returns 0 for null, undefined, empty, or invalid inputs', () => {
    expect(toNumber(null)).toBe(0);
    expect(toNumber(undefined)).toBe(0);
    expect(toNumber('')).toBe(0);
    expect(toNumber('   ')).toBe(0);
    expect(toNumber('not-a-number')).toBe(0);
  });
});
