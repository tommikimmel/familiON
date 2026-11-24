import { TransactionItem } from '../../../core/finance/transactions.models';
import {
  buildTransactionPatch,
  formatDayGroupLabel,
  formatReferenceSnapshot,
  getTransactionEditCapabilities,
  groupTransactionsByDay,
} from './transactions.grouping';

function makeTx(overrides: Partial<TransactionItem> = {}): TransactionItem {
  return {
    id: overrides.id ?? 'tx-1',
    type: overrides.type ?? 'EXPENSE',
    scope: overrides.scope ?? 'PERSONAL',
    status: overrides.status ?? 'POSTED',
    amount: overrides.amount ?? '1000.0000',
    currencyCode: overrides.currencyCode ?? 'ARS',
    transactionDate: overrides.transactionDate ?? '2026-11-24',
    description: overrides.description ?? 'Compra',
    notes: overrides.notes ?? null,
    category: overrides.category ?? {
      id: 'cat-1',
      name: 'Comida',
      icon: 'shopping-cart',
      colorHex: '#0f766e',
    },
    account: overrides.account ?? {
      id: 'acc-1',
      name: 'Efectivo',
      accountType: 'CASH',
    },
    paidBy: overrides.paidBy ?? { id: 'user-1', fullName: 'Ana' },
    attributedTo: overrides.attributedTo ?? { id: 'user-1', fullName: 'Ana' },
    installment: overrides.installment ?? null,
    lens: overrides.lens ?? 'NOMINAL',
    lensAmount: overrides.lensAmount ?? '1000.0000',
    lensUnit: overrides.lensUnit ?? 'ARS',
    lensApproximated: overrides.lensApproximated ?? false,
  };
}

describe('transactions.grouping', () => {
  describe('formatDayGroupLabel', () => {
    it('formats Hoy, Ayer, same-year weekday date, and different-year date', () => {
      // 2026-11-24 is Tuesday; 2026-11-23 is Monday; 2026-11-16 is Monday
      expect(formatDayGroupLabel('2026-11-24', '2026-11-24')).toBe('Hoy');
      expect(formatDayGroupLabel('2026-11-23', '2026-11-24')).toBe('Ayer');
      expect(formatDayGroupLabel('2026-11-16', '2026-11-24')).toBe('Lunes 16 nov');
      expect(formatDayGroupLabel('2025-11-21', '2026-11-24')).toBe('Viernes 21 nov 2025');
    });
  });

  describe('groupTransactionsByDay', () => {
    it('groups transactions by day preserving order and computes net day totals per currency', () => {
      const items: TransactionItem[] = [
        makeTx({ id: '1', transactionDate: '2026-11-24', type: 'EXPENSE', amount: '1500.0000', currencyCode: 'ARS' }),
        makeTx({ id: '2', transactionDate: '2026-11-24', type: 'INCOME', amount: '5000.0000', currencyCode: 'ARS' }),
        makeTx({ id: '3', transactionDate: '2026-11-24', type: 'EXPENSE', amount: '20.5000', currencyCode: 'USD' }),
        makeTx({ id: '4', transactionDate: '2026-11-23', type: 'TRANSFER_OUT', amount: '10000.0000', currencyCode: 'ARS' }),
        makeTx({ id: '5', transactionDate: '2026-11-16', type: 'EXPENSE', amount: '3200.0000', currencyCode: 'ARS' }),
      ];

      const groups = groupTransactionsByDay(items, 'NOMINAL', '2026-11-24');

      expect(groups).toHaveLength(3);
      expect(groups[0].label).toBe('Hoy');
      expect(groups[0].items.map((i) => i.id)).toEqual(['1', '2', '3']);
      expect(groups[0].totals).toEqual([
        { unit: 'ARS', amount: 3500, approximated: false },
        { unit: 'USD', amount: -20.5, approximated: false },
      ]);

      // Transfers are neutral and do not produce misleading expense totals
      expect(groups[1].label).toBe('Ayer');
      expect(groups[1].totals).toEqual([]);

      expect(groups[2].label).toBe('Lunes 16 nov');
      expect(groups[2].totals).toEqual([
        { unit: 'ARS', amount: -3200, approximated: false },
      ]);
    });

    it('uses lensAmount and lensUnit when lens is not NOMINAL and propagates approximation flag', () => {
      const items: TransactionItem[] = [
        makeTx({
          id: '1',
          transactionDate: '2026-11-24',
          type: 'EXPENSE',
          amount: '15000.0000',
          currencyCode: 'ARS',
          lens: 'REFERENCE_CURRENCY',
          lensAmount: '12.5000',
          lensUnit: 'USD',
          lensApproximated: false,
        }),
        makeTx({
          id: '2',
          transactionDate: '2026-11-24',
          type: 'EXPENSE',
          amount: '30000.0000',
          currencyCode: 'ARS',
          lens: 'REFERENCE_CURRENCY',
          lensAmount: '25.0000',
          lensUnit: 'USD',
          lensApproximated: true,
        }),
      ];

      const groups = groupTransactionsByDay(items, 'REFERENCE_CURRENCY', '2026-11-24');
      expect(groups[0].totals).toEqual([
        { unit: 'USD', amount: -37.5, approximated: true },
      ]);
    });
  });

  describe('formatReferenceSnapshot', () => {
    it('formats implicit reference currency snapshot sentence', () => {
      const snapshot = makeTx({
        currencyCode: 'ARS',
        lens: 'REFERENCE_CURRENCY',
        lensAmount: '25.3000',
        lensUnit: 'USD',
        lensApproximated: false,
      });

      expect(formatReferenceSnapshot(snapshot)).toBe('Equivalía a USD 25,30 ese día');
    });

    it('returns null when transaction is already in the reference currency', () => {
      const snapshot = makeTx({
        currencyCode: 'USD',
        lens: 'REFERENCE_CURRENCY',
        lensAmount: '25.3000',
        lensUnit: 'USD',
      });

      expect(formatReferenceSnapshot(snapshot)).toBeNull();
    });
  });

  describe('buildTransactionPatch', () => {
    it('includes only changed fields', () => {
      const original = makeTx({
        amount: '1000.0000',
        transactionDate: '2026-11-20',
        description: 'Café',
        notes: 'Con medialunas',
        scope: 'PERSONAL',
      });
      const caps = getTransactionEditCapabilities(original, true);

      const patch = buildTransactionPatch(
        original,
        {
          amount: '1000',
          transactionDate: '2026-11-20',
          categoryId: 'cat-1',
          description: 'Café con leche',
          notes: 'Con medialunas',
          accountId: 'acc-1',
          attributedToUserId: 'user-1',
          scope: 'PERSONAL',
        },
        caps,
      );

      expect(patch).toEqual({ description: 'Café con leche' });
    });

    it('ignores restricted fields for installment and exchange transactions', () => {
      const installmentTx = makeTx({
        installment: { planId: 'plan-1', number: 2, total: 6 },
      });
      const caps = getTransactionEditCapabilities(installmentTx, true);

      expect(caps.canEditAmount).toBe(false);
      expect(caps.canEditDate).toBe(false);
      expect(caps.canEditCategory).toBe(true);

      const patch = buildTransactionPatch(
        installmentTx,
        {
          amount: '9999',
          transactionDate: '2026-12-01',
          categoryId: 'cat-2',
          description: 'Compra',
          notes: 'Nota nueva',
          accountId: 'acc-other',
          attributedToUserId: 'user-2',
          scope: 'FAMILY',
        },
        caps,
      );

      expect(patch).toEqual({ categoryId: 'cat-2', notes: 'Nota nueva' });
    });
  });
});
