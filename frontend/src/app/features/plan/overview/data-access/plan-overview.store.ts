import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, Signal, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, switchMap, tap } from 'rxjs';
import { ProblemDetail } from '../../../../core/api/problem-detail';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { ViewContextStore } from '../../../../core/state/view-context.store';
import { extractYearMonth } from '../utils/plan-overview.formatters';
import { PlanOverviewApi, PlanQueryParams } from './plan-overview.api';
import { PlanPayload } from './plan-overview.models';

const DEFAULT_LOAD_ERROR = 'No pudimos cargar tu planificación. Intentá nuevamente.';

function extractErrorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ProblemDetail> | null | undefined;
    if (body && typeof body.detail === 'string' && body.detail.trim().length > 0) {
      return body.detail.trim();
    }
    if (body && typeof body.title === 'string' && body.title.trim().length > 0) {
      return body.title.trim();
    }
  }
  return DEFAULT_LOAD_ERROR;
}

@Injectable()
export class PlanOverviewStore {
  private readonly api = inject(PlanOverviewApi);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _payload = signal<PlanPayload | null>(null);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _reloadTick = signal<number>(0);

  private readonly trigger$ = new Subject<PlanQueryParams>();

  readonly payload: Signal<PlanPayload | null> = this._payload.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  readonly month: Signal<string> = computed(
    () => extractYearMonth(this.viewContext.anchorDate()),
    { equal: (a, b) => a === b },
  );

  constructor() {
    this.trigger$
      .pipe(
        switchMap((params) => {
          this._loading.set(true);
          this._error.set(null);

          return this.api.getPlan(params).pipe(
            tap((data) => {
              this._payload.set(data);
              this._loading.set(false);
            }),
            catchError((err: unknown) => {
              this._payload.set(null);
              this._error.set(extractErrorMessage(err));
              this._loading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();

    effect(() => {
      const view = this.viewContext.view();
      const month = this.month();
      this.financeEvents.last();
      this._reloadTick();

      this.trigger$.next({ view, month });
    });
  }

  reload(): void {
    this._reloadTick.update((tick) => tick + 1);
  }
}
