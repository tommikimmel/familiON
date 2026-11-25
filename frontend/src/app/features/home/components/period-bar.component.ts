import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Granularity } from '../../../core/state/view-context.store';
import { PeriodLabelPipe } from '../../../shared/pipes/period-label.pipe';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../shared/ui/segmented-toggle/segmented-toggle.component';

const GRANULARITY_OPTIONS: readonly SegmentedOption<Granularity>[] = [
  { value: 'DAY', label: 'Día' },
  { value: 'WEEK', label: 'Sem' },
  { value: 'MONTH', label: 'Mes' },
  { value: 'YEAR', label: 'Año' },
];

/** Mobile granularity tabs + period label (mock `.gran-row`). */
@Component({
  selector: 'fo-period-bar',
  standalone: true,
  imports: [IconComponent, PeriodLabelPipe, SegmentedToggleComponent],
  template: `
    <div class="flex items-center justify-between gap-2">
      <fo-segmented-toggle
        variant="tabs"
        [options]="options"
        [value]="granularity()"
        (valueChange)="granularityChange.emit($event)"
        ariaLabel="Granularidad del período"
      />
      <div class="flex items-center gap-0.5">
        <button
          type="button"
          aria-label="Período anterior"
          class="period-nav fo-focus-ring"
          (click)="shift.emit(-1)"
        >
          <fo-icon name="chevron-left" [size]="13" />
        </button>
        <button
          type="button"
          title="Volver a hoy"
          class="period-label fo-focus-ring"
          (click)="today.emit()"
        >
          {{ anchorDate() | periodLabel: granularity() }}
        </button>
        <button
          type="button"
          aria-label="Período siguiente"
          class="period-nav fo-focus-ring"
          (click)="shift.emit(1)"
        >
          <fo-icon name="chevron-right" [size]="13" />
        </button>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .period-label {
      border: none;
      background: transparent;
      color: var(--fo-text-muted);
      font-family: inherit;
      font-size: 11px;
      font-weight: 500;
      font-variant-numeric: tabular-nums;
      cursor: pointer;
      border-radius: 6px;
    }
    .period-nav {
      display: grid;
      place-items: center;
      width: 18px;
      height: 18px;
      border: none;
      border-radius: 6px;
      background: transparent;
      color: var(--fo-text-muted);
      cursor: pointer;
    }
    .period-nav:hover,
    .period-label:hover {
      color: var(--fo-text);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PeriodBarComponent {
  readonly granularity = input.required<Granularity>();
  readonly anchorDate = input.required<string>();
  readonly granularityChange = output<Granularity>();
  readonly shift = output<-1 | 1>();
  readonly today = output<void>();

  protected readonly options = GRANULARITY_OPTIONS;
}
