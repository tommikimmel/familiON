import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
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

/** Mock `buildDonut` geometry: 124px viewBox, r=46, stroke 15 (19 when focused), 2.5 gap. */
const SVG_SIZE = 124;
const CENTER = SVG_SIZE / 2;
const RADIUS = 46;
const STROKE_WIDTH = 15;
const FOCUSED_STROKE_WIDTH = 19;
const SLICE_GAP = 2.5;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const POP_OUT_DISTANCE = 2;

/** Mock slice palette: accent, accent-mid, accent-light, slice-4, slice-5. */
export const DONUT_PALETTE: readonly string[] = [
  'var(--fo-accent)',
  'var(--fo-accent-mid)',
  'var(--fo-accent-light)',
  'var(--fo-slice-4)',
  'var(--fo-slice-5)',
];

@Component({
  selector: 'fo-donut-chart',
  standalone: true,
  template: `
    <div class="fo-donut-body" [class.fo-donut--lg]="size() === 'lg'">
      <div class="fo-donut-wrap">
        <svg
          [attr.viewBox]="'0 0 ' + svgSize + ' ' + svgSize"
          class="fo-donut-svg"
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
            <g class="fo-donut-slice-group" [class.is-focused]="isSelected">
              <circle
                [attr.cx]="center"
                [attr.cy]="center"
                [attr.r]="radius"
                fill="none"
                [attr.stroke]="segment.color"
                [attr.stroke-width]="isSelected ? focusedStrokeWidth : strokeWidth"
                [attr.stroke-dasharray]="segment.dashArray"
                [attr.stroke-dashoffset]="segment.dashOffset"
                [attr.aria-label]="
                  segment.slice.label + ': ' + formatValue()(segment.value) + ' (' + formatPercent(segment.percentage) + ')'
                "
                [attr.aria-pressed]="isSelected"
                role="button"
                tabindex="0"
                class="fo-donut-arc fo-focus-ring"
                [class.is-dim]="selectedId() !== null && !isSelected"
                [class.is-focused]="isSelected"
                (click)="toggleSelect(segment.slice)"
                (keydown.enter)="toggleSelect(segment.slice)"
                (keydown.space)="$event.preventDefault(); toggleSelect(segment.slice)"
              />
            </g>
          }
        </svg>

        <div class="fo-donut-center">
          @if (activeSegment(); as current) {
            <div class="fo-donut-center-k">{{ current.slice.label }}</div>
            <div class="fo-donut-center-v">{{ formatValue()(current.value) }}</div>
            <div class="fo-donut-center-p">{{ formatPercent(current.percentage) }}</div>
          } @else {
            @if (centerLabel()) {
              <div class="fo-donut-center-k">{{ centerLabel() }}</div>
            }
            <div class="fo-donut-center-v">{{ formatValue()(total()) }}</div>
            @if (segments().length > 0) {
              <div class="fo-donut-center-p">100%</div>
            }
          }
        </div>
      </div>

      @if (segments().length > 0) {
        <ul class="fo-donut-legend" aria-label="Leyenda del gráfico">
          @for (segment of segments(); track segment.slice.id) {
            @let isSelected = selectedId() === segment.slice.id;
            <li>
              <button
                type="button"
                [attr.aria-pressed]="isSelected"
                [attr.aria-label]="segment.slice.label + ': ' + formatValue()(segment.value) + ' (' + formatPercent(segment.percentage) + ')'"
                (click)="toggleSelect(segment.slice)"
                class="fo-legend-row fo-focus-ring"
                [class.is-active]="isSelected"
              >
                <span class="fo-legend-left">
                  <span class="fo-legend-dot" [style.background]="segment.color" aria-hidden="true"></span>
                  <span class="fo-legend-text">
                    <span class="fo-legend-name">{{ segment.slice.label }}</span>
                    @if (segment.slice.sublabel) {
                      <span class="fo-legend-sub">{{ segment.slice.sublabel }}</span>
                    }
                  </span>
                </span>
                <span class="fo-legend-val">
                  {{ legendValue() === 'amount' ? formatValue()(segment.value) : formatPercent(segment.percentage) }}
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

    .fo-donut-body {
      display: grid;
      grid-template-columns: 124px minmax(0, 1fr);
      gap: 12px;
      align-items: center;
    }

    .fo-donut-wrap {
      position: relative;
      width: 124px;
      height: 124px;
      display: grid;
      place-items: center;
    }

    .fo-donut-svg {
      width: 124px;
      height: 124px;
      transform: rotate(-90deg);
      overflow: visible;
    }

    .fo-donut-arc {
      cursor: pointer;
      transition:
        stroke-dasharray 0.52s var(--fo-ease-smooth),
        stroke-dashoffset 0.52s var(--fo-ease-smooth),
        opacity 0.2s ease,
        stroke-width 0.24s var(--fo-ease-spring),
        filter 0.24s ease;
    }

    .fo-donut-arc.is-dim {
      opacity: 0.28;
    }

    .fo-donut-arc.is-focused {
      filter: drop-shadow(0 0 6px var(--fo-accent-glow));
    }

    .fo-donut-center {
      position: absolute;
      padding: 0 6px;
      text-align: center;
      pointer-events: none;
    }

    .fo-donut-center-k {
      max-width: 78px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 9.5px;
      font-weight: 600;
      color: var(--fo-text-muted);
    }

    .fo-donut-center-v {
      margin-top: 1px;
      font-size: 13px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }

    .fo-donut-center-p {
      font-size: 10px;
      font-weight: 700;
      color: var(--fo-accent);
      font-variant-numeric: tabular-nums;
    }

    .fo-donut-legend {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 0;
    }

    .fo-legend-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
      width: 100%;
      padding: 4px 6px;
      border: none;
      border-radius: 8px;
      background: transparent;
      color: inherit;
      font-family: inherit;
      font-size: 11px;
      text-align: left;
      cursor: pointer;
      transition:
        background 0.15s ease,
        transform 0.15s var(--fo-ease-spring);
    }

    .fo-legend-row:hover {
      background: var(--fo-surface-2);
    }

    .fo-legend-row.is-active {
      background: var(--fo-accent-soft);
      transform: translateX(3px);
    }

    .fo-legend-left {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
    }

    .fo-legend-dot {
      width: 8px;
      height: 8px;
      border-radius: 999px;
      flex-shrink: 0;
    }

    .fo-legend-text {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .fo-legend-name,
    .fo-legend-sub {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .fo-legend-name {
      color: var(--fo-text-2);
      font-weight: 500;
    }

    .fo-legend-sub {
      font-size: 10px;
      color: var(--fo-text-muted);
    }

    .fo-legend-val {
      flex-shrink: 0;
      font-weight: 700;
      color: var(--fo-text);
      font-variant-numeric: tabular-nums;
    }

    @media (min-width: 1024px) {
      .fo-donut-body {
        grid-template-columns: 126px minmax(0, 1fr);
        gap: 14px;
      }

      .fo-donut-wrap,
      .fo-donut-svg {
        width: 126px;
        height: 126px;
      }

      .fo-donut-center-v {
        font-size: 13.5px;
      }

      .fo-legend-row {
        padding: 4px 7px;
        font-size: 11.5px;
      }
    }

    /* size="lg": bigger ring + legend, stacked and centered on narrow screens. */
    .fo-donut--lg {
      grid-template-columns: minmax(0, 1fr);
      justify-items: center;
      gap: 16px;
    }

    .fo-donut--lg .fo-donut-wrap,
    .fo-donut--lg .fo-donut-svg {
      width: 184px;
      height: 184px;
    }

    .fo-donut--lg .fo-donut-center-k {
      max-width: 120px;
      font-size: 11px;
    }

    .fo-donut--lg .fo-donut-center-v {
      font-size: 17px;
    }

    .fo-donut--lg .fo-donut-center-p {
      font-size: 12px;
    }

    .fo-donut--lg .fo-donut-legend {
      width: 100%;
      max-width: 340px;
    }

    .fo-donut--lg .fo-legend-row {
      padding: 6px 8px;
      font-size: 13px;
    }

    @media (min-width: 640px) {
      .fo-donut--lg {
        grid-template-columns: 200px minmax(0, 340px);
        justify-content: center;
        gap: 32px;
      }

      .fo-donut--lg .fo-donut-wrap,
      .fo-donut--lg .fo-donut-svg {
        width: 200px;
        height: 200px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DonutChartComponent {
  readonly slices = input.required<readonly DonutSlice[]>();
  readonly centerLabel = input<string>('Total');
  readonly formatValue = input<(value: number) => string>(DEFAULT_FORMATTER);
  /** Optional explicit accent; when set, slices use a tonal scale of it instead of the mock palette. */
  readonly accent = input<string | null>(null);
  /** What the legend shows on the right: percentage (mock) or formatted amount. */
  readonly legendValue = input<'percent' | 'amount'>('percent');
  /** `lg`: bigger chart with its legend centered, for wide cards. */
  readonly size = input<'md' | 'lg'>('md');
  readonly selected = output<DonutSlice | null>();

  protected readonly svgSize = SVG_SIZE;
  protected readonly center = CENTER;
  protected readonly radius = RADIUS;
  protected readonly strokeWidth = STROKE_WIDTH;
  protected readonly focusedStrokeWidth = FOCUSED_STROKE_WIDTH;

  protected readonly selectedId = signal<string | null>(null);

  readonly total = computed(() =>
    this.slices().reduce((sum, slice) => sum + Math.max(0, slice.value), 0),
  );

  readonly segments = computed<ComputedDonutSegment[]>(() => {
    const items = this.slices();
    if (items.length === 0) {
      return [];
    }

    const explicitAccent = this.accent();
    const palette = explicitAccent ? tonalScale(explicitAccent, items.length) : DONUT_PALETTE;
    const totalValue = this.total();
    const gap = items.length > 1 ? SLICE_GAP : 0;
    let accumulatedLength = 0;

    return items.map((slice, index) => {
      const value = Math.max(0, slice.value);
      const percentage = totalValue > 0 ? (value / totalValue) * 100 : 0;
      const arcLength = totalValue > 0 ? (value / totalValue) * CIRCUMFERENCE : 0;
      const visibleLength = Math.max(0, arcLength - gap);
      const dashOffset = accumulatedLength === 0 ? 0 : -accumulatedLength;

      const midLength = accumulatedLength + arcLength / 2;
      const midAngle = -Math.PI / 2 + (midLength / CIRCUMFERENCE) * 2 * Math.PI;
      const popOffsetX = Number((Math.cos(midAngle) * POP_OUT_DISTANCE).toFixed(2));
      const popOffsetY = Number((Math.sin(midAngle) * POP_OUT_DISTANCE).toFixed(2));

      accumulatedLength += arcLength;

      return {
        slice,
        color: slice.color ?? palette[index % palette.length],
        value,
        percentage,
        arcLength,
        dashArray: `${visibleLength.toFixed(2)} ${(CIRCUMFERENCE - visibleLength).toFixed(2)}`,
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
    return `${Math.round(percentage)}%`;
  }
}
