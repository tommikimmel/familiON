import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  LucideAlertCircle,
  LucideArrowLeftRight,
  LucideBaby,
  LucideBanknote,
  LucideBell,
  LucideBriefcase,
  LucideBus,
  LucideCalendarRange,
  LucideCamera,
  LucideCar,
  LucideChartPie,
  LucideCheck,
  LucideCheckCircle,
  LucideChevronDown,
  LucideChevronLeft,
  LucideChevronRight,
  LucideCircleEllipsis,
  LucideCreditCard,
  LucideDynamicIcon,
  LucideEye,
  LucideEyeOff,
  LucideFileText,
  LucideFuel,
  LucideGift,
  LucideGraduationCap,
  LucideHeartPulse,
  LucideHome,
  LucideHouse,
  LucideIcon,
  LucideKeyRound,
  LucideLandmark,
  LucideLaptop,
  LucideLock,
  LucideMonitor,
  LucideMoon,
  LucidePartyPopper,
  LucidePawPrint,
  LucidePencil,
  LucidePercent,
  LucidePiggyBank,
  LucidePlugZap,
  LucidePlus,
  LucideRepeat,
  LucideShield,
  LucideShirt,
  LucideShoppingCart,
  LucideSiren,
  LucideSmartphone,
  LucideSofa,
  LucideSparkles,
  LucideStar,
  LucideSun,
  LucideTicket,
  LucideTrash2,
  LucideTrendingUp,
  LucideUser,
  LucideUsers,
  LucideWallet,
  LucideWrench,
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
  eye: LucideEye,
  'eye-off': LucideEyeOff,
  banknote: LucideBanknote,
  landmark: LucideLandmark,
  'credit-card': LucideCreditCard,
  'piggy-bank': LucidePiggyBank,
  smartphone: LucideSmartphone,
  lock: LucideLock,
  pencil: LucidePencil,
  'trash-2': LucideTrash2,
  star: LucideStar,
  'shopping-cart': LucideShoppingCart,
  house: LucideHouse,
  'plug-zap': LucidePlugZap,
  'heart-pulse': LucideHeartPulse,
  'graduation-cap': LucideGraduationCap,
  fuel: LucideFuel,
  wrench: LucideWrench,
  shield: LucideShield,
  'file-text': LucideFileText,
  ticket: LucideTicket,
  siren: LucideSiren,
  'party-popper': LucidePartyPopper,
  bus: LucideBus,
  shirt: LucideShirt,
  sofa: LucideSofa,
  'paw-print': LucidePawPrint,
  baby: LucideBaby,
  laptop: LucideLaptop,
  repeat: LucideRepeat,
  gift: LucideGift,
  percent: LucidePercent,
  'circle-ellipsis': LucideCircleEllipsis,
  briefcase: LucideBriefcase,
  sparkles: LucideSparkles,
  'key-round': LucideKeyRound,
  'trending-up': LucideTrendingUp,
} as const satisfies Record<string, LucideIcon>;

export type FoIconName = keyof typeof ICON_REGISTRY;

export function isKnownFoIcon(name: string | null | undefined): name is FoIconName {
  return typeof name === 'string' && Object.prototype.hasOwnProperty.call(ICON_REGISTRY, name);
}

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
  readonly name = input.required<string>();
  readonly size = input<number>(20);
  readonly strokeWidth = input<number>(2);
  readonly label = input<string | null>(null);

  protected readonly iconComponent = computed<LucideIcon>(() => {
    const requested = this.name();
    return isKnownFoIcon(requested) ? ICON_REGISTRY[requested] : ICON_REGISTRY['circle-ellipsis'];
  });
}
