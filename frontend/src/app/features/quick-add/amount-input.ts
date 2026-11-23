export type KeypadKey =
  | '0'
  | '1'
  | '2'
  | '3'
  | '4'
  | '5'
  | '6'
  | '7'
  | '8'
  | '9'
  | '000'
  | ','
  | 'BACKSPACE';

const MAX_INTEGER_DIGITS = 12;
const MAX_DECIMAL_DIGITS = 2;

const CURRENCY_SYMBOLS: Record<string, string> = {
  ARS: '$',
  USD: 'US$',
  EUR: '€',
  USDT: 'USDT',
};

export function currencySymbol(currencyCode: string): string {
  const code = currencyCode.trim().toUpperCase();
  return CURRENCY_SYMBOLS[code] ?? code;
}

/**
 * Applies a single keypad press onto a canonical dot-decimal string (e.g. "1250.5").
 */
export function applyKeypadKey(current: string, key: KeypadKey): string {
  if (key === 'BACKSPACE') {
    if (current.length === 0) {
      return '';
    }
    return current.slice(0, -1);
  }

  if (key === ',') {
    if (current.includes('.')) {
      return current;
    }
    if (current === '') {
      return '0.';
    }
    return `${current}.`;
  }

  const dotIndex = current.indexOf('.');
  const hasDot = dotIndex !== -1;

  if (key === '000') {
    if (current === '' || current === '0') {
      return current;
    }
    if (hasDot) {
      const decimals = current.slice(dotIndex + 1);
      const remaining = MAX_DECIMAL_DIGITS - decimals.length;
      if (remaining <= 0) {
        return current;
      }
      return `${current}${'0'.repeat(Math.min(3, remaining))}`;
    }
    const remaining = MAX_INTEGER_DIGITS - current.length;
    if (remaining <= 0) {
      return current;
    }
    return `${current}${'0'.repeat(Math.min(3, remaining))}`;
  }

  // Single digit 0-9
  if (hasDot) {
    const decimals = current.slice(dotIndex + 1);
    if (decimals.length >= MAX_DECIMAL_DIGITS) {
      return current;
    }
    return `${current}${key}`;
  }

  if (current === '0') {
    return key === '0' ? '0' : key;
  }

  if (current.length >= MAX_INTEGER_DIGITS) {
    return current;
  }

  return `${current}${key}`;
}

/**
 * Sanitizes free-typed desktop input accepting '.' or ',' as decimal separator
 * into a canonical dot-decimal string.
 */
export function normalizeDecimalInput(raw: string): string {
  const normalized = raw.replace(/,/g, '.');
  let integerPart = '';
  let decimalPart = '';
  let seenDot = false;

  for (const ch of normalized) {
    if (ch >= '0' && ch <= '9') {
      if (seenDot) {
        if (decimalPart.length < MAX_DECIMAL_DIGITS) {
          decimalPart += ch;
        }
      } else if (integerPart.length < MAX_INTEGER_DIGITS) {
        integerPart += ch;
      }
    } else if (ch === '.' && !seenDot) {
      seenDot = true;
    }
  }

  // Collapse leading zeros in the integer part while keeping single "0"
  if (integerPart.length > 1) {
    integerPart = integerPart.replace(/^0+(?=\d)/, '');
  }

  if (seenDot) {
    const safeInt = integerPart === '' ? '0' : integerPart;
    return `${safeInt}.${decimalPart}`;
  }

  return integerPart;
}

/**
 * Formats a canonical dot-decimal string for Argentine Spanish display
 * (dot thousands separator, comma decimal separator).
 */
export function formatAmountDisplay(canonical: string): string {
  if (canonical === '') {
    return '0';
  }
  const dotIndex = canonical.indexOf('.');
  const hasDot = dotIndex !== -1;
  const intRaw = hasDot ? canonical.slice(0, dotIndex) : canonical;
  const decRaw = hasDot ? canonical.slice(dotIndex + 1) : '';

  const safeInt = intRaw === '' ? '0' : intRaw;
  const groupedInt = safeInt.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

  return hasDot ? `${groupedInt},${decRaw}` : groupedInt;
}

/**
 * Converts a canonical dot-decimal string into a positive decimal string suitable
 * for backend APIs, or null if zero/empty/invalid.
 */
export function toApiDecimal(canonical: string): string | null {
  if (!canonical) {
    return null;
  }
  const cleaned = canonical.endsWith('.') ? canonical.slice(0, -1) : canonical;
  if (!cleaned || !/^\d+(\.\d+)?$/.test(cleaned)) {
    return null;
  }
  const numeric = Number(cleaned);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return null;
  }
  return cleaned;
}
