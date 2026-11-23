import {
  applyKeypadKey,
  formatAmountDisplay,
  normalizeDecimalInput,
  toApiDecimal,
} from './amount-input';

describe('amount-input keypad helpers', () => {
  it('handles 000 key on integers, decimals, and zero/empty states', () => {
    expect(applyKeypadKey('', '000')).toBe('');
    expect(applyKeypadKey('0', '000')).toBe('0');
    expect(applyKeypadKey('15', '000')).toBe('15000');
    expect(applyKeypadKey('12.', '000')).toBe('12.00');
    expect(applyKeypadKey('12.5', '000')).toBe('12.50');
    expect(applyKeypadKey('12.50', '000')).toBe('12.50');
  });

  it('allows only a single decimal comma and prefixes zero when empty', () => {
    let value = '';
    value = applyKeypadKey(value, ',');
    expect(value).toBe('0.');

    value = applyKeypadKey(value, ',');
    expect(value).toBe('0.');

    value = applyKeypadKey(value, '5');
    value = applyKeypadKey(value, ',');
    expect(value).toBe('0.5');

    value = applyKeypadKey(value, '2');
    value = applyKeypadKey(value, '9');
    expect(value).toBe('0.52');
  });

  it('deletes characters with BACKSPACE down to empty', () => {
    let value = '150.5';
    value = applyKeypadKey(value, 'BACKSPACE');
    expect(value).toBe('150.');
    value = applyKeypadKey(value, 'BACKSPACE');
    expect(value).toBe('150');
    value = applyKeypadKey(value, 'BACKSPACE');
    expect(value).toBe('15');
    value = applyKeypadKey(value, 'BACKSPACE');
    expect(value).toBe('1');
    value = applyKeypadKey(value, 'BACKSPACE');
    expect(value).toBe('');
    value = applyKeypadKey(value, 'BACKSPACE');
    expect(value).toBe('');
  });

  it('normalizes desktop input accepting both comma and dot', () => {
    expect(normalizeDecimalInput('1250,5')).toBe('1250.5');
    expect(normalizeDecimalInput('1250.50')).toBe('1250.50');
    expect(normalizeDecimalInput('12,34,56')).toBe('12.34');
    expect(normalizeDecimalInput(',75')).toBe('0.75');
    expect(normalizeDecimalInput('007')).toBe('7');
    expect(normalizeDecimalInput('abc45.6xyz')).toBe('45.6');
  });

  it('formats display string in es-AR and converts to API decimal string', () => {
    expect(formatAmountDisplay('')).toBe('0');
    expect(formatAmountDisplay('36000')).toBe('36.000');
    expect(formatAmountDisplay('36000.')).toBe('36.000,');
    expect(formatAmountDisplay('36000.5')).toBe('36.000,5');

    expect(toApiDecimal('')).toBeNull();
    expect(toApiDecimal('0')).toBeNull();
    expect(toApiDecimal('0.00')).toBeNull();
    expect(toApiDecimal('36000.')).toBe('36000');
    expect(toApiDecimal('36000.50')).toBe('36000.50');
  });
});
