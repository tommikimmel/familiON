import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FoIconName, IconComponent } from '../../../shared/ui';
import { ALERT_SEVERITY_LABEL, formatIsoDateShort, formatKm } from '../data-access/vehicle-labels';
import { VehicleAlert } from '../data-access/vehicles.models';

@Component({
  selector: 'fo-vehicle-alert-chip',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (layout() === 'row') {
      <span class="alert-row-title">
        <fo-icon [name]="iconName()" [size]="13" />
        <span class="truncate">{{ alert().title }}</span>
      </span>
      <span
        [attr.data-testid]="'vehicle-alert-' + alert().severity"
        class="fo-pill-tag fo-num shrink-0"
        [class.alert-danger]="alert().severity === 'OVERDUE'"
        [class.alert-warning]="alert().severity === 'WARNING'"
        [class.fo-pill-accent]="alert().severity === 'INFO'"
      >
        {{ dueSummary() ?? severityLabel() }}
        <span class="sr-only">({{ severityLabel() }})</span>
      </span>
    } @else {
      <span
        [attr.data-testid]="'vehicle-alert-' + alert().severity"
        class="fo-pill-tag max-w-full gap-1"
        [class.alert-danger]="alert().severity === 'OVERDUE'"
        [class.alert-warning]="alert().severity === 'WARNING'"
        [class.fo-pill-accent]="alert().severity === 'INFO'"
      >
        <fo-icon [name]="iconName()" [size]="11" />
        <span class="truncate">{{ alert().title }}</span>
        @if (dueSummary(); as due) {
          <span class="shrink-0 opacity-85 fo-num">· {{ due }}</span>
        }
        <span class="sr-only">({{ severityLabel() }})</span>
      </span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      max-width: 100%;
    }
    :host(.vh-row) {
      display: flex;
    }
    .alert-row-title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
      font-size: 12px;
    }
    .alert-danger {
      background: color-mix(in srgb, var(--fo-danger) 12%, transparent);
      color: var(--fo-danger);
    }
    .alert-warning {
      background: color-mix(in srgb, var(--fo-warning) 14%, transparent);
      color: var(--fo-warning);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleAlertChipComponent {
  readonly alert = input.required<VehicleAlert>();
  readonly compact = input<boolean>(false);
  /** `row` renders a title + due pill line (mock «Desgaste y vencimientos»). */
  readonly layout = input<'chip' | 'row'>('chip');

  protected readonly severityLabel = computed(() => ALERT_SEVERITY_LABEL[this.alert().severity]);

  protected readonly iconName = computed<FoIconName>(() => {
    const item = this.alert();
    if (item.severity === 'OVERDUE') {
      return 'siren';
    }
    switch (item.kind) {
      case 'INSURANCE':
      case 'VTV':
        return 'shield';
      case 'SERVICE_DATE':
        return 'calendar-range';
      case 'SERVICE_KM':
        return 'wrench';
    }
  });

  protected readonly dueSummary = computed<string | null>(() => {
    if (this.compact()) {
      return null;
    }
    const item = this.alert();
    if (item.dueKm !== null && item.dueDate) {
      return `${formatKm(item.dueKm)} o ${formatIsoDateShort(item.dueDate)}`;
    }
    if (item.dueKm !== null) {
      return formatKm(item.dueKm);
    }
    if (item.dueDate) {
      return formatIsoDateShort(item.dueDate);
    }
    return null;
  });
}
