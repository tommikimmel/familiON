import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import { FUEL_TYPE_LABEL, formatIsoDateShort } from '../data-access/vehicle-labels';
import { FuelType, VehiclePriceTrendPoint } from '../data-access/vehicles.models';

export interface PriceTrendPlotPoint {
  date: string;
  dateLabel: string;
  pricePerLiter: number;
  fuelType: FuelType;
  fuelLabel: string;
  x: number;
  y: number;
}

const SVG_WIDTH = 640;
const SVG_HEIGHT = 236;
const PADDING_TOP = 16;
const PADDING_RIGHT = 18;
const PADDING_BOTTOM = 34;
const PADDING_LEFT = 58;
const PLOT_WIDTH = SVG_WIDTH - PADDING_LEFT - PADDING_RIGHT;
const PLOT_HEIGHT = SVG_HEIGHT - PADDING_TOP - PADDING_BOTTOM;
const TICK_COUNT = 4;

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function formatAxisPrice(value: number, unit: string): string {
  const prefix = unit === 'USD' ? 'US$' : unit === 'ARS' ? '$' : '';
  const formatted = new Intl.NumberFormat('es-AR', {
    maximumFractionDigits: value < 10 ? 2 : 0,
  }).format(value);
  return prefix ? `${prefix}${formatted}` : `${formatted} ${unit}`.trim();
}

