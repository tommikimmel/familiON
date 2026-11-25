import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API } from '../../../../core/api/api-config';
import { View } from '../../../../core/state/view-context.store';
import {
  Contribution,
  ContributionCreate,
  GoalStatus,
  SavingsGoal,
  SavingsGoalCreate,
  SavingsGoalPatch,
} from './goals.models';

@Injectable({ providedIn: 'root' })
export class GoalsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API}/savings-goals`;

  list(view: View, status?: GoalStatus | 'ALL'): Observable<SavingsGoal[]> {
    let params = new HttpParams().set('view', view);
    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }
    return this.http.get<SavingsGoal[]>(this.baseUrl, { params });
  }

  create(payload: SavingsGoalCreate): Observable<SavingsGoal> {
    return this.http.post<SavingsGoal>(this.baseUrl, payload);
  }

  update(id: string, patch: SavingsGoalPatch): Observable<SavingsGoal> {
    return this.http.patch<SavingsGoal>(`${this.baseUrl}/${encodeURIComponent(id)}`, patch);
  }

  listContributions(goalId: string): Observable<Contribution[]> {
    return this.http.get<Contribution[]>(
      `${this.baseUrl}/${encodeURIComponent(goalId)}/contributions`,
    );
  }

  createContribution(goalId: string, payload: ContributionCreate): Observable<Contribution> {
    return this.http.post<Contribution>(
      `${this.baseUrl}/${encodeURIComponent(goalId)}/contributions`,
      payload,
    );
  }

  deleteContribution(goalId: string, contributionId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/${encodeURIComponent(goalId)}/contributions/${encodeURIComponent(contributionId)}`,
    );
  }
}
