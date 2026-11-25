import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { Lens, View } from '../../../core/state/view-context.store';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { DonutChartComponent, DonutSlice } from '../../../shared/ui/donut-chart/donut-chart.component';
import {
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../shared/ui/segmented-toggle/segmented-toggle.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  capDonutSlices,
  formatCompactAxisValue,
  formatSignedRealVariation,
  resolveDisplayUnit,
} from '../data-access/home.formatters';
import { CategoryBreakdown, PersonBreakdown } from '../data-access/home.models';

export type BreakdownMode = 'CATEGORY' | 'PERSON';

interface VariationChip {
  id: string;
  name: string;
  label: string;
  tone: 'danger' | 'success' | 'neutral';
}

const MODE_OPTIONS: readonly SegmentedOption<BreakdownMode>[] = [
  { value: 'CATEGORY', label: 'Categorías' },
  { value: 'PERSON', label: 'Personas' },
];

@Component({
  selector: 'fo-breakdown-card',
  standalone: true,
  imports: [CardComponent, DonutChartComponent, SegmentedToggleComponent],
  template: `
    <fo-card class="h-full">
      <div class="donut-header">
        <h2 class="donut-title">Distribución</h2>
        @if (canShowPerson()) {
          <fo-segmented-toggle
            data-testid="breakdown-mode-toggle"
            [options]="modeOptions"
            [value]="effectiveMode()"
            (valueChange)="onModeChange($event)"
            ariaLabel="Agrupar distribución por categorías o personas"
            size="sm"
          />
        }
      </div>

      <div>
        @if (!hasActiveData()) {
          <p class="py-8 text-center text-sm text-muted">
            No pudimos cargar el desglose para este período.
          </p>
        } @else if (donutSlices().length === 0) {
          <p class="py-8 text-center text-sm text-muted">
            Sin gastos registrados en este período.
          </p>
        } @else {
          <fo-donut-chart
            [slices]="donutSlices()"
            [centerLabel]="centerLabel()"
            [formatValue]="valueFormatter()"
            (selected)="onSliceSelected($event)"
          />

          @if (variationChips().length > 0) {
            <div
              data-testid="category-variations"
              class="mt-3 border-t border-border pt-2.5"
            >
              <p class="mb-1.5 text-[10.5px] font-medium text-muted">
                Variación real vs. período anterior
              </p>
              <div class="flex flex-wrap gap-1.5">
                @for (chip of variationChips(); track chip.id) {
                  <span
                    class="inline-flex items-center gap-1.5 rounded-full px-[7px] py-0.5 text-[10px] font-semibold tabular-nums"
                    [class.bg-danger/10]="chip.tone === 'danger'"
                    [class.text-danger]="chip.tone === 'danger'"
                    [class.bg-success/10]="chip.tone === 'success'"
                    [class.text-success]="chip.tone === 'success'"
                    [class.bg-surface-2]="chip.tone === 'neutral'"
                    [class.text-muted]="chip.tone === 'neutral'"
                    [class.ring-1]="selectedSliceId() === chip.id"
                    [class.ring-accent]="selectedSliceId() === chip.id"
                  >
                    <span class="max-w-28 truncate text-text">{{ chip.name }}</span>
                    <span class="font-semibold">{{ chip.label }}</span>
                  </span>
                }
              </div>
            </div>
          }
        }
      </div>
    </fo-card>
  `,
  styles: `
    :host {
      display: block;
    }
    .donut-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 10px;
    }
    .donut-title {
      font-size: 12px;
      font-weight: 700;
    }
    @media (min-width: 1024px) {
      .donut-title {
        font-size: 13px;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreakdownCardComponent {
  readonly byCategory = input<CategoryBreakdown | null>(null);
  readonly byPerson = input<PersonBreakdown | null>(null);
  readonly view = input<View>('PERSONAL');
  readonly lens = input<Lens>('NOMINAL');
  readonly sliceSelected = output<DonutSlice | null>();

  protected readonly modeOptions = MODE_OPTIONS;
  protected readonly selectedMode = signal<BreakdownMode>('CATEGORY');
  protected readonly selectedSliceId = signal<string | null>(null);

  protected readonly canShowPerson = computed(
    () => this.view() === 'FAMILY' && this.byPerson() !== null,
  );

  protected readonly effectiveMode = computed<BreakdownMode>(() =>
    this.canShowPerson() ? this.selectedMode() : 'CATEGORY',
  );

  protected readonly hasActiveData = computed(() =>
    this.effectiveMode() === 'PERSON' ? this.byPerson() !== null : this.byCategory() !== null,
  );

  protected readonly centerLabel = computed(() =>
    this.effectiveMode() === 'PERSON' ? 'Familia' : 'Gastos',
  );

  protected readonly displayUnit = computed(() => {
    const active = this.effectiveMode() === 'PERSON' ? this.byPerson() : this.byCategory();
    return resolveDisplayUnit(active?.unit, this.lens());
  });

  protected readonly valueFormatter = computed<(value: number) => string>(() => {
    const unit = this.displayUnit();
    return (value: number) => formatCompactAxisValue(value, unit);
  });

  protected readonly donutSlices = computed<DonutSlice[]>(() => {
    if (this.effectiveMode() === 'PERSON') {
      const person = this.byPerson();
      if (!person) {
        return [];
      }
      return capDonutSlices(
        person.slices.map((slice) => ({
          id: slice.userId,
          label: slice.fullName,
          value: toNumber(slice.amount),
        })),
      );
    }

    const category = this.byCategory();
    if (!category) {
      return [];
    }
    return capDonutSlices(
      category.slices.map((slice, index) => ({
        id: slice.categoryId ?? `uncategorized-${index}`,
        label: slice.name,
        value: toNumber(slice.amount),
      })),
    );
  });

  protected readonly variationChips = computed<VariationChip[]>(() => {
    if (this.effectiveMode() !== 'CATEGORY') {
      return [];
    }
    const category = this.byCategory();
    if (!category) {
      return [];
    }

    const chips: VariationChip[] = [];
    category.slices.forEach((slice, index) => {
      const label = formatSignedRealVariation(slice.variationPct);
      if (!label) {
        return;
      }
      const num = toNumber(slice.variationPct);
      const tone: VariationChip['tone'] = num > 0 ? 'danger' : num < 0 ? 'success' : 'neutral';
      chips.push({
        id: slice.categoryId ?? `uncategorized-${index}`,
        name: slice.name,
        label,
        tone,
      });
    });
    return chips;
  });

  protected onModeChange(next: BreakdownMode): void {
    this.selectedMode.set(next);
    this.selectedSliceId.set(null);
    this.sliceSelected.emit(null);
  }

  protected onSliceSelected(slice: DonutSlice | null): void {
    this.selectedSliceId.set(slice?.id ?? null);
    this.sliceSelected.emit(slice);
  }
}
