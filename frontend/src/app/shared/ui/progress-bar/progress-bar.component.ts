import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Thin progress bar (mock `.progress-track` + `.progress-fill`). */
@Component({
  selector: 'fo-progress-bar',
  standalone: true,
  template: `
    <div
      class="fo-progress-track"
      role="progressbar"
      aria-valuemin="0"
      aria-valuemax="100"
      [attr.aria-valuenow]="percent()"
      [attr.aria-label]="ariaLabel()"
    >
      <div class="fo-progress-fill" [style.width.%]="percent()" [style.background]="color()"></div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressBarComponent {
  /** Progress in the 0–100 range; values outside are clamped. */
  readonly value = input.required<number>();
  readonly color = input<string | null>(null);
  readonly ariaLabel = input<string>('Progreso');

  readonly percent = computed(() => {
    const raw = this.value();
    if (!Number.isFinite(raw)) {
      return 0;
    }
    return Math.round(Math.min(100, Math.max(0, raw)));
  });
}
