import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import {
  CalendarGridCell,
  WEEKDAY_LABELS,
  buildCalendarGrid,
  formatCalendarFullDate,
  formatCalendarMonthTitle,
  formatCompactAxisValue,
  formatLocalIsoDate,
  getCalendarItemMeta,
} from '../data-access/analytics.logic';
import { CalendarItemKind, CalendarView, CalendarYearMonth } from '../data-access/analytics.models';

@Component({
  selector: 'fo-analytics-calendar',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe, SkeletonComponent],
  template: `
    <div class="space-y-4">
      <!-- Independent month navigation bar -->
      <fo-card padding="md">
        <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div class="flex items-center gap-2">
            <div
              class="flex items-center gap-1 rounded-full border border-border bg-surface-2/70 p-1"
            >
              <button
                type="button"
                data-testid="calendar-prev-month"
                aria-label="Mes anterior"
                (click)="shiftMonth.emit(-1)"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
              >
                <fo-icon name="chevron-left" [size]="16" />
              </button>

              <h2
                data-testid="calendar-month-title"
                class="min-w-36 px-2 text-center text-sm font-semibold text-text sm:text-base"
              >
                {{ monthTitle() }}
              </h2>

              <button
                type="button"
                data-testid="calendar-next-month"
                aria-label="Mes siguiente"
                (click)="shiftMonth.emit(1)"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
              >
                <fo-icon name="chevron-right" [size]="16" />
              </button>
            </div>

            <fo-button
              data-testid="calendar-today-btn"
              variant="ghost"
              size="sm"
              (click)="resetToday.emit()"
            >
              Hoy
            </fo-button>
          </div>

          <div class="flex flex-wrap items-center gap-3 text-xs text-muted" aria-hidden="true">
            <span class="inline-flex items-center gap-1.5">
              <span class="h-2 w-2 rounded-full bg-success"></span>
              Ingresos
            </span>
            <span class="inline-flex items-center gap-1.5">
              <span class="h-2 w-2 rounded-full bg-danger"></span>
              Gastos
            </span>
            <span class="inline-flex items-center gap-1.5">
              <span class="h-2 w-2 rounded-full bg-warning"></span>
              Vencimientos
            </span>
          </div>
        </div>
      </fo-card>

      @if (loading()) {
        <fo-card padding="md" data-testid="calendar-loading-skeleton">
          <div class="space-y-3" aria-busy="true">
            <div class="grid grid-cols-7 gap-2">
              @for (day of weekdayLabels; track day) {
                <fo-skeleton height="1rem" />
              }
            </div>
            @for (row of [1, 2, 3, 4, 5]; track row) {
              <div class="grid grid-cols-7 gap-2">
                @for (col of [1, 2, 3, 4, 5, 6, 7]; track col) {
                  <fo-skeleton height="4rem" rounded="md" />
                }
              </div>
            }
          </div>
        </fo-card>
      } @else if (error(); as errorMessage) {
        <fo-card padding="lg" data-testid="calendar-error-state">
          <div class="flex flex-col items-center py-6 text-center">
            <div
              class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-danger/12 text-danger"
            >
              <fo-icon name="alert-circle" [size]="24" />
            </div>
            <h3 class="text-base font-semibold text-text">No pudimos cargar el calendario</h3>
            <p class="mt-1 max-w-sm text-sm text-muted">{{ errorMessage }}</p>
            <div class="mt-4">
              <fo-button data-testid="calendar-retry-btn" (click)="retry.emit()">
                Reintentar
              </fo-button>
            </div>
          </div>
        </fo-card>
      } @else {
        <div class="grid grid-cols-1 gap-5 lg:grid-cols-12">
          <!-- Monthly grid (Monday first) -->
          <div class="lg:col-span-8">
            <fo-card padding="md">
              <div class="grid grid-cols-7 gap-1 pb-2 text-center">
                @for (weekday of weekdayLabels; track weekday) {
                  <span class="text-[11px] font-semibold tracking-wide text-muted uppercase">
                    {{ weekday }}
                  </span>
                }
              </div>

              <div data-testid="calendar-grid" class="space-y-1.5">
                @for (week of weeks(); track $index) {
                  <div data-testid="calendar-week-row" class="grid grid-cols-7 gap-1 sm:gap-1.5">
                    @for (cell of week; track $index) {
                      @if (cell) {
                        @let isSelected = activeCell()?.date === cell.date;
                        <button
                          type="button"
                          data-testid="calendar-day-cell"
                          [attr.data-date]="cell.date"
                          [attr.aria-pressed]="isSelected"
                          [attr.aria-label]="dayAriaLabel(cell)"
                          (click)="selectDate(cell.date)"
                          class="fo-focus-ring flex min-h-14 cursor-pointer flex-col justify-between rounded-xl border p-1.5 text-left transition-colors duration-150 sm:min-h-20 sm:p-2"
                          [class.border-accent]="isSelected"
                          [class.bg-accent-soft]="isSelected"
                          [class.border-border]="!isSelected"
                          [class.bg-surface]="!isSelected"
                          [class.hover:bg-surface-2]="!isSelected"
                        >
                          <div class="flex items-center justify-between gap-1">
                            <span
                              class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold tabular-nums"
                              [class.bg-accent]="cell.isToday"
                              [class.text-accent-contrast]="cell.isToday"
                              [class.text-text]="!cell.isToday"
                            >
                              {{ cell.dayNumber }}
                            </span>

                            @if (cell.hasDueItems) {
                              <span
                                data-testid="calendar-due-dot"
                                class="h-2 w-2 shrink-0 rounded-full bg-warning"
                                [attr.title]="cell.dueCount + ' vencimiento(s)'"
                                aria-hidden="true"
                              ></span>
                            }
                          </div>

                          <!-- Totals on sm+ screens -->
                          <div class="mt-1 hidden space-y-0.5 tabular-nums sm:block">
                            @if (cell.income > 0) {
                              <span class="block truncate text-[10px] font-medium text-success">
                                +{{ formatCompact(cell.income) }}
                              </span>
                            }
                            @if (cell.expense > 0) {
                              <span class="block truncate text-[10px] font-medium text-danger">
                                &minus;{{ formatCompact(cell.expense) }}
                              </span>
                            }
                          </div>

                          <!-- Compact indicator dots on small mobile -->
                          <div class="mt-1 flex items-center gap-1 sm:hidden" aria-hidden="true">
                            @if (cell.income > 0) {
                              <span class="h-1.5 w-1.5 rounded-full bg-success"></span>
                            }
                            @if (cell.expense > 0) {
                              <span class="h-1.5 w-1.5 rounded-full bg-danger"></span>
                            }
                          </div>
                        </button>
                      } @else {
                        <div
                          data-testid="calendar-empty-cell"
                          aria-hidden="true"
                          class="min-h-14 rounded-xl bg-surface-2/40 sm:min-h-20"
                        ></div>
                      }
                    }
                  </div>
                }
              </div>
            </fo-card>
          </div>

          <!-- Selected day detail -->
          <div class="lg:col-span-4">
            <fo-card padding="md" data-testid="calendar-day-detail" class="h-full">
              @if (activeCell(); as selected) {
                <div class="space-y-4">
                  <div class="border-b border-border/80 pb-3">
                    <h3 class="text-base font-semibold text-text">
                      {{ formatFullDate(selected.date) }}
                    </h3>
                    <div class="mt-2 flex flex-wrap items-center gap-3 text-xs tabular-nums">
                      <span class="text-muted">
                        Ingresos:
                        <strong class="font-semibold text-success">
                          {{ selected.income | money: baseCurrency() }}
                        </strong>
                      </span>
                      <span class="text-muted">
                        Gastos:
                        <strong class="font-semibold text-danger">
                          {{ selected.expense | money: baseCurrency() }}
                        </strong>
                      </span>
                    </div>
                  </div>

                  @if (selected.items.length === 0) {
                    <div class="flex flex-col items-center py-8 text-center">
                      <div
                        class="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-surface-2 text-muted"
                      >
                        <fo-icon name="calendar-range" [size]="18" />
                      </div>
                      <p class="text-sm text-muted">
                        Sin movimientos ni vencimientos para este día.
                      </p>
                    </div>
                  } @else {
                    <ul data-testid="calendar-day-items" class="space-y-2">
                      @for (item of selected.items; track item.refId + '-' + $index) {
                        @let meta = itemMeta(item.kind);
                        <li
                          data-testid="calendar-day-item"
                          class="flex items-start justify-between gap-3 rounded-xl border border-border/80 bg-surface-2/40 px-3 py-2.5"
                        >
                          <div class="flex min-w-0 items-start gap-2.5">
                            <span
                              class="mt-0.5 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
                              [class.bg-warning/15]="meta.isDue"
                              [class.text-warning]="meta.isDue"
                              [class.bg-surface-2]="!meta.isDue"
                              [class.text-accent]="!meta.isDue"
                              aria-hidden="true"
                            >
                              <fo-icon [name]="meta.icon" [size]="15" />
                            </span>

                            <div class="min-w-0">
                              <p class="truncate text-sm font-medium text-text">
                                {{ item.title }}
                              </p>
                              <span
                                class="mt-0.5 inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
                                [class.bg-warning/15]="meta.isDue"
                                [class.text-warning]="meta.isDue"
                                [class.bg-surface-2]="!meta.isDue"
                                [class.text-muted]="!meta.isDue"
                              >
                                {{ meta.label }}
                              </span>
                            </div>
                          </div>

                          @if (item.amount !== null) {
                            <span class="shrink-0 text-sm font-semibold text-text tabular-nums">
                              {{ item.amount | money: item.currencyCode || baseCurrency() }}
                            </span>
                          }
                        </li>
                      }
                    </ul>
                  }
                </div>
              }
            </fo-card>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsCalendarComponent {
  readonly yearMonth = input.required<CalendarYearMonth>();
  readonly calendar = input<CalendarView | null>(null);
  readonly loading = input<boolean>(false);
  readonly error = input<string | null>(null);
  readonly baseCurrency = input<string>('ARS');
  readonly todayIso = input<string>(formatLocalIsoDate(new Date()));

  readonly shiftMonth = output<-1 | 1>();
  readonly resetToday = output<void>();
  readonly retry = output<void>();

  protected readonly weekdayLabels = WEEKDAY_LABELS;
  protected readonly selectedDate = signal<string | null>(null);

  protected readonly monthTitle = computed(() => {
    const ym = this.yearMonth();
    return formatCalendarMonthTitle(ym.year, ym.month);
  });

  readonly weeks = computed<(CalendarGridCell | null)[][]>(() => {
    const ym = this.yearMonth();
    return buildCalendarGrid(ym.year, ym.month, this.calendar()?.days ?? [], this.todayIso());
  });

  private readonly populatedCells = computed<CalendarGridCell[]>(() =>
    this.weeks()
      .flat()
      .filter((cell): cell is CalendarGridCell => cell !== null),
  );

  protected readonly activeCell = computed<CalendarGridCell | null>(() => {
    const cells = this.populatedCells();
    if (cells.length === 0) {
      return null;
    }

    const chosen = this.selectedDate();
    if (chosen) {
      const match = cells.find((cell) => cell.date === chosen);
      if (match) {
        return match;
      }
    }

    const today = cells.find((cell) => cell.isToday);
    if (today) {
      return today;
    }

    const firstWithItems = cells.find((cell) => cell.items.length > 0);
    return firstWithItems ?? cells[0];
  });

  protected selectDate(date: string): void {
    this.selectedDate.set(date);
  }

  protected formatCompact(value: number): string {
    return formatCompactAxisValue(value, this.baseCurrency());
  }

  protected formatFullDate(isoDate: string): string {
    return formatCalendarFullDate(isoDate);
  }

  protected itemMeta(kind: CalendarItemKind) {
    return getCalendarItemMeta(kind);
  }

  protected dayAriaLabel(cell: CalendarGridCell): string {
    const parts = [formatCalendarFullDate(cell.date)];
    if (cell.income > 0) {
      parts.push(`ingresos ${this.formatCompact(cell.income)}`);
    }
    if (cell.expense > 0) {
      parts.push(`gastos ${this.formatCompact(cell.expense)}`);
    }
    if (cell.hasDueItems) {
      parts.push(`${cell.dueCount} vencimiento${cell.dueCount === 1 ? '' : 's'}`);
    }
    return parts.join(', ');
  }
}
