import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { Granularity } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  formatCompactAxisValue,
  getEvolutionHeading,
  resolveDisplayUnit,
} from '../data-access/analytics.logic';
import { Series } from '../data-access/analytics.models';

export interface AnalyticsChartTick {
  value: number;
  y: number;
  label: string;
}

export interface AnalyticsChartScale {
  min: number;
  max: number;
  zeroY: number;
  ticks: AnalyticsChartTick[];
}

export interface ComputedSeriesPoint {
  periodStart: string;
  label: string;
  income: number;
  expense: number;
  balance: number;
  slotX: number;
  slotWidth: number;
  centerX: number;
  barWidth: number;
  incomeBar: { x: number; y: number; height: number };
  expenseBar: { x: number; y: number; height: number };
  balancePoint: { x: number; y: number };
}

const SVG_WIDTH = 760;
const SVG_HEIGHT = 256;
const PADDING_TOP = 18;
const PADDING_RIGHT = 16;
const PADDING_BOTTOM = 36;
const PADDING_LEFT = 62;
const PLOT_WIDTH = SVG_WIDTH - PADDING_LEFT - PADDING_RIGHT;
const PLOT_HEIGHT = SVG_HEIGHT - PADDING_TOP - PADDING_BOTTOM;
const TICK_COUNT = 4;

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

