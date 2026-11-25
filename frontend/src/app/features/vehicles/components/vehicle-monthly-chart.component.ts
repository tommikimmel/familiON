import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import { formatKm, formatMonthShort } from '../data-access/vehicle-labels';
import { VehicleMonthlyPoint } from '../data-access/vehicles.models';

export interface MonthlyChartTick {
  value: number;
  y: number;
  label: string;
}

export interface ComputedMonthlyBar {
  month: string;
  label: string;
  fuelCost: number;
  maintenanceCost: number;
  totalCost: number;
  km: number;
  slotX: number;
  slotWidth: number;
  centerX: number;
  barWidth: number;
  barX: number;
  fuelBar: { y: number; height: number };
  maintenanceBar: { y: number; height: number };
}

const SVG_WIDTH = 640;
const SVG_HEIGHT = 236;
const PADDING_TOP = 16;
const PADDING_RIGHT = 16;
const PADDING_BOTTOM = 34;
const PADDING_LEFT = 58;
const PLOT_WIDTH = SVG_WIDTH - PADDING_LEFT - PADDING_RIGHT;
const PLOT_HEIGHT = SVG_HEIGHT - PADDING_TOP - PADDING_BOTTOM;
const TICK_COUNT = 4;

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

function formatCompactAmount(value: number, unit: string): string {
  const abs = Math.abs(value);
  const prefix = unit === 'USD' ? 'US$' : unit === 'ARS' ? '$' : '';
  const suffix = prefix === '' && unit ? ` ${unit}` : '';

  if (abs >= 1_000_000) {
    return `${prefix}${(value / 1_000_000).toFixed(1)}M${suffix}`;
  }
  if (abs >= 1_000) {
    return `${prefix}${Math.round(value / 1_000)}k${suffix}`;
  }
  return `${prefix}${Math.round(value)}${suffix}`;
}

