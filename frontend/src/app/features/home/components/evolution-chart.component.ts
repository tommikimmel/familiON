import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Lens } from '../../../core/state/view-context.store';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { PillTagComponent } from '../../../shared/ui/pill-tag/pill-tag.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { toNumber } from '../../../shared/utils/decimal';
import { formatCompactAxisValue, resolveDisplayUnit } from '../data-access/home.formatters';
import { Series } from '../data-access/home.models';

export interface EvolutionColumn {
  periodStart: string;
  label: string;
  shortLabel: string;
  expense: number;
  valueLabel: string;
  /** Bar height in px (mock: `max(14, value / max * 96)`). */
  height: number;
  current: boolean;
}

const MAX_BAR_HEIGHT = 96;
const MIN_BAR_HEIGHT = 14;

/** Desktop 6-month expense evolution (mock `.bar-chart` / `.b-col`). */
@Component({
  selector: 'fo-evolution-chart',
  standalone: true,
  imports: [CardComponent, PillTagComponent, SkeletonComponent],
  template: `
    <fo-card class="h-full">
      <div class="flex items-center justify-between gap-2">
        <div class="min-w-0">
          <h2 class="text-[13px] font-bold">Evolución de Gastos (6 meses)</h2>
          <p class="text-[11px] text-muted">{{ subtitle() }}</p>
        </div>
        <fo-pill-tag class="shrink-0">{{ isConstantBase() ? 'Real' : 'Nominal' }}</fo-pill-tag>
      </div>

      @if (loading() && columns().length === 0) {
        <div class="mt-4">
          <fo-skeleton height="130px" rounded="lg" />
        </div>
      } @else if (columns().length === 0) {
        <p class="py-12 text-center text-xs text-muted">Sin datos de evolución para mostrar.</p>
      } @else {
        <ul class="bar-chart" aria-label="Gastos de los últimos meses">
          @for (col of columns(); track col.periodStart) {
            <li class="b-col" [attr.aria-label]="col.label + ': ' + col.valueLabel">
              <span data-testid="evolution-value" class="b-val" aria-hidden="true">
                {{ col.valueLabel }}
              </span>
              <div
                data-testid="evolution-expense-bar"
                class="b-rect"
                [class.is-current]="col.current"
                [class.is-ipc]="!col.current && isConstantBase()"
                [style.height.px]="col.height"
              ></div>
              <span class="b-lbl" aria-hidden="true">{{ col.shortLabel }}</span>
            </li>
          }
        </ul>
      }
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }
    .bar-chart {
      display: grid;
      grid-template-columns: repeat(6, minmax(0, 1fr));
      align-items: end;
      gap: 10px;
      height: 146px;
      padding-top: 16px;
    }
    .b-col {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      gap: 5px;
      height: 100%;
    }
    .b-val {
      font-size: 10px;
      font-weight: 600;
      color: var(--fo-text-muted);
      font-variant-numeric: tabular-nums;
    }
    .b-rect {
      width: 100%;
      max-width: 34px;
      border-radius: 7px 7px 3px 3px;
      background: var(--fo-surface-2);
      transition:
        height 0.45s var(--fo-ease-smooth),
        background 0.25s ease;
    }
    .b-rect.is-current {
      background: var(--fo-accent);
    }
    .b-rect.is-ipc {
      background: var(--fo-accent-mid);
    }
    .b-lbl {
      font-size: 10.5px;
      font-weight: 600;
      color: var(--fo-text-muted);
    }
    @media (prefers-reduced-motion: reduce) {
      .b-rect {
        transition: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EvolutionChartComponent {
  readonly series = input<Series | null>(null);
  readonly loading = input<boolean>(false);
  readonly lens = input<Lens>('NOMINAL');

  protected readonly isConstantBase = computed(() => this.lens() === 'CONSTANT_BASE');

  protected readonly subtitle = computed(() =>
    this.isConstantBase()
      ? 'Meses pasados traídos a plata de hoy (IPC)'
      : 'Pesos nominales / divisa elegida',
  );

  readonly displayUnit = computed(() => {
    const series = this.series();
    return resolveDisplayUnit(series?.unit, series?.lens ?? this.lens());
  });

  readonly columns = computed<EvolutionColumn[]>(() => {
    const points = this.series()?.points ?? [];
    const unit = this.displayUnit();
    const values = points.map((p) => Math.max(0, toNumber(p.expense)));
    const max = Math.max(...values, 1);
    return points.map((point, index) => ({
      periodStart: point.periodStart,
      label: point.label,
      shortLabel: shortMonth(point.label),
      expense: values[index],
      valueLabel: formatCompactAxisValue(values[index], unit),
      height: Math.max(MIN_BAR_HEIGHT, Math.round((values[index] / max) * MAX_BAR_HEIGHT)),
      current: index === points.length - 1,
    }));
  });
}

function shortMonth(label: string): string {
  const word = label.trim().split(/\s+/)[0] ?? '';
  const short = word.slice(0, 3);
  return short.charAt(0).toUpperCase() + short.slice(1);
}
