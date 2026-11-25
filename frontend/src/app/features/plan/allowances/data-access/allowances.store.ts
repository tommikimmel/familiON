import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { extractProblemMessage } from '../utils/problem-message';
import { AllowancesApi } from './allowances.api';
import {
  AccountOption,
  Allowance,
  AllowanceCreate,
  AllowancePatch,
  DisbursePayload,
  DisburseResult,
  FamilyMemberSummary,
} from './allowances.models';

@Injectable({ providedIn: 'root' })
export class AllowancesStore {
  private readonly api = inject(AllowancesApi);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _allowances = signal<Allowance[]>([]);
  private readonly _members = signal<FamilyMemberSummary[]>([]);
  private readonly _accounts = signal<AccountOption[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  private loadSeq = 0;

  readonly allowances: Signal<Allowance[]> = this._allowances.asReadonly();
  readonly members: Signal<FamilyMemberSummary[]> = this._members.asReadonly();
  readonly accounts: Signal<AccountOption[]> = this._accounts.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  readonly children: Signal<FamilyMemberSummary[]> = computed(() =>
    this._members().filter((m) => m.role === 'HIJO'),
  );

  readonly activeAllowances: Signal<Allowance[]> = computed(() =>
    this._allowances().filter((a) => a.isActive),
  );

  async loadAll(): Promise<void> {
    const requestId = ++this.loadSeq;
    this._loading.set(true);
    this._error.set(null);

    try {
      const [allowanceList, memberList, accountList] = await Promise.all([
        firstValueFrom(this.api.list()),
        firstValueFrom(this.api.members()).catch(() => [] as FamilyMemberSummary[]),
        firstValueFrom(this.api.familyAccounts()).catch(() => [] as AccountOption[]),
      ]);

      if (requestId === this.loadSeq) {
        this._allowances.set(allowanceList);
        this._members.set(memberList);
        this._accounts.set(accountList);
      }
    } catch (err) {
      if (requestId === this.loadSeq) {
        this._error.set(
          extractProblemMessage(err, 'No pudimos cargar las mensualidades. Intentá de nuevo.'),
        );
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  async create(payload: AllowanceCreate): Promise<Allowance> {
    const created = await firstValueFrom(this.api.create(payload));
    this._allowances.update((current) => [...current, created]);
    return created;
  }

  async update(id: string, patch: AllowancePatch): Promise<Allowance> {
    const updated = await firstValueFrom(this.api.update(id, patch));
    this._allowances.update((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    return updated;
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.api.remove(id));
    this._allowances.update((current) => current.filter((item) => item.id !== id));
  }

  async disburse(id: string, payload: DisbursePayload): Promise<DisburseResult> {
    const result = await firstValueFrom(this.api.disburse(id, payload));
    this._allowances.update((current) =>
      current.map((item) => (item.id === result.allowance.id ? result.allowance : item)),
    );
    this.financeEvents.emit('transaction-created');
    return result;
  }
}
