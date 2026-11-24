import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccountsStore } from '../../../../core/finance/accounts.store';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { ViewContextStore } from '../../../../core/state/view-context.store';
import { toNumber } from '../../../../shared/utils/decimal';
import {
  currentYearMonth,
  extractProblemMessage,
  shiftYearMonth,
} from '../utils/recurring-format';
import { RecurringApi } from './recurring.api';
import {
  ConfirmPaymentPayload,
  RecurringCurrencySummary,
  RecurringExpense,
  RecurringExpenseCreate,
  RecurringExpensePatch,
} from './recurring.models';

@Injectable({ providedIn: 'root' })
export class RecurringStore {
  private readonly api = inject(RecurringApi);
  private readonly accountsStore = inject(AccountsStore);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _month = signal<string>(currentYearMonth());
  private readonly _items = signal<RecurringExpense[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  private loadSeq = 0;

  readonly month: Signal<string> = this._month.asReadonly();
  readonly items: Signal<RecurringExpense[]> = this._items.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  readonly visibleItems: Signal<RecurringExpense[]> = computed(() =>
    this._items()
      .filter((item) => item.month.status !== 'NOT_APPLICABLE')
      .sort((a, b) => {
        const dateCmp = a.month.dueDate.localeCompare(b.month.dueDate);
        if (dateCmp !== 0) {
          return dateCmp;
        }
        if (a.dueDay !== b.dueDay) {
          return a.dueDay - b.dueDay;
        }
        return a.name.localeCompare(b.name, 'es');
      }),
  );

  readonly summaryByCurrency: Signal<RecurringCurrencySummary[]> = computed(() => {
    const map = new Map<string, RecurringCurrencySummary>();

    for (const item of this.visibleItems()) {
      const code = item.currencyCode.trim().toUpperCase();
      const existing = map.get(code) ?? {
        currencyCode: code,
        estimatedTotal: 0,
        paidTotal: 0,
        pendingTotal: 0,
      };

      const estimated = toNumber(item.month.estimatedAmount);
      existing.estimatedTotal += estimated;

      if (item.month.status === 'PAID') {
        const paid =
          item.month.paidAmount !== null ? toNumber(item.month.paidAmount) : estimated;
        existing.paidTotal += paid;
      } else {
        existing.pendingTotal += estimated;
      }

      map.set(code, existing);
    }

    return Array.from(map.values());
  });

  readonly paidCount: Signal<number> = computed(
    () => this.visibleItems().filter((item) => item.month.status === 'PAID').length,
  );

  readonly overdueCount: Signal<number> = computed(
    () => this.visibleItems().filter((item) => item.month.status === 'OVERDUE').length,
  );

  async load(month?: string): Promise<void> {
    const targetMonth = month ?? this._month();
    this._month.set(targetMonth);

    const requestId = ++this.loadSeq;
    this._loading.set(true);
    this._error.set(null);

    try {
      const list = await firstValueFrom(this.api.list(targetMonth));
      if (requestId === this.loadSeq) {
        this._items.set(list);
      }
    } catch (err) {
      if (requestId === this.loadSeq) {
        this._error.set(extractProblemMessage(err, 'No pudimos cargar tus gastos fijos.'));
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  async shiftMonth(delta: -1 | 1): Promise<void> {
    const nextMonth = shiftYearMonth(this._month(), delta);
    await this.load(nextMonth);
  }

  async setMonth(month: string): Promise<void> {
    await this.load(month);
  }

  async create(payload: RecurringExpenseCreate): Promise<RecurringExpense> {
    const created = await firstValueFrom(this.api.create(payload));
    await this.refreshSilently(this._month());
    this.financeEvents.emit('plan-changed');
    return created;
  }

  async update(id: string, patch: RecurringExpensePatch): Promise<RecurringExpense> {
    const updated = await firstValueFrom(this.api.update(id, patch));
    await this.refreshSilently(this._month());
    this.financeEvents.emit('plan-changed');
    return updated;
  }

  async archive(id: string): Promise<void> {
    await firstValueFrom(this.api.archive(id));
    this._items.update((current) => current.filter((item) => item.id !== id));
    await this.refreshSilently(this._month());
    this.financeEvents.emit('plan-changed');
  }

  async confirmPayment(
    id: string,
    payload: ConfirmPaymentPayload,
  ): Promise<RecurringExpense> {
    const updated = await firstValueFrom(this.api.confirmPayment(id, payload));
    this._items.update((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    await this.reloadAccountsSafely();
    this.financeEvents.emit('transaction-created');
    return updated;
  }

  private async refreshSilently(month: string): Promise<void> {
    const requestId = ++this.loadSeq;
    try {
      const list = await firstValueFrom(this.api.list(month));
      if (requestId === this.loadSeq) {
        this._items.set(list);
        this._error.set(null);
      }
    } catch {
      // Keep mutation success intact; next reload will recover view state.
    }
  }

  private async reloadAccountsSafely(): Promise<void> {
    try {
      await this.accountsStore.load(this.viewContext.view());
    } catch {
      // Non-blocking: account balances refresh best-effort after payment confirmation.
    }
  }
}