@Component({
  selector: 'fo-vehicle-monthly-chart',
  standalone: true,
  imports: [CardComponent, MoneyPipe],
  template: `
    <fo-card padding="md">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 class="text-base font-semibold text-text">Costos mensuales</h2>
          <p class="text-xs text-muted">Combustible y mantenimiento apilados por mes</p>
        </div>

        <div class="flex items-center gap-3 text-xs text-muted" aria-hidden="true">
          <span class="inline-flex items-center gap-1.5">
            <span class="h-2.5 w-2.5 rounded-xs bg-accent"></span>
            Combustible
          </span>
          <span class="inline-flex items-center gap-1.5">
            <span class="h-2.5 w-2.5 rounded-xs bg-warning"></span>
            Mantenimiento
          </span>
        </div>
      </div>

      @if (bars().length === 0) {
        <p class="py-10 text-center text-sm text-muted">
          Sin movimientos mensuales registrados en este período.
        </p>
      } @else {
        <div class="mt-4 space-y-3">
          <div class="overflow-x-auto pb-1">
            <svg
              [attr.viewBox]="'0 0 ' + svgWidth + ' ' + svgHeight"
              class="h-56 w-full min-w-[420px] overflow-visible"
              role="img"
              aria-label="Gráfico de costos mensuales de combustible y mantenimiento"
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

              <line
                [attr.x1]="paddingLeft"
                [attr.x2]="svgWidth - paddingRight"
                [attr.y1]="baselineY"
                [attr.y2]="baselineY"
                stroke="var(--fo-border)"
                stroke-width="1.5"
              />

              @for (bar of bars(); track bar.month; let i = $index) {
                @let isFocused = activeIndex() === i;
                <g>
                  @if (isFocused) {
                    <rect
                      [attr.x]="bar.slotX + 3"
                      [attr.y]="paddingTop"
                      [attr.width]="bar.slotWidth - 6"
                      [attr.height]="plotHeight"
                      rx="8"
                      fill="var(--fo-surface-2)"
                      opacity="0.75"
                    />
                  }

                  @if (bar.fuelBar.height > 0) {
                    <rect
                      data-testid="vehicle-monthly-fuel-bar"
                      [attr.x]="bar.barX"
                      [attr.y]="bar.fuelBar.y"
                      [attr.width]="bar.barWidth"
                      [attr.height]="bar.fuelBar.height"
                      rx="3"
                      fill="var(--fo-accent)"
                      [attr.opacity]="isFocused ? 0.98 : 0.82"
                      class="fo-vehicle-bar"
                    />
                  }

                  @if (bar.maintenanceBar.height > 0) {
                    <rect
                      data-testid="vehicle-monthly-maintenance-bar"
                      [attr.x]="bar.barX"
                      [attr.y]="bar.maintenanceBar.y"
                      [attr.width]="bar.barWidth"
                      [attr.height]="bar.maintenanceBar.height"
                      rx="3"
                      fill="var(--fo-warning)"
                      [attr.opacity]="isFocused ? 0.98 : 0.85"
                      class="fo-vehicle-bar"
                    />
                  }

                  <text
                    [attr.x]="bar.centerX"
                    [attr.y]="svgHeight - 10"
                    text-anchor="middle"
                    class="text-[10px] font-medium"
                    [class.fill-text]="isFocused"
                    [class.fill-muted]="!isFocused"
                  >
                    {{ bar.label }}
                  </text>

                  <rect
                    data-testid="vehicle-monthly-slot"
                    [attr.x]="bar.slotX"
                    [attr.y]="paddingTop"
                    [attr.width]="bar.slotWidth"
                    [attr.height]="plotHeight + 22"
                    fill="transparent"
                    role="button"
                    tabindex="0"
                    class="fo-focus-ring cursor-pointer"
                    [attr.aria-label]="barAriaLabel(bar)"
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

          @if (activeBar(); as current) {
            <div
              data-testid="vehicle-monthly-tooltip"
              class="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-surface-2/80 px-3.5 py-2.5 text-xs"
            >
              <span class="font-semibold text-text">
                {{ current.label }}
                <span class="font-normal text-muted tabular-nums">({{ formatKmValue(current.km) }})</span>
              </span>
              <div class="flex flex-wrap items-center gap-3 tabular-nums">
                <span class="text-muted">
                  Combustible:
                  <strong class="font-semibold text-accent">
                    {{ current.fuelCost | money: unit() }}
                  </strong>
                </span>
                <span class="text-muted">
                  Service:
                  <strong class="font-semibold text-warning">
                    {{ current.maintenanceCost | money: unit() }}
                  </strong>
                </span>
                <span class="text-muted">
                  Total:
                  <strong class="font-semibold text-text">
                    {{ current.totalCost | money: unit() }}
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

    .fo-vehicle-bar {
      transition:
        height 240ms ease-out,
        y 240ms ease-out,
        opacity 150ms ease-out;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleMonthlyChartComponent {
  readonly monthly = input<readonly VehicleMonthlyPoint[]>([]);
  readonly unit = input<string>('ARS');

  protected readonly svgWidth = SVG_WIDTH;
  protected readonly svgHeight = SVG_HEIGHT;
  protected readonly paddingTop = PADDING_TOP;
  protected readonly paddingRight = PADDING_RIGHT;
  protected readonly paddingLeft = PADDING_LEFT;
  protected readonly plotHeight = PLOT_HEIGHT;
  protected readonly baselineY = PADDING_TOP + PLOT_HEIGHT;

  protected readonly selectedIndex = signal<number | null>(null);

  readonly scale = computed(() => {
    const items = this.monthly();
    const unit = this.unit();
    let maxTotal = 0;
    for (const item of items) {
      const total = Math.max(0, toNumber(item.fuelCost)) + Math.max(0, toNumber(item.maintenanceCost));
      maxTotal = Math.max(maxTotal, total);
    }
    const max = maxTotal > 0 ? maxTotal * 1.15 : 100;
    const ticks: MonthlyChartTick[] = [];
    for (let i = 0; i <= TICK_COUNT; i++) {
      const value = (max * i) / TICK_COUNT;
      const y = round2(PADDING_TOP + ((max - value) / max) * PLOT_HEIGHT);
      ticks.push({
        value,
        y,
        label: formatCompactAmount(value, unit),
      });
    }
    return { max, ticks };
  });

  readonly bars = computed<ComputedMonthlyBar[]>(() => {
    const items = this.monthly();
    if (items.length === 0) {
      return [];
    }

    const { max } = this.scale();
    const slotWidth = PLOT_WIDTH / items.length;
    const barWidth = round2(Math.min(34, Math.max(12, slotWidth * 0.52)));

    return items.map((item, index) => {
      const fuelCost = Math.max(0, toNumber(item.fuelCost));
      const maintenanceCost = Math.max(0, toNumber(item.maintenanceCost));
      const totalCost = fuelCost + maintenanceCost;

      const slotX = round2(PADDING_LEFT + slotWidth * index);
      const centerX = round2(slotX + slotWidth / 2);
      const barX = round2(centerX - barWidth / 2);

      const fuelHeight = round2((fuelCost / max) * PLOT_HEIGHT);
      const maintenanceHeight = round2((maintenanceCost / max) * PLOT_HEIGHT);
      const fuelY = round2(this.baselineY - fuelHeight);
      const maintenanceY = round2(fuelY - maintenanceHeight);

      return {
        month: item.month,
        label: formatMonthShort(item.month),
        fuelCost,
        maintenanceCost,
        totalCost,
        km: item.km,
        slotX,
        slotWidth: round2(slotWidth),
        centerX,
        barWidth,
        barX,
        fuelBar: { y: fuelY, height: fuelHeight },
        maintenanceBar: { y: maintenanceY, height: maintenanceHeight },
      };
    });
  });

  protected readonly activeIndex = computed<number>(() => {
    const items = this.bars();
    if (items.length === 0) {
      return -1;
    }
    const chosen = this.selectedIndex();
    if (chosen !== null && chosen >= 0 && chosen < items.length) {
      return chosen;
    }
    return items.length - 1;
  });

  protected readonly activeBar = computed<ComputedMonthlyBar | null>(() => {
    const idx = this.activeIndex();
    return idx >= 0 ? (this.bars()[idx] ?? null) : null;
  });

  protected selectIndex(index: number): void {
    this.selectedIndex.set(index);
  }

  protected formatKmValue(km: number): string {
    return formatKm(km);
  }

  protected barAriaLabel(bar: ComputedMonthlyBar): string {
    const unit = this.unit();
    return `${bar.label}: combustible ${formatCompactAmount(bar.fuelCost, unit)}, mantenimiento ${formatCompactAmount(bar.maintenanceCost, unit)}, ${formatKm(bar.km)}`;
  }
}
