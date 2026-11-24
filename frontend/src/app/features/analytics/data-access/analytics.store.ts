import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, Signal, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, catchError, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { ProblemDetail } from '../../../core/api/problem-detail';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { View, ViewContextQueryParams, ViewContextStore } from '../../../core/state/view-context.store';
import { AnalyticsApi } from './analytics.api';
import { getMonthDateRange, shiftCalendarYearMonth } from './analytics.logic';
import {
  AnalyticsSummary,
  AnalyticsTab,
  CalendarView,
  CalendarYearMonth,
  CategoryBreakdown,
  PersonBreakdown,
  Series,
} from './analytics.models';

const DEFAULT_ANALYTICS_ERROR = 'No pudimos cargar los datos de analítica. Intentá nuevamente.';
const DEFAULT_CALENDAR_ERROR = 'No pudimos cargar el calendario del mes. Intentá nuevamente.';
const SERIES_PERIODS = 12;

interface SafeResult<T> {
  data: T | null;
  error: unknown;
  forbidden?: boolean;
}

interface CalendarTrigger {
  view: View;
  month: CalendarYearMonth;
}

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ProblemDetail> | null | undefined;
    if (body && typeof body.detail === 'string' && body.detail.trim().length > 0) {
      return body.detail.trim();
    }
    if (body && typeof body.title === 'string' && body.title.trim().length > 0) {
      return body.title.trim();
    }
  }
  return fallback;
}

function getCurrentYearMonth(): CalendarYearMonth {
  const now = new Date();
  return {
    year: now.getFullYear(),
    month: now.getMonth() + 1,
  };
}

function wrapCall<T>(obs: Observable<T>): Observable<SafeResult<T>> {
  return obs.pipe(
    map((data): SafeResult<T> => ({ data, error: null })),
    catchError((error: unknown) =>
      of<SafeResult<T>>({
        data: null,
        error,
        forbidden: error instanceof HttpErrorResponse && error.status === 403,
      }),
    ),
  );
}

@Injectable()
export class AnalyticsStore {
  private readonly api = inject(AnalyticsApi);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _summary = signal<AnalyticsSummary | null>(null);
  private readonly _series = signal<Series | null>(null);
  private readonly _byCategory = signal<CategoryBreakdown | null>(null);
  private readonly _byPerson = signal<PersonBreakdown | null>(null);
  private readonly _personForbidden = signal<boolean>(false);
  private readonly _loading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  private readonly _activeTab = signal<AnalyticsTab>('OVERVIEW');
  private readonly _calendarMonth = signal<CalendarYearMonth>(getCurrentYearMonth());
  private readonly _calendar = signal<CalendarView | null>(null);
  private readonly _calendarLoading = signal<boolean>(false);
  private readonly _calendarError = signal<string | null>(null);

  private readonly _reloadTick = signal<number>(0);
  private readonly _calendarReloadTick = signal<number>(0);

  private readonly overviewTrigger$ = new Subject<ViewContextQueryParams>();
  private readonly calendarTrigger$ = new Subject<CalendarTrigger>();

  readonly summary: Signal<AnalyticsSummary | null> = this._summary.asReadonly();
  readonly series: Signal<Series | null> = this._series.asReadonly();
  readonly byCategory: Signal<CategoryBreakdown | null> = this._byCategory.asReadonly();
  readonly byPerson: Signal<PersonBreakdown | null> = this._byPerson.asReadonly();
  readonly personForbidden: Signal<boolean> = this._personForbidden.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  readonly activeTab: Signal<AnalyticsTab> = this._activeTab.asReadonly();
  readonly calendarMonth: Signal<CalendarYearMonth> = this._calendarMonth.asReadonly();
  readonly calendar: Signal<CalendarView | null> = this._calendar.asReadonly();
  readonly calendarLoading: Signal<boolean> = this._calendarLoading.asReadonly();
  readonly calendarError: Signal<string | null> = this._calendarError.asReadonly();

  readonly showPersonSection: Signal<boolean> = computed(
    () => this.viewContext.view() === 'FAMILY' && !this._personForbidden(),
  );

  constructor() {
    this.overviewTrigger$
      .pipe(
        switchMap((params) => {
          this._loading.set(true);
          this._error.set(null);

          const isFamily = params.view === 'FAMILY';
          const byPerson$ = isFamily
            ? wrapCall(this.api.getByPerson(params))
            : of<SafeResult<PersonBreakdown>>({ data: null, error: null, forbidden: false });

          return forkJoin({
            summary: wrapCall(this.api.getSummary(params)),
            series: wrapCall(this.api.getSeries(params, SERIES_PERIODS)),
            byCategory: wrapCall(this.api.getByCategory(params)),
            byPerson: byPerson$,
          }).pipe(
            tap(({ summary, series, byCategory, byPerson }) => {
              this._summary.set(summary.data);
              this._series.set(series.data);
              this._byCategory.set(byCategory.data);

              if (!isFamily) {
                this._byPerson.set(null);
                this._personForbidden.set(false);
              } else if (byPerson.forbidden) {
                this._byPerson.set(null);
                this._personForbidden.set(true);
              } else {
                this._byPerson.set(byPerson.data);
                this._personForbidden.set(false);
              }

              const coreAllFailed =
                summary.data === null && series.data === null && byCategory.data === null;
              if (coreAllFailed) {
                const firstErr = summary.error ?? series.error ?? byCategory.error;
                this._error.set(extractErrorMessage(firstErr, DEFAULT_ANALYTICS_ERROR));
              } else {
                this._error.set(null);
              }

              this._loading.set(false);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();

    this.calendarTrigger$
      .pipe(
        switchMap(({ view, month }) => {
          this._calendarLoading.set(true);
          this._calendarError.set(null);

          const { from, to } = getMonthDateRange(month.year, month.month);
          return this.api.getCalendar(view, from, to).pipe(
            tap((data) => {
              this._calendar.set(data);
              this._calendarLoading.set(false);
            }),
            catchError((err: unknown) => {
              this._calendar.set(null);
              this._calendarError.set(extractErrorMessage(err, DEFAULT_CALENDAR_ERROR));
              this._calendarLoading.set(false);
              return of(null);
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

      this.overviewTrigger$.next(params);
    });

    effect(() => {
      const tab = this._activeTab();
      const view = this.viewContext.view();
      const month = this._calendarMonth();
      this.financeEvents.last();
      this._calendarReloadTick();

      if (tab === 'CALENDAR') {
        this.calendarTrigger$.next({ view, month });
      }
    });
  }

  reload(): void {
    this._reloadTick.update((tick) => tick + 1);
  }

  setActiveTab(tab: AnalyticsTab): void {
    this._activeTab.set(tab);
  }

  shiftCalendarMonth(delta: -1 | 1): void {
    this._calendarMonth.update((current) => shiftCalendarYearMonth(current, delta));
  }

  setCalendarMonth(year: number, month: number): void {
    this._calendarMonth.set({ year, month });
  }

  resetCalendarMonth(): void {
    this._calendarMonth.set(getCurrentYearMonth());
  }

  reloadCalendar(): void {
    this._calendarReloadTick.update((tick) => tick + 1);
  }
}