@Component({
  selector: 'fo-vehicle-price-trend-chart',
  standalone: true,
  imports: [CardComponent, MoneyPipe],
  template: `
    <fo-card padding="md">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 class="text-[12.5px] font-bold text-text lg:text-[13px]">Evolución del precio por litro</h2>
          <p class="mt-px text-[10.5px] text-muted">Historial de precio unitario en tus cargas</p>
        </div>

        @if (points().length > 1) {
          @let delta = priceVariationPct();
          @if (delta !== null) {
            <span
              class="rounded-full px-2.5 py-0.5 text-xs font-semibold tabular-nums"
              [class.bg-danger/10]="delta > 0"
              [class.text-danger]="delta > 0"
              [class.bg-success/10]="delta <= 0"
              [class.text-success]="delta <= 0"
            >
              {{ delta > 0 ? '+' : '' }}{{ delta.toFixed(1) }}%
            </span>
          }
        }
      </div>

      @if (points().length === 0) {
        <p class="py-10 text-center text-sm text-muted">
          Cargá combustible para ver la evolución del precio por litro.
        </p>
      } @else {
        <div class="mt-4 space-y-3">
          <div class="overflow-x-auto pb-1">
            <svg
              [attr.viewBox]="'0 0 ' + svgWidth + ' ' + svgHeight"
              class="h-56 w-full min-w-[420px] overflow-visible"
              role="img"
              aria-label="Gráfico de evolución del precio por litro de combustible"
            >
              @for (tick of scale().ticks; track tick.value) {
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

              @if (points().length > 1) {
                <polyline
                  data-testid="vehicle-price-trend-line"
                  [attr.points]="polylinePoints()"
                  fill="none"
                  stroke="var(--fo-accent)"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              }

              @for (point of points(); track point.date + '-' + $index; let i = $index) {
                @let isFocused = activeIndex() === i;
                <g>
                  @if (shouldShowDateLabel(i)) {
                    <text
                      [attr.x]="point.x"
                      [attr.y]="svgHeight - 10"
                      text-anchor="middle"
                      class="text-[10px] font-medium"
                      [class.fill-text]="isFocused"
                      [class.fill-muted]="!isFocused"
                    >
                      {{ shortTickDate(point.date) }}
                    </text>
                  }

                  <circle
                    data-testid="vehicle-price-trend-dot"
                    [attr.cx]="point.x"
                    [attr.cy]="point.y"
                    [attr.r]="isFocused ? 6 : 4"
                    fill="var(--fo-surface)"
                    stroke="var(--fo-accent)"
                    stroke-width="2.5"
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
            </svg>
          </div>

          @if (activePoint(); as current) {
            <div
              data-testid="vehicle-price-trend-tooltip"
              class="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-surface-2/80 px-3.5 py-2.5 text-xs"
            >
              <div class="flex items-center gap-2">
                <span class="font-semibold text-text">{{ current.dateLabel }}</span>
                <span class="rounded-full bg-surface px-2 py-0.5 text-[11px] font-medium text-muted">
                  {{ current.fuelLabel }}
                </span>
              </div>
              <span class="font-semibold text-accent tabular-nums">
                {{ current.pricePerLiter | money: unit() }} / L
              </span>
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
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehiclePriceTrendChartComponent {
  readonly trend = input<readonly VehiclePriceTrendPoint[]>([]);
  readonly unit = input<string>('ARS');

  protected readonly svgWidth = SVG_WIDTH;
  protected readonly svgHeight = SVG_HEIGHT;
  protected readonly paddingLeft = PADDING_LEFT;
  protected readonly paddingRight = PADDING_RIGHT;

  protected readonly selectedIndex = signal<number | null>(null);

  readonly scale = computed(() => {
    const items = this.trend();
    const unit = this.unit();
    if (items.length === 0) {
      return { min: 0, max: 100, ticks: [] as { value: number; y: number; label: string }[] };
    }

    let rawMin = Number.POSITIVE_INFINITY;
    let rawMax = 0;
    for (const item of items) {
      const val = Math.max(0, toNumber(item.pricePerLiter));
      rawMin = Math.min(rawMin, val);
      rawMax = Math.max(rawMax, val);
    }

    const spread = rawMax - rawMin;
    const margin = spread > 0 ? spread * 0.18 : Math.max(rawMax * 0.15, 10);
    const min = Math.max(0, rawMin - margin);
    const max = rawMax + margin;
    const range = max - min || 1;

    const ticks: { value: number; y: number; label: string }[] = [];
    for (let i = 0; i <= TICK_COUNT; i++) {
      const value = min + (range * i) / TICK_COUNT;
      const y = round2(PADDING_TOP + ((max - value) / range) * PLOT_HEIGHT);
      ticks.push({ value, y, label: formatAxisPrice(value, unit) });
    }

    return { min, max, ticks };
  });

  readonly points = computed<PriceTrendPlotPoint[]>(() => {
    const items = this.trend();
    if (items.length === 0) {
      return [];
    }

    const { min, max } = this.scale();
    const range = max - min || 1;

    return items.map((item, index) => {
      const pricePerLiter = Math.max(0, toNumber(item.pricePerLiter));
      const x =
        items.length === 1
          ? round2(PADDING_LEFT + PLOT_WIDTH / 2)
          : round2(PADDING_LEFT + (PLOT_WIDTH * index) / (items.length - 1));
      const y = round2(PADDING_TOP + ((max - pricePerLiter) / range) * PLOT_HEIGHT);

      return {
        date: item.date,
        dateLabel: formatIsoDateShort(item.date),
        pricePerLiter,
        fuelType: item.fuelType,
        fuelLabel: FUEL_TYPE_LABEL[item.fuelType] ?? item.fuelType,
        x,
        y,
      };
    });
  });

  readonly polylinePoints = computed(() =>
    this.points()
      .map((point) => `${point.x},${point.y}`)
      .join(' '),
  );

  readonly priceVariationPct = computed<number | null>(() => {
    const pts = this.points();
    if (pts.length < 2) {
      return null;
    }
    const first = pts[0].pricePerLiter;
    const last = pts[pts.length - 1].pricePerLiter;
    if (first <= 0) {
      return null;
    }
    return ((last - first) / first) * 100;
  });

  protected readonly activeIndex = computed<number>(() => {
    const pts = this.points();
    if (pts.length === 0) {
      return -1;
    }
    const chosen = this.selectedIndex();
    if (chosen !== null && chosen >= 0 && chosen < pts.length) {
      return chosen;
    }
    return pts.length - 1;
  });

  protected readonly activePoint = computed<PriceTrendPlotPoint | null>(() => {
    const idx = this.activeIndex();
    return idx >= 0 ? (this.points()[idx] ?? null) : null;
  });

  protected selectIndex(index: number): void {
    this.selectedIndex.set(index);
  }

  protected shouldShowDateLabel(index: number): boolean {
    const total = this.points().length;
    if (total <= 6) {
      return true;
    }
    const step = Math.ceil(total / 5);
    return index === 0 || index === total - 1 || index % step === 0;
  }

  protected shortTickDate(isoDate: string): string {
    const parts = isoDate.slice(0, 10).split('-');
    if (parts.length !== 3) {
      return isoDate;
    }
    return `${parts[2]}/${parts[1]}`;
  }

  protected pointAriaLabel(point: PriceTrendPlotPoint): string {
    return `${point.dateLabel}, ${point.fuelLabel}: ${formatAxisPrice(point.pricePerLiter, this.unit())} por litro`;
  }
}
