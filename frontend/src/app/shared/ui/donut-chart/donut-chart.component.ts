import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { ThemeService } from '../../../core/theme/theme.service';
import { tonalScale } from '../../utils/color';

export interface DonutSlice {
  id: string;
  label: string;
  value: number;
  color?: string;
  sublabel?: string;
}

export interface ComputedDonutSegment {
  slice: DonutSlice;
  color: string;
  value: number;
  percentage: number;
  arcLength: number;
  dashArray: string;
  dashOffset: number;
  popOffsetX: number;
  popOffsetY: number;
}

const DEFAULT_FORMATTER = (value: number): string =>
  new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value);

const SVG_SIZE = 220;
const CENTER = SVG_SIZE / 2;
const RADIUS = 76;
const STROKE_WIDTH = 24;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const POP_OUT_DISTANCE = 7;

@Component({
  selector: 'fo-donut-chart',
  standalone: true,
  template: `
    <div class="flex flex-col items-center gap-4">
      <div class="relative flex items-center justify-center">
        <svg
          [attr.viewBox]="'0 0 ' + svgSize + ' ' + svgSize"
          class="h-52 w-52 overflow-visible"
          role="img"
          [attr.aria-label]="centerLabel() || 'Gráfico de distribución'"
        >
          <circle
            [attr.cx]="center"
            [attr.cy]="center"
            [attr.r]="radius"
            fill="none"
            stroke="var(--fo-surface-2)"
            [attr.stroke-width]="strokeWidth"
          />

          @for (segment of segments(); track segment.slice.id) {
            @let isSelected = selectedId() === segment.slice.id;
            <g
              class="fo-donut-slice-group"
              [attr.transform]="
                isSelected
                  ? 'translate(' + segment.popOffsetX + ', ' + segment.popOffsetY + ')'
                  : 'translate(0, 0)'
              "
            >
              <circle
                [attr.cx]="center"
                [attr.cy]="center"
                [attr.r]="radius"
                fill="none"
                [attr.stroke]="segment.color"
                [attr.stroke-width]="isSelected ? strokeWidth + 3 : strokeWidth"
                [attr.stroke-dasharray]="segment.dashArray"
                [attr.stroke-dashoffset]="segment.dashOffset"
                [attr.transform]="'rotate(-90 ' + center + ' ' + center + ')'"
                [attr.aria-label]="
                  segment.slice.label + ': ' + formatValue()(segment.value) + ' (' + formatPercent(segment.percentage) + ')'
                "
                [attr.aria-pressed]="isSelected"
                role="button"
                tabindex="0"
                class="fo-donut-arc fo-focus-ring cursor-pointer"
                [class.opacity-45]="selectedId() !== null && !isSelected"
                (click)="toggleSelect(segment.slice)"
                (keydown.enter)="toggleSelect(segment.slice)"
                (keydown.space)="$event.preventDefault(); toggleSelect(segment.slice)"
              />
            </g>
          }
        </svg>

        <div
          class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center px-8 text-center"
        >
          @if (activeSegment(); as current) {
            <span class="max-w-full truncate text-xs font-medium text-muted">
              {{ current.slice.label }}
            </span>
            <span class="tabular-nums text-base font-semibold text-text">
              {{ formatValue()(current.value) }}
            </span>
            <span class="tabular-nums text-xs font-medium text-accent">
              {{ formatPercent(current.percentage) }}
            </span>
          } @else {
            @if (centerLabel()) {
              <span class="max-w-full truncate text-xs font-medium text-muted">
                {{ centerLabel() }}
              </span>
            }
            <span class="tabular-nums text-lg font-semibold text-text">
              {{ formatValue()(total()) }}
            </span>
          }
        </div>
      </div>

      @if (segments().length > 0) {
        <ul class="grid w-full grid-cols-1 gap-1.5 sm:grid-cols-2" aria-label="Leyenda del gráfico">
          @for (segment of segments(); track segment.slice.id) {
            @let isSelected = selectedId() === segment.slice.id;
            <li>
              <button
                type="button"
                [attr.aria-pressed]="isSelected"
                (click)="toggleSelect(segment.slice)"
                class="fo-focus-ring flex w-full cursor-pointer items-center justify-between gap-2 rounded-xl px-2.5 py-1.5 text-left transition-colors duration-150"
                [class.bg-surface-2]="isSelected"
                [class.hover:bg-surface-2]="!isSelected"
              >
                <span class="flex min-w-0 items-center gap-2">
                  <span
                    class="h-2.5 w-2.5 shrink-0 rounded-full"
                    [style.background-color]="segment.color"
                    aria-hidden="true"
                  ></span>
                  <span class="flex min-w-0 flex-col">
                    <span class="truncate text-sm font-medium text-text">
                      {{ segment.slice.label }}
                    </span>
                    @if (segment.slice.sublabel) {
                      <span class="truncate text-xs text-muted">
                        {{ segment.slice.sublabel }}
                      </span>
                    }
                  </span>
                </span>

                <span class="shrink-0 text-right">
                  <span class="block tabular-nums text-sm font-medium text-text">
                    {{ formatValue()(segment.value) }}
                  </span>
                  <span class="block tabular-nums text-xs text-muted">
                    {{ formatPercent(segment.percentage) }}
                  </span>
                </span>
              </button>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .fo-donut-slice-group {
      transition: transform 240ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1));
    }

    .fo-donut-arc {
      transition:
        stroke-dasharray 320ms ease-out,
        stroke-dashoffset 320ms ease-out,
        stroke-width 200ms ease-out,
        opacity 180ms ease-out;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DonutChartComponent {
  private readonly themeService = inject(ThemeService, { optional: true });

  readonly slices = input.required<readonly DonutSlice[]>();
  readonly centerLabel = input<string>('Total');
  readonly formatValue = input<(value: number) => string>(DEFAULT_FORMATTER);
  readonly accent = input<string | null>(null);
  readonly selected = output<DonutSlice | null>();

  protected readonly svgSize = SVG_SIZE;
  protected readonly center = CENTER;
  protected readonly radius = RADIUS;
  protected readonly strokeWidth = STROKE_WIDTH;

  protected readonly selectedId = signal<string | null>(null);

  readonly total = computed(() =>
    this.slices().reduce((sum, slice) => sum + Math.max(0, slice.value), 0),
  );

  readonly segments = computed<ComputedDonutSegment[]>(() => {
    const items = this.slices();
    if (items.length === 0) {
      return [];
    }

    const effectiveAccent = this.accent() ?? this.themeService?.accent() ?? '#0F766E';
    const palette = tonalScale(effectiveAccent, items.length);
    const totalValue = this.total();
    let accumulatedLength = 0;

    return items.map((slice, index) => {
      const value = Math.max(0, slice.value);
      const percentage = totalValue > 0 ? (value / totalValue) * 100 : 0;
      const arcLength = totalValue > 0 ? (value / totalValue) * CIRCUMFERENCE : 0;
      const dashOffset = accumulatedLength === 0 ? 0 : -accumulatedLength;

      const midLength = accumulatedLength + arcLength / 2;
      const midAngle = -Math.PI / 2 + (midLength / CIRCUMFERENCE) * 2 * Math.PI;
      const popOffsetX = Number((Math.cos(midAngle) * POP_OUT_DISTANCE).toFixed(2));
      const popOffsetY = Number((Math.sin(midAngle) * POP_OUT_DISTANCE).toFixed(2));

      accumulatedLength += arcLength;

      return {
        slice,
        color: slice.color ?? palette[index],
        value,
        percentage,
        arcLength,
        dashArray: `${arcLength} ${CIRCUMFERENCE}`,
        dashOffset,
        popOffsetX,
        popOffsetY,
      };
    });
  });

  protected readonly activeSegment = computed<ComputedDonutSegment | null>(() => {
    const currentId = this.selectedId();
    if (!currentId) {
      return null;
    }
    return this.segments().find((seg) => seg.slice.id === currentId) ?? null;
  });

  protected toggleSelect(slice: DonutSlice): void {
    const next = this.selectedId() === slice.id ? null : slice;
    this.selectedId.set(next?.id ?? null);
    this.selected.emit(next);
  }

  protected formatPercent(percentage: number): string {
    return `${percentage.toFixed(1)}%`;
  }
}
