import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  LucideAlertCircle,
  LucideArrowLeftRight,
  LucideBell,
  LucideCalendarRange,
  LucideCamera,
  LucideCar,
  LucideChartPie,
  LucideCheck,
  LucideCheckCircle,
  LucideChevronDown,
  LucideChevronLeft,
  LucideChevronRight,
  LucideDynamicIcon,
  LucideHome,
  LucideIcon,
  LucideMonitor,
  LucideMoon,
  LucidePlus,
  LucideSun,
  LucideUser,
  LucideUsers,
  LucideWallet,
  LucideX,
} from '@lucide/angular';

const ICON_REGISTRY = {
  home: LucideHome,
  'calendar-range': LucideCalendarRange,
  plus: LucidePlus,
  car: LucideCar,
  user: LucideUser,
  users: LucideUsers,
  bell: LucideBell,
  camera: LucideCamera,
  'chevron-left': LucideChevronLeft,
  'chevron-right': LucideChevronRight,
  'chevron-down': LucideChevronDown,
  check: LucideCheck,
  'check-circle': LucideCheckCircle,
  'alert-circle': LucideAlertCircle,
  x: LucideX,
  wallet: LucideWallet,
  'chart-pie': LucideChartPie,
  'arrow-left-right': LucideArrowLeftRight,
  sun: LucideSun,
  moon: LucideMoon,
  monitor: LucideMonitor,
} as const satisfies Record<string, LucideIcon>;

export type FoIconName = keyof typeof ICON_REGISTRY;

@Component({
  selector: 'fo-icon',
  standalone: true,
  imports: [LucideDynamicIcon],
  template: `
    <svg
      [lucideIcon]="iconComponent()"
      [size]="size()"
      [strokeWidth]="strokeWidth()"
      [title]="label()"
      class="inline-block shrink-0"
    ></svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 0;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  readonly name = input.required<FoIconName>();
  readonly size = input<number>(20);
  readonly strokeWidth = input<number>(2);
  readonly label = input<string | null>(null);

  protected readonly iconComponent = computed<LucideIcon>(() => ICON_REGISTRY[this.name()]);
}
