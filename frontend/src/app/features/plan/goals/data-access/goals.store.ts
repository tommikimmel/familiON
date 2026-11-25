import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { View, ViewContextStore } from '../../../../core/state/view-context.store';
import { extractProblemMessage } from '../utils/problem-message';
import { GoalsApi } from './goals.api';
import {
  Contribution,
  ContributionCreate,
  GoalStatus,
  SavingsGoal,
  SavingsGoalCreate,
  SavingsGoalPatch,
} from './goals.models';

@Injectable({ providedIn: 'root' })
export class GoalsStore {
  private readonly api = inject(GoalsApi);
  private readonly viewContext = inject(ViewContextStore);

  private readonly _goals = signal<SavingsGoal[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _statusFilter = signal<GoalStatus | 'ALL'>('ACTIVE');

  private loadSeq = 0;

  readonly goals: Signal<SavingsGoal[]> = this._goals.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly statusFilter: Signal<GoalStatus | 'ALL'> = this._statusFilter.asReadonly();

  readonly activeGoals: Signal<SavingsGoal[]> = computed(() =>
    this._goals().filter((goal) => goal.status === 'ACTIVE'),
  );

  readonly completedGoals: Signal<SavingsGoal[]> = computed(() =>
    this._goals().filter((goal) => goal.status === 'COMPLETED'),
  );

  readonly filteredGoals: Signal<SavingsGoal[]> = computed(() => {
    const filter = this._statusFilter();
    if (filter === 'ALL') {
      return this._goals();
    }
    return this._goals().filter((goal) => goal.status === filter);
  });

  setStatusFilter(filter: GoalStatus | 'ALL'): void {
    this._statusFilter.set(filter);
  }

  async load(view?: View, status?: GoalStatus | 'ALL'): Promise<void> {
    const targetView = view ?? this.viewContext.view();
    const targetStatus = status ?? this._statusFilter();
    const requestId = ++this.loadSeq;

    this._loading.set(true);
    this._error.set(null);

    try {
      // Fetch without status filter from backend if ALL or let client filter,
      // or pass status to backend if active. If status is provided, pass it.
      const list = await firstValueFrom(this.api.list(targetView, targetStatus));
      if (requestId === this.loadSeq) {
        this._goals.set(list);
      }
    } catch (err) {
      if (requestId === this.loadSeq) {
        this._error.set(
          extractProblemMessage(err, 'No pudimos cargar las metas de ahorro. Intentá de nuevo.'),
        );
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  async create(payload: SavingsGoalCreate): Promise<SavingsGoal> {
    const created = await firstValueFrom(this.api.create(payload));
    this._goals.update((current) => [created, ...current]);
    return created;
  }

  async update(id: string, patch: SavingsGoalPatch): Promise<SavingsGoal> {
    const updated = await firstValueFrom(this.api.update(id, patch));
    this._goals.update((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    return updated;
  }

  async listContributions(goalId: string): Promise<Contribution[]> {
    return firstValueFrom(this.api.listContributions(goalId));
  }

  async addContribution(goalId: string, payload: ContributionCreate): Promise<Contribution> {
    const created = await firstValueFrom(this.api.createContribution(goalId, payload));
    // Reload goals to update accumulated amount, progress, months remaining, etc.
    await this.load(this.viewContext.view(), this._statusFilter());
    return created;
  }

  async deleteContribution(goalId: string, contributionId: string): Promise<void> {
    await firstValueFrom(this.api.deleteContribution(goalId, contributionId));
    // Reload goals to refresh accumulated values
    await this.load(this.viewContext.view(), this._statusFilter());
  }
}
