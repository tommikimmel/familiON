import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { Lens } from '../../../core/state/view-context.store';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { DonutChartComponent, DonutSlice } from '../../../shared/ui/donut-chart/donut-chart.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { toNumber } from '../../../shared/utils/decimal';
import {
  formatMoneyValue,
  formatSignedRealVariation,
  resolveDisplayUnit,
} from '../data-access/analytics.logic';
import { CategoryBreakdown, PersonBreakdown } from '../data-access/analytics.models';

interface CategoryVariationItem {
  id: string;
  name: string;
  icon: string;
  label: string;
  tone: 'danger' | 'success' | 'neutral';
}

@Component({
  selector: 'fo-analytics-breakdowns',
  standalone: true,
  imports: [CardComponent, DonutChartComponent, IconComponent],
  template: `
    <div class="grid grid-cols-1 gap-5" [class.lg:grid-cols-2]="showPerson()">
      <!-- By Category -->
      <fo-card class="h-full" padding="md" data-testid="category-breakdown-card">
        <div class="flex items-center justify-between gap-2">
          <div>
            <h2 class="text-base font-semibold text-text">Gastos por categoría</h2>
            @if (byCategory()?.period?.label; as periodLabel) {
              <p class="text-xs text-muted">{{ periodLabel }}</p>
            }
          </div>
          <fo-icon name="chart-pie" [size]="18" class="text-muted" />
        </div>

        <div class="mt-4">
          @if (!byCategory()) {
            <p class="py-10 text-center text-sm text-muted">
              No pudimos cargar el desglose por categoría.
            </p>
          } @else if (categorySlices().length === 0) {
            <p class="py-10 text-center text-sm text-muted">
              Sin gastos registrados por categoría en este período.
            </p>
          } @else {
            <fo-donut-chart
              [slices]="categorySlices()"
              size="lg"
              centerLabel="Categorías"
              [formatValue]="categoryFormatter()"
              (selected)="selectedCategoryId.set($event?.id ?? null)"
            />

            @if (categoryVariations().length > 0) {
              <div
                data-testid="category-variation-list"
                class="mt-4 border-t border-border/80 pt-3"
              >
                <p class="mb-2 text-center text-[11px] font-medium tracking-wide text-muted uppercase">
                  Variación real por categoría
                </p>
                <div class="flex flex-wrap justify-center gap-1.5">
                  @for (item of categoryVariations(); track item.id) {
                    <span
                      class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium tabular-nums"
                      [class.bg-danger/10]="item.tone === 'danger'"
                      [class.text-danger]="item.tone === 'danger'"
                      [class.bg-success/10]="item.tone === 'success'"
                      [class.text-success]="item.tone === 'success'"
                      [class.bg-surface-2]="item.tone === 'neutral'"
                      [class.text-muted]="item.tone === 'neutral'"
                      [class.ring-1]="selectedCategoryId() === item.id"
                      [class.ring-accent]="selectedCategoryId() === item.id"
                    >
                      <fo-icon [name]="item.icon" [size]="12" />
                      <span class="max-w-28 truncate text-text">{{ item.name }}</span>
                      <span class="font-semibold">{{ item.label }}</span>
                    </span>
                  }
                </div>
              </div>
            }
          }
        </div>
      </fo-card>

      <!-- By Person (FAMILY view only, hidden on 403) -->
      @if (showPerson()) {
        <fo-card class="h-full" padding="md" data-testid="person-breakdown-card">
          <div class="flex items-center justify-between gap-2">
            <div>
              <h2 class="text-base font-semibold text-text">Gastos por persona</h2>
              @if (byPerson()?.period?.label; as periodLabel) {
                <p class="text-xs text-muted">{{ periodLabel }}</p>
              }
            </div>
            <fo-icon name="users" [size]="18" class="text-muted" />
          </div>

          <div class="mt-4">
            @if (!byPerson()) {
              <p class="py-10 text-center text-sm text-muted">
                No pudimos cargar el desglose por persona.
              </p>
            } @else if (personSlices().length === 0) {
              <p class="py-10 text-center text-sm text-muted">
                Sin gastos atribuidos por persona en este período.
              </p>
            } @else {
              <fo-donut-chart
                [slices]="personSlices()"
                size="lg"
                centerLabel="Personas"
                [formatValue]="personFormatter()"
              />
            }
          </div>
        </fo-card>
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
export class AnalyticsBreakdownsComponent {
  readonly byCategory = input<CategoryBreakdown | null>(null);
  readonly byPerson = input<PersonBreakdown | null>(null);
  readonly showPerson = input<boolean>(false);
  readonly lens = input<Lens>('NOMINAL');

  protected readonly selectedCategoryId = signal<string | null>(null);

  protected readonly categoryUnit = computed(() =>
    resolveDisplayUnit(this.byCategory()?.unit, this.lens()),
  );

  protected readonly personUnit = computed(() =>
    resolveDisplayUnit(this.byPerson()?.unit, this.lens()),
  );

  protected readonly categoryFormatter = computed<(value: number) => string>(() => {
    const unit = this.categoryUnit();
    return (value: number) => formatMoneyValue(value, unit);
  });

  protected readonly personFormatter = computed<(value: number) => string>(() => {
    const unit = this.personUnit();
    return (value: number) => formatMoneyValue(value, unit);
  });

  protected readonly categorySlices = computed<DonutSlice[]>(() => {
    const data = this.byCategory();
    if (!data) {
      return [];
    }
    return data.slices.map((slice, index) => ({
      id: slice.categoryId ?? `uncategorized-${index}`,
      label: slice.name,
      value: toNumber(slice.amount),
      color: slice.colorHex ?? undefined,
      sublabel: formatSignedRealVariation(slice.variationPct) ?? undefined,
    }));
  });

  protected readonly categoryVariations = computed<CategoryVariationItem[]>(() => {
    const data = this.byCategory();
    if (!data) {
      return [];
    }
    const items: CategoryVariationItem[] = [];
    data.slices.forEach((slice, index) => {
      const label = formatSignedRealVariation(slice.variationPct);
      if (!label) {
        return;
      }
      const num = toNumber(slice.variationPct);
      const tone: CategoryVariationItem['tone'] =
        num > 0 ? 'danger' : num < 0 ? 'success' : 'neutral';
      items.push({
        id: slice.categoryId ?? `uncategorized-${index}`,
        name: slice.name,
        icon: slice.icon || 'circle-ellipsis',
        label,
        tone,
      });
    });
    return items;
  });

  protected readonly personSlices = computed<DonutSlice[]>(() => {
    const data = this.byPerson();
    if (!data) {
      return [];
    }
    return data.slices.map((slice) => ({
      id: slice.userId,
      label: slice.fullName,
      value: toNumber(slice.amount),
      color: slice.accentColor || undefined,
    }));
  });
}