@Component({
  selector: 'fo-analytics-evolution-chart',
  standalone: true,
  imports: [CardComponent, MoneyPipe],
  template: `
    <fo-card padding="md">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 class="text-base font-semibold text-text">{{ heading().title }}</h2>
          <p class="text-xs text-muted">{{ heading().subtitle }}</p>
        </div>

        <div class="flex items-center gap-3 text-xs text-muted" aria-hidden="true">
          <span class="inline-flex items-center gap-1.5">
            <span class="h-2.5 w-2.5 rounded-xs bg-success/85"></span>
            Ingresos
          </span>
          <span class="inline-flex items-center gap-1.5">
            <span class="h-2.5 w-2.5 rounded-xs bg-danger/80"></span>
            Gastos
          </span>
          <span class="inline-flex items-center gap-1.5">
            <span class="h-0.5 w-3 rounded-full bg-accent"></span>
            Balance
          </span>
        </div>
      </div>

      @if (!series()) {
        <p class="py-12 text-center text-sm text-muted">
          No pudimos cargar la serie de evolución para este período.
        </p>
      } @else if (computedPoints().length === 0) {
        <p class="py-12 text-center text-sm text-muted">
          Sin datos de evolución disponibles para este período.
        </p>
      } @else {
        <div class="mt-4 space-y-3">
          <div class="overflow-x-auto pb-1">
            <svg
              [attr.viewBox]="'0 0 ' + svgWidth + ' ' + svgHeight"
              class="h-64 w-full min-w-[560px] overflow-visible"
              role="img"
              [attr.aria-label]="heading().title + ': ' + heading().subtitle"
            >
              @for (tick of chartScale().ticks; track tick.value) {
                <line
                  [attr.x1]="paddingLeft"
                  [attr.x2]="svgWidth - paddingRight"
                  [attr.y1]="tick.y"
                  [attr.y2]="tick.y"
                  stroke="var(--fo-border)"
                  stroke-dasharray="3 3"
                  stroke-width="1"
                />
                <text
                  [attr.x]="paddingLeft - 8"
                  [attr.y]="tick.y + 4"
                  text-anchor="end"
                  class="fill-muted text-[10px] tabular-nums"
                >
                  {{ tick.label }}
                </text>
              }

              <line
                [attr.x1]="paddingLeft"
                [attr.x2]="svgWidth - paddingRight"
                [attr.y1]="chartScale().zeroY"
                [attr.y2]="chartScale().zeroY"
                stroke="var(--fo-border)"
                stroke-width="1.5"
              />

              @for (point of computedPoints(); track point.periodStart; let i = $index) {
                @let isFocused = activeIndex() === i;
                <g>
                  @if (isFocused) {
                    <rect
                      [attr.x]="point.slotX + 3"
                      [attr.y]="paddingTop"
                      [attr.width]="point.slotWidth - 6"
                      [attr.height]="plotHeight"
                      rx="8"
                      fill="var(--fo-surface-2)"
                      opacity="0.75"
                    />
                  }

                  <rect
                    data-testid="analytics-evolution-income-bar"
                    [attr.x]="point.incomeBar.x"
                    [attr.y]="point.incomeBar.y"
                    [attr.width]="point.barWidth"
                    [attr.height]="point.incomeBar.height"
                    rx="3"
                    fill="var(--fo-success)"
                    [attr.opacity]="isFocused ? 0.95 : 0.78"
                    class="fo-chart-bar"
                  />

                  <rect
                    data-testid="analytics-evolution-expense-bar"
                    [attr.x]="point.expenseBar.x"
                    [attr.y]="point.expenseBar.y"
                    [attr.width]="point.barWidth"
                    [attr.height]="point.expenseBar.height"
                    rx="3"
                    fill="var(--fo-danger)"
                    [attr.opacity]="isFocused ? 0.95 : 0.72"
                    class="fo-chart-bar"
                  />

                  <text
                    [attr.x]="point.centerX"
                    [attr.y]="svgHeight - 10"
                    text-anchor="middle"
                    class="text-[10px] font-medium"
                    [class.fill-text]="isFocused"
                    [class.fill-muted]="!isFocused"
                  >
                    {{ point.label }}
                  </text>

                  <rect
                    data-testid="analytics-evolution-slot"
                    [attr.x]="point.slotX"
                    [attr.y]="paddingTop"
                    [attr.width]="point.slotWidth"
                    [attr.height]="plotHeight + 24"
                    fill="transparent"
                    role="button"
                    tabindex="0"
                    class="fo-focus-ring cursor-pointer"
                    [attr.aria-label]="pointAriaLabel(point)"
                    [attr.aria-pressed]="isFocused"
                    (mouseenter)="selectIndex(i)"
                    (focus)="selectIndex(i)"
                    (click)="selectIndex(i)"
                    (keydown.enter)="selectIndex(i)"
                    (keydown.space)="$event.preventDefault(); selectIndex(i)"
                  />
                </g>
              }

              <polyline
                data-testid="analytics-evolution-balance-line"
                [attr.points]="balanceLinePoints()"
                fill="none"
                stroke="var(--fo-accent)"
                stroke-width="2.5"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="pointer-events-none"
              />

              @for (point of computedPoints(); track point.periodStart; let i = $index) {
                @let isFocused = activeIndex() === i;
                <circle
                  data-testid="analytics-evolution-balance-dot"
                  [attr.cx]="point.balancePoint.x"
                  [attr.cy]="point.balancePoint.y"
                  [attr.r]="isFocused ? 5 : 3.5"
                  fill="var(--fo-surface)"
                  stroke="var(--fo-accent)"
                  stroke-width="2.5"
                  class="pointer-events-none"
                />
              }
            </svg>
          </div>

          @if (activePoint(); as current) {
            <div
              data-testid="analytics-evolution-tooltip"
              class="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-surface-2/80 px-3.5 py-2.5 text-xs"
            >
              <span class="font-semibold text-text">{{ current.label }}</span>
              <div class="flex flex-wrap items-center gap-4 tabular-nums">
                <span class="text-muted">
                  Ingresos:
                  <strong class="font-semibold text-success">
                    {{ current.income | money: displayUnit() }}
                  </strong>
                </span>
                <span class="text-muted">
                  Gastos:
                  <strong class="font-semibold text-danger">
                    {{ current.expense | money: displayUnit() }}
                  </strong>
                </span>
                <span class="text-muted">
                  Balance:
                  <strong class="font-semibold text-accent">
                    {{ current.balance | money: displayUnit() }}
                  </strong>
                </span>
              </div>
            </div>
          }
        </div>
      }
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }

    .fo-chart-bar {
      transition:
        height 260ms ease-out,
        y 260ms ease-out,
        opacity 150ms ease-out;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyticsEvolutionChartComponent {
  readonly series = input<Series | null>(null);
  readonly granularity = input<Granularity>('MONTH');

  protected readonly svgWidth = SVG_WIDTH;
  protected readonly svgHeight = SVG_HEIGHT;
  protected readonly paddingTop = PADDING_TOP;
  protected readonly paddingRight = PADDING_RIGHT;
  protected readonly paddingLeft = PADDING_LEFT;
  protected readonly plotHeight = PLOT_HEIGHT;

  protected readonly selectedIndex = signal<number | null>(null);

  protected readonly heading = computed(() => {
    const currentSeries = this.series();
    const effectiveGranularity = currentSeries?.granularity ?? this.granularity();
    const count = currentSeries?.points.length || 12;
    return getEvolutionHeading(effectiveGranularity, count);
  });

  readonly displayUnit = computed(() => {
    const current = this.series();
    return current ? resolveDisplayUnit(current.unit, current.lens) : 'ARS';
  });

  readonly chartScale = computed<AnalyticsChartScale>(() => {
    const points = this.series()?.points ?? [];
    const unit = this.displayUnit();

    let rawMax = 0;
    let rawMin = 0;
    for (const point of points) {
      const inc = Math.max(0, toNumber(point.income));
      const exp = Math.max(0, toNumber(point.expense));
      const bal = toNumber(point.balance);
      rawMax = Math.max(rawMax, inc, exp, bal);
      rawMin = Math.min(rawMin, bal);
    }

    const max = rawMax > 0 ? rawMax * 1.12 : 100;
    const min = rawMin < 0 ? rawMin * 1.12 : 0;
    const range = max - min || 1;

    const yFor = (val: number): number =>
      round2(PADDING_TOP + ((max - val) / range) * PLOT_HEIGHT);

    const ticks: AnalyticsChartTick[] = [];
    for (let i = 0; i <= TICK_COUNT; i++) {
      const value = min + (range * i) / TICK_COUNT;
      ticks.push({
        value,
        y: yFor(value),
        label: formatCompactAxisValue(value, unit),
      });
    }

    return {
      min,
      max,
      zeroY: yFor(0),
      ticks,
    };
  });

  readonly computedPoints = computed<ComputedSeriesPoint[]>(() => {
    const points = this.series()?.points ?? [];
    if (points.length === 0) {
      return [];
    }

    const scale = this.chartScale();
    const range = scale.max - scale.min || 1;
    const yFor = (val: number): number =>
      round2(PADDING_TOP + ((scale.max - val) / range) * PLOT_HEIGHT);

    const slotWidth = PLOT_WIDTH / points.length;
    const barWidth = round2(Math.min(18, Math.max(8, slotWidth * 0.28)));
    const barGap = 3;

    return points.map((point, index) => {
      const income = Math.max(0, toNumber(point.income));
      const expense = Math.max(0, toNumber(point.expense));
      const balance = toNumber(point.balance);

      const slotX = round2(PADDING_LEFT + slotWidth * index);
      const centerX = round2(slotX + slotWidth / 2);

      const incomeY = yFor(income);
      const expenseY = yFor(expense);
      const balanceY = yFor(balance);

      return {
        periodStart: point.periodStart,
        label: point.label,
        income,
        expense,
        balance,
        slotX,
        slotWidth: round2(slotWidth),
        centerX,
        barWidth,
        incomeBar: {
          x: round2(centerX - barWidth - barGap / 2),
          y: incomeY,
          height: round2(Math.max(0, scale.zeroY - incomeY)),
        },
        expenseBar: {
          x: round2(centerX + barGap / 2),
          y: expenseY,
          height: round2(Math.max(0, scale.zeroY - expenseY)),
        },
        balancePoint: {
          x: centerX,
          y: balanceY,
        },
      };
    });
  });

  readonly balanceLinePoints = computed(() =>
    this.computedPoints()
      .map((point) => `${point.balancePoint.x},${point.balancePoint.y}`)
      .join(' '),
  );

  protected readonly activeIndex = computed<number>(() => {
    const points = this.computedPoints();
    if (points.length === 0) {
      return -1;
    }
    const chosen = this.selectedIndex();
    if (chosen !== null && chosen >= 0 && chosen < points.length) {
      return chosen;
    }
    return points.length - 1;
  });

  protected readonly activePoint = computed<ComputedSeriesPoint | null>(() => {
    const idx = this.activeIndex();
    return idx >= 0 ? (this.computedPoints()[idx] ?? null) : null;
  });

  protected selectIndex(index: number): void {
    this.selectedIndex.set(index);
  }

  protected pointAriaLabel(point: ComputedSeriesPoint): string {
    const unit = this.displayUnit();
    return `${point.label}: ingresos ${formatCompactAxisValue(point.income, unit)}, gastos ${formatCompactAxisValue(point.expense, unit)}, balance ${formatCompactAxisValue(point.balance, unit)}`;
  }
}
