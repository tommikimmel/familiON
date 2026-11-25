import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FoIconName, IconComponent } from '../../../shared/ui';
import {
  ALERT_SEVERITY_LABEL,
  formatIsoDateShort,
  formatKm,
} from '../data-access/vehicle-labels';
import { VehicleAlert } from '../data-access/vehicles.models';

@Component({
  selector: 'fo-vehicle-alert-chip',
  standalone: true,
  imports: [IconComponent],
  template: `
    <span
      [attr.data-testid]="'vehicle-alert-' + alert().severity"
      class="inline-flex max-w-full items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium"
      [class.border-danger/30]="alert().severity === 'OVERDUE'"
      [class.bg-danger/10]="alert().severity === 'OVERDUE'"
      [class.text-danger]="alert().severity === 'OVERDUE'"
      [class.border-warning/30]="alert().severity === 'WARNING'"
      [class.bg-warning/10]="alert().severity === 'WARNING'"
      [class.text-warning]="alert().severity === 'WARNING'"
      [class.border-accent/30]="alert().severity === 'INFO'"
      [class.bg-accent-soft]="alert().severity === 'INFO'"
      [class.text-accent]="alert().severity === 'INFO'"
    >
      <fo-icon [name]="iconName()" [size]="13" />
      <span class="truncate">{{ alert().title }}</span>
      @if (dueSummary(); as due) {
        <span class="shrink-0 opacity-85 tabular-nums">· {{ due }}</span>
      }
      <span class="sr-only">({{ severityLabel() }})</span>
    </span>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehicleAlertChipComponent {
  readonly alert = input.required<VehicleAlert>();
  readonly compact = input<boolean>(false);

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
