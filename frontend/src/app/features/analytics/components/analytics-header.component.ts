import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Granularity, Lens } from '../../../core/state/view-context.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../shared/ui/segmented-toggle/segmented-toggle.component';
import { getLensExplanation, getLensOptions } from '../data-access/analytics.logic';
import { AnalyticsTab } from '../data-access/analytics.models';

const TAB_OPTIONS: readonly SegmentedOption<AnalyticsTab>[] = [
  { value: 'OVERVIEW', label: 'Resumen' },
  { value: 'CALENDAR', label: 'Calendario' },
];

const GRANULARITY_OPTIONS: readonly SegmentedOption<Granularity>[] = [
  { value: 'DAY', label: 'Día' },
  { value: 'WEEK', label: 'Semana' },
  { value: 'MONTH', label: 'Mes' },
  { value: 'YEAR', label: 'Año' },
];

@Component({
  selector: 'fo-analytics-header',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, SegmentedToggleComponent],
  template: `
    <div class="space-y-4">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="text-2xl font-bold tracking-tight text-text">Analítica</h1>
          <p class="text-sm text-muted">
            Entendé tus gastos reales por período, lente y calendario.
          </p>
        </div>

        <div class="w-full sm:w-64">
          <fo-segmented-toggle
            data-testid="analytics-tab-toggle"
            [options]="tabOptions"
            [value]="activeTab()"
            (valueChange)="tabChange.emit($event)"
            ariaLabel="Sección de analítica"
            size="md"
          />
        </div>
      </div>

      @if (activeTab() === 'OVERVIEW') {
        <fo-card padding="md">
          <div class="space-y-4">
            <!-- Row 1: Granularity + Period navigation -->
            <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div class="w-full lg:w-80">
                <fo-segmented-toggle
                  data-testid="analytics-granularity-toggle"
                  [options]="granularityOptions"
                  [value]="granularity()"
                  (valueChange)="granularityChange.emit($event)"
                  ariaLabel="Período de análisis"
                  size="sm"
                />
              </div>

              <div class="flex flex-wrap items-center justify-between gap-2 sm:justify-end">
                <div
                  class="flex items-center gap-1 rounded-full border border-border bg-surface-2/70 p-1"
                >
                  <button
                    type="button"
                    data-testid="analytics-period-prev"
                    aria-label="Período anterior"
                    (click)="shiftPeriod.emit(-1)"
                    class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
                  >
                    <fo-icon name="chevron-left" [size]="16" />
                  </button>

                  <span
                    data-testid="analytics-period-label"
                    class="min-w-32 px-2 text-center text-sm font-semibold text-text tabular-nums"
                  >
                    {{ periodLabel() }}
                  </span>

                  <button
                    type="button"
                    data-testid="analytics-period-next"
                    aria-label="Período siguiente"
                    (click)="shiftPeriod.emit(1)"
                    class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
                  >
                    <fo-icon name="chevron-right" [size]="16" />
                  </button>
                </div>

                <fo-button
                  data-testid="analytics-period-today"
                  variant="secondary"
                  size="sm"
                  (click)="resetToday.emit()"
                >
                  Hoy
                </fo-button>
              </div>
            </div>

            <!-- Row 2: Explicit 4-lens selector + 1-line explanation -->
            <div class="space-y-2 border-t border-border/80 pt-3">
              <div
                role="radiogroup"
                aria-label="Lente de visualización"
                data-testid="analytics-lens-selector"
                class="grid grid-cols-2 gap-1.5 rounded-2xl border border-border bg-surface-2 p-1 sm:grid-cols-4"
              >
                @for (option of lensOptions(); track option.value; let i = $index) {
                  @let isSelected = option.value === lens();
                  <button
                    type="button"
                    role="radio"
                    [attr.aria-checked]="isSelected"
                    [attr.tabindex]="isSelected ? 0 : -1"
                    [attr.data-testid]="'analytics-lens-' + option.value"
                    (click)="selectLens(option.value)"
                    (keydown)="onLensKeydown($event, i)"
                    class="fo-focus-ring cursor-pointer rounded-xl px-3 py-2 text-center text-xs font-medium transition-colors duration-150 sm:text-sm"
                    [class.bg-surface]="isSelected"
                    [class.text-text]="isSelected"
                    [class.shadow-xs]="isSelected"
                    [class.text-muted]="!isSelected"
                    [class.hover:text-text]="!isSelected"
                  >
                    {{ option.label }}
                  </button>
                }
              </div>

              <p
                data-testid="analytics-lens-explanation"
                class="flex items-center gap-1.5 text-xs text-muted"
              >
                <fo-icon name="eye" [size]="14" class="shrink-0 text-accent" />
                <span>{{ activeLensExplanation() }}</span>
              </p>
            </div>
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
export class AnalyticsHeaderComponent {
  readonly activeTab = input.required<AnalyticsTab>();
  readonly granularity = input.required<Granularity>();
  readonly periodLabel = input.required<string>();
  readonly lens = input.required<Lens>();
  readonly referenceCurrency = input<string>('USD');

  readonly tabChange = output<AnalyticsTab>();
  readonly granularityChange = output<Granularity>();
  readonly shiftPeriod = output<-1 | 1>();
  readonly resetToday = output<void>();
  readonly lensChange = output<Lens>();

  protected readonly tabOptions = TAB_OPTIONS;
  protected readonly granularityOptions = GRANULARITY_OPTIONS;

  protected readonly lensOptions = computed(() => getLensOptions(this.referenceCurrency()));

  protected readonly activeLensExplanation = computed(() =>
    getLensExplanation(this.lens(), this.referenceCurrency()),
  );

  protected selectLens(next: Lens): void {
    if (this.lens() !== next) {
      this.lensChange.emit(next);
    }
  }

  protected onLensKeydown(event: KeyboardEvent, currentIndex: number): void {
    const items = this.lensOptions();
    if (items.length === 0) {
      return;
    }

    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      nextIndex = (currentIndex + 1) % items.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      nextIndex = (currentIndex - 1 + items.length) % items.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = items.length - 1;
    }

    if (nextIndex === null) {
      return;
    }

    event.preventDefault();
    this.selectLens(items[nextIndex].value);

    const parent = (event.currentTarget as HTMLElement | null)?.parentElement;
    const buttons = parent?.querySelectorAll<HTMLButtonElement>('button[role="radio"]');
    buttons?.[nextIndex]?.focus();
  }
}
