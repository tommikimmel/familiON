import { installmentPreview, installmentSummaryLines } from './installment-preview';

describe('installmentPreview', () => {
  it('computes per-installment amount in TOTAL mode and rounds to 2 decimals', () => {
    const result = installmentPreview({
      amount: '100',
      totalInstallments: 3,
      amountMode: 'TOTAL',
      purchaseDate: '2026-11-10',
      closingDay: 20,
      currencyCode: 'ARS',
    });

    expect(result).not.toBeNull();
    expect(result?.totalAmount).toBe(100);
    expect(result?.perInstallmentAmount).toBe(33.33);
    expect(result?.firstMonthLabel).toBe('Dic 2026');
    expect(result?.lastMonthLabel).toBe('Feb 2027');
    expect(result?.summary).toContain('3 cuotas de');
    expect(result?.summary).toContain('33,33/mes');
    expect(result?.summary).toContain('(Dic 2026 → Feb 2027)');
  });

  it('computes total amount in PER_INSTALLMENT mode and formats exact example', () => {
    const result = installmentPreview({
      amount: '36000',
      totalInstallments: 6,
      amountMode: 'PER_INSTALLMENT',
      purchaseDate: '2026-11-15',
      closingDay: 15,
      currencyCode: 'ARS',
    });

    expect(result).not.toBeNull();
    expect(result?.perInstallmentAmount).toBe(36000);
    expect(result?.totalAmount).toBe(216000);
    expect(result?.firstMonthLabel).toBe('Dic 2026');
    expect(result?.lastMonthLabel).toBe('May 2027');
    expect(result?.summary).toContain('6 cuotas de');
    expect(result?.summary).toContain('36.000/mes');
    expect(result?.summary).toContain('216.000');
    expect(result?.summary).toContain('(Dic 2026 → May 2027)');
  });

  it('starts two months later when purchase day is after closingDay', () => {
    const result = installmentPreview({
      amount: 120000,
      totalInstallments: 6,
      amountMode: 'TOTAL',
      purchaseDate: '2026-11-21',
      closingDay: 20,
      currencyCode: 'ARS',
    });

    expect(result).not.toBeNull();
    expect(result?.perInstallmentAmount).toBe(20000);
    expect(result?.totalAmount).toBe(120000);
    expect(result?.firstMonthLabel).toBe('Ene 2027');
    expect(result?.lastMonthLabel).toBe('Jun 2027');
  });

  it('starts the next month when closingDay is absent', () => {
    const result = installmentPreview({
      amount: '60000',
      totalInstallments: 3,
      amountMode: 'TOTAL',
      purchaseDate: '2026-11-28',
      closingDay: null,
      currencyCode: 'ARS',
    });

    expect(result).not.toBeNull();
    expect(result?.firstMonthLabel).toBe('Dic 2026');
    expect(result?.lastMonthLabel).toBe('Feb 2027');
  });

  it('returns null for invalid amount, single installment, or invalid date', () => {
    expect(
      installmentPreview({
        amount: '0',
        totalInstallments: 6,
        amountMode: 'TOTAL',
        purchaseDate: '2026-11-10',
      }),
    ).toBeNull();

    expect(
      installmentPreview({
        amount: '1000',
        totalInstallments: 1,
        amountMode: 'TOTAL',
        purchaseDate: '2026-11-10',
      }),
    ).toBeNull();

    expect(
      installmentPreview({
        amount: '1000',
        totalInstallments: 6,
        amountMode: 'TOTAL',
        purchaseDate: 'invalid',
      }),
    ).toBeNull();
  });
});

describe('installmentSummaryLines', () => {
  const preview = installmentPreview({
    amount: '180000',
    totalInstallments: 6,
    amountMode: 'TOTAL',
    purchaseDate: '2026-11-15',
    closingDay: 20,
    currencyCode: 'ARS',
  })!;

  it('builds the mock live summary lines', () => {
    const lines = installmentSummaryLines(preview, { currencyCode: 'ARS', hasInterest: false, cashPrice: '' });
    expect(lines.main).toBe('6 cuotas de $30.000 / mes');
    expect(lines.total).toBe('$180.000');
    expect(lines.range).toBe('Dic 2026 → May 2027');
    expect(lines.tag).toBe('Sin interés');
  });

  it('shows the real interest over the cash price when known', () => {
    const lines = installmentSummaryLines(preview, { currencyCode: 'ARS', hasInterest: true, cashPrice: '150000' });
    expect(lines.tag).toBe('Interés real: +$30.000 (+20,0% sobre contado)');
  });

  it('asks for the cash price when interest has no reference', () => {
    const lines = installmentSummaryLines(preview, { currencyCode: 'ARS', hasInterest: true, cashPrice: '' });
    expect(lines.tag).toBe('Con interés · completá precio contado para ver el % de recargo');
  });
});
