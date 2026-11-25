import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { clampProgressPct } from '../utils/goal-formatters';

@Component({
  selector: 'fo-goal-progress-ring',
  standalone: true,
  template: `
    <div
      class="relative inline-flex items-center justify-center shrink-0"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [attr.aria-label]="ariaLabel()"
      role="progressbar"
      [attr.aria-valuenow]="clampedPercent()"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <svg
        [attr.width]="size()"
        [attr.height]="size()"
        class="-rotate-90 transform"
        aria-hidden="true"
      >
        <!-- Background track -->
        <circle
          [attr.cx]="center()"
          [attr.cy]="center()"
          [attr.r]="radius()"
          fill="none"
          stroke="currentColor"
          [attr.stroke-width]="strokeWidth()"
          class="text-border"
        />

        <!-- Progress ring -->
        <circle
          [attr.cx]="center()"
          [attr.cy]="center()"
          [attr.r]="radius()"
          fill="none"
          [attr.stroke]="ringColor()"
          [attr.stroke-width]="strokeWidth()"
          stroke-linecap="round"
          [attr.stroke-dasharray]="circumference()"
          [attr.stroke-dashoffset]="dashOffset()"
          class="transition-[stroke-dashoffset] duration-500 motion-reduce:duration-0"
        />
      </svg>

      <!-- Center content -->
      <div class="absolute inset-0 flex flex-col items-center justify-center text-center">
        @if (isCompleted()) {
          <span class="text-xs font-bold text-success tabular-nums">100%</span>
        } @else {
          <span class="text-xs font-semibold text-text tabular-nums">
            {{ roundedPercent() }}%
          </span>
        }
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalProgressRingComponent {
  readonly progressPct = input<string | number>(0);
  readonly size = input<number>(68);
  readonly strokeWidth = input<number>(6);
  readonly customColor = input<string | null>(null);

  protected readonly clampedPercent = computed(() => clampProgressPct(this.progressPct()));
  protected readonly isCompleted = computed(() => this.clampedPercent() >= 100);
  protected readonly roundedPercent = computed(() => Math.round(this.clampedPercent()));

  protected readonly center = computed(() => this.size() / 2);
  protected readonly radius = computed(() => (this.size() - this.strokeWidth()) / 2);
  protected readonly circumference = computed(() => 2 * Math.PI * this.radius());

  protected readonly dashOffset = computed(() => {
    const fraction = this.clampedPercent() / 100;
    return this.circumference() * (1 - fraction);
  });

  protected readonly ringColor = computed(() => {
    if (this.customColor()) {
      return this.customColor()!;
    }
    if (this.isCompleted()) {
      return 'var(--fo-color-success, #10B981)';
    }
    return 'var(--fo-color-accent, #0F766E)';
  });

  protected readonly ariaLabel = computed(() => `Progreso: ${this.roundedPercent()}%`);
}
