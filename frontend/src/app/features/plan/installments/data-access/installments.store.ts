import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AccountsStore } from '../../../../core/finance/accounts.store';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { View, ViewContextStore } from '../../../../core/state/view-context.store';
import { toNumber } from '../../../../shared/utils/decimal';
import {
  extractProblemMessage,
  sumCommitmentByCurrency,
} from '../utils/installments-format';
import { InstallmentsApi } from './installments.api';
import {
  CancelRemainingPayload,
  CurrencyPendingTotal,
  InstallmentCommitment,
  InstallmentPlan,
  InstallmentPlanDetail,
  InstallmentPlanStatus,
} from './installments.models';

const COMMITMENT_MONTHS = 12;

function sumRemainingByCurrency(plans: readonly InstallmentPlan[]): CurrencyPendingTotal[] {
  const totals = new Map<string, number>();
  for (const plan of plans) {
    const code = plan.currencyCode.trim().toUpperCase();
    totals.set(code, (totals.get(code) ?? 0) + toNumber(plan.remainingAmount));
  }
  return Array.from(totals.entries())
    .filter(([, amount]) => amount > 0)
    .map(([currencyCode, amount]) => ({ currencyCode, amount }));
}

@Injectable({ providedIn: 'root' })
export class InstallmentsStore {
  private readonly api = inject(InstallmentsApi);
  private readonly accountsStore = inject(AccountsStore);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _status = signal<InstallmentPlanStatus>('ACTIVE');
  private readonly _plans = signal<InstallmentPlan[]>([]);
  private readonly _commitment = signal<InstallmentCommitment[]>([]);
  private readonly _activePendingTotals = signal<CurrencyPendingTotal[] | null>(null);
  private readonly _selectedDetail = signal<InstallmentPlanDetail | null>(null);
  private readonly _loading = signal<boolean>(false);
  private readonly _listLoading = signal<boolean>(false);
  private readonly _detailLoading = signal<boolean>(false);
  private readonly _mutating = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _detailError = signal<string | null>(null);

  private loadSeq = 0;
  private listSeq = 0;
  private detailSeq = 0;

  readonly status: Signal<InstallmentPlanStatus> = this._status.asReadonly();
  readonly plans: Signal<InstallmentPlan[]> = this._plans.asReadonly();
  readonly commitment: Signal<InstallmentCommitment[]> = this._commitment.asReadonly();
  readonly selectedDetail: Signal<InstallmentPlanDetail | null> = this._selectedDetail.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly listLoading: Signal<boolean> = this._listLoading.asReadonly();
  readonly detailLoading: Signal<boolean> = this._detailLoading.asReadonly();
  readonly mutating: Signal<boolean> = this._mutating.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly detailError: Signal<string | null> = this._detailError.asReadonly();

  readonly pendingTotals: Signal<CurrencyPendingTotal[]> = computed(() => {
    const activeTotals = this._activePendingTotals();
    if (activeTotals !== null) {
      return activeTotals;
    }
    return sumCommitmentByCurrency(this._commitment());
  });

  async load(view?: View, status?: InstallmentPlanStatus): Promise<void> {
    const targetView = view ?? this.viewContext.view();
    const targetStatus = status ?? this._status();
    this._status.set(targetStatus);

    const requestId = ++this.loadSeq;
    const listRequestId = ++this.listSeq;
    this._loading.set(true);
    this._error.set(null);

    try {
      const [plans, commitment] = await Promise.all([
        firstValueFrom(this.api.list(targetView, targetStatus)),
        firstValueFrom(this.api.commitment(targetView, COMMITMENT_MONTHS)),
      ]);

      if (requestId === this.loadSeq) {
        this._plans.set(plans);
        this._commitment.set(commitment);
        if (targetStatus === 'ACTIVE') {
          this._activePendingTotals.set(sumRemainingByCurrency(plans));
        } else {
          this._activePendingTotals.set(null);
        }
      }
    } catch (err) {
      if (requestId === this.loadSeq && listRequestId === this.listSeq) {
        this._error.set(extractProblemMessage(err, 'No pudimos cargar tus compras en cuotas.'));
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  async setStatus(status: InstallmentPlanStatus, view?: View): Promise<void> {
    this._status.set(status);
    const targetView = view ?? this.viewContext.view();
    const requestId = ++this.listSeq;
    this._listLoading.set(true);
    this._error.set(null);

    try {
      const plans = await firstValueFrom(this.api.list(targetView, status));
      if (requestId === this.listSeq) {
        this._plans.set(plans);
        if (status === 'ACTIVE') {
          this._activePendingTotals.set(sumRemainingByCurrency(plans));
        }
      }
    } catch (err) {
      if (requestId === this.listSeq) {
        this._error.set(extractProblemMessage(err, 'No pudimos cargar tus compras en cuotas.'));
      }
    } finally {
      if (requestId === this.listSeq) {
        this._listLoading.set(false);
      }
    }
  }

  async loadDetail(id: string): Promise<void> {
    const requestId = ++this.detailSeq;
    this._detailLoading.set(true);
    this._detailError.set(null);

    try {
      const detail = await firstValueFrom(this.api.getById(id));
      if (requestId === this.detailSeq) {
        this._selectedDetail.set(detail);
      }
    } catch (err) {
      if (requestId === this.detailSeq) {
        this._selectedDetail.set(null);
        this._detailError.set(
          extractProblemMessage(err, 'No pudimos cargar el detalle de este plan.'),
        );
      }
    } finally {
      if (requestId === this.detailSeq) {
        this._detailLoading.set(false);
      }
    }
  }

  clearDetail(): void {
    this.detailSeq++;
    this._selectedDetail.set(null);
    this._detailError.set(null);
    this._detailLoading.set(false);
  }

  async cancelRemaining(id: string, payload: CancelRemainingPayload): Promise<InstallmentPlan> {
    this._mutating.set(true);
    try {
      const updated = await firstValueFrom(this.api.cancelRemaining(id, payload));
      const targetView = this.viewContext.view();
      await this.refreshAfterMutation(targetView, this._status());
      this.financeEvents.emit('plan-changed');
      return updated;
    } finally {
      this._mutating.set(false);
    }
  }

  private async refreshAfterMutation(view: View, status: InstallmentPlanStatus): Promise<void> {
    const requestId = ++this.loadSeq;
    const listRequestId = ++this.listSeq;
    try {
      const [plans, commitment] = await Promise.all([
        firstValueFrom(this.api.list(view, status)),
        firstValueFrom(this.api.commitment(view, COMMITMENT_MONTHS)),
        this.reloadAccountsSafely(view),
      ]);
      if (requestId === this.loadSeq && listRequestId === this.listSeq) {
        this._plans.set(plans);
        this._commitment.set(commitment);
        if (status === 'ACTIVE') {
          this._activePendingTotals.set(sumRemainingByCurrency(plans));
        } else {
          this._activePendingTotals.set(null);
        }
        this._error.set(null);
      }
    } catch {
      // Keep mutation success intact; next reload will recover view state.
    }
  }

  private async reloadAccountsSafely(view: View): Promise<void> {
    try {
      await this.accountsStore.load(view);
    } catch {
      // Non-blocking: account balances refresh best-effort after installment mutation.
    }
  }
}
