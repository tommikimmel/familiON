import { MoneyPipe } from './money.pipe';

describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  it('formats numbers and decimal strings in ARS using es-AR currency format', () => {
    const expected = new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 2,
    }).format(36000);

    expect(pipe.transform(36000, 'ARS')).toBe(expected);
    expect(pipe.transform('36000.0000', 'ARS')).toBe(expected);
  });

  it('formats USD currency values', () => {
    const expected = new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2,
    }).format(125.5);

    expect(pipe.transform('125.5000', 'USD')).toBe(expected);
  });

  it('formats non-ISO units like UVA and % as number plus suffix', () => {
    const formattedNumber = new Intl.NumberFormat('es-AR', {
      maximumFractionDigits: 2,
    }).format(1250.5);

    expect(pipe.transform('1250.5000', 'UVA')).toBe(`${formattedNumber} UVA`);

    const formattedPct = new Intl.NumberFormat('es-AR', {
      maximumFractionDigits: 2,
    }).format(42.5);

    expect(pipe.transform(42.5, '%')).toBe(`${formattedPct}%`);
  });

  it('handles null and undefined values safely as 0', () => {
    const expectedZero = new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 2,
    }).format(0);

    expect(pipe.transform(null, 'ARS')).toBe(expectedZero);
    expect(pipe.transform(undefined, 'ARS')).toBe(expectedZero);
  });
});
