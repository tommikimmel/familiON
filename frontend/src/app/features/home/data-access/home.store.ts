import { BreakpointObserver } from '@angular/cdk/layout';
import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, Signal, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { EMPTY, Subject, catchError, map, switchMap, tap } from 'rxjs';
import { ProblemDetail } from '../../../core/api/problem-detail';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import { ViewContextQueryParams, ViewContextStore } from '../../../core/state/view-context.store';
import { HomeApi } from './home.api';
import { buildDemoHomePayload, buildDemoSeries } from './home.fixtures';
import { HomePayload, Series } from './home.models';

const DESKTOP_BREAKPOINT = '(min-width: 1024px)';
const DEFAULT_LOAD_ERROR = 'No pudimos cargar tu resumen de inicio. Intentá nuevamente.';

interface HomeTrigger {
  params: ViewContextQueryParams;
  demo: boolean;
}

interface SeriesTrigger {
  params: Pick<ViewContextQueryParams, 'view' | 'lens' | 'date'>;
  demo: boolean;
  desktop: boolean;
}

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
export class HomeStore {
  private readonly api = inject(HomeApi);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);
  private readonly notificationBadge = inject(NotificationBadgeService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly route = inject(ActivatedRoute, { optional: true });

  private readonly _payload = signal<HomePayload | null>(null);
  private readonly _series = signal<Series | null>(null);
  private readonly _loading = signal<boolean>(false);
  private readonly _seriesLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _demoMode = signal<boolean>(
    this.route?.snapshot?.queryParamMap?.get('demo') === '1',
  );
  private readonly _reloadTick = signal<number>(0);

  private readonly homeTrigger$ = new Subject<HomeTrigger>();
  private readonly seriesTrigger$ = new Subject<SeriesTrigger>();

  readonly payload: Signal<HomePayload | null> = this._payload.asReadonly();
  readonly series: Signal<Series | null> = this._series.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly seriesLoading: Signal<boolean> = this._seriesLoading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly demoMode: Signal<boolean> = this._demoMode.asReadonly();

  readonly isDesktop: Signal<boolean> = toSignal(
    this.breakpointObserver
      .observe(DESKTOP_BREAKPOINT)
      .pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_BREAKPOINT) },
  );

  private readonly seriesParams = computed(
    () => {
      const current = this.viewContext.queryParams();
      return {
        view: current.view,
        lens: current.lens,
        date: current.date,
      };
    },
    {
      equal: (a, b) => a.view === b.view && a.lens === b.lens && a.date === b.date,
    },
  );

  constructor() {
    this.route?.queryParamMap.pipe(takeUntilDestroyed()).subscribe((queryMap) => {
      this._demoMode.set(queryMap.get('demo') === '1');
    });

    this.homeTrigger$
      .pipe(
        switchMap(({ params, demo }) => {
          if (demo) {
            const fixture = buildDemoHomePayload(params);
            this._error.set(null);
            this._loading.set(false);
            this._payload.set(fixture);
            if (fixture.unreadNotifications !== null) {
              this.notificationBadge.set(fixture.unreadNotifications);
            }
            return EMPTY;
          }

          this._loading.set(true);
          this._error.set(null);

          return this.api.getHome(params).pipe(
            tap((data) => {
              this._payload.set(data);
              this._loading.set(false);
              if (data.unreadNotifications !== null && data.unreadNotifications !== undefined) {
                this.notificationBadge.set(data.unreadNotifications);
              }
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

    this.seriesTrigger$
      .pipe(
        switchMap(({ params, demo, desktop }) => {
          if (!desktop) {
            this._series.set(null);
            this._seriesLoading.set(false);
            return EMPTY;
          }

          if (demo) {
            this._seriesLoading.set(false);
            this._series.set(buildDemoSeries(params));
            return EMPTY;
          }

          this._seriesLoading.set(true);

          return this.api.getSeries(params, 6).pipe(
            tap((data) => {
              this._series.set(data);
              this._seriesLoading.set(false);
            }),
            catchError(() => {
              this._series.set(null);
              this._seriesLoading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();

    effect(() => {
      const params = this.viewContext.queryParams();
      this.financeEvents.last();
      this._reloadTick();
      const demo = this._demoMode();

      this.homeTrigger$.next({ params, demo });
    });

    effect(() => {
      const params = this.seriesParams();
      this.financeEvents.last();
      this._reloadTick();
      const desktop = this.isDesktop();
      const demo = this._demoMode();

      this.seriesTrigger$.next({ params, demo, desktop });
    });
  }

  reload(): void {
    this._reloadTick.update((tick) => tick + 1);
  }

  setDemoMode(enabled: boolean): void {
    this._demoMode.set(enabled);
  }
}
