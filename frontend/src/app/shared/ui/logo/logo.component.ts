import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';

@Component({
  selector: 'fo-logo',
  standalone: true,
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 190 56"
      [attr.height]="compact() ? 28 : 36"
      [attr.width]="compact() ? 95 : 122"
      role="img"
      aria-label="FamiliON"
      class="block select-none"
    >
      <text
        x="104"
        y="40"
        text-anchor="end"
        class="fo-logo-wordmark"
      >
        Famili
      </text>

      <rect
        x="110"
        y="12"
        width="70"
        height="32"
        rx="16"
        class="fo-logo-track"
        [attr.fill]="isOn() ? 'var(--fo-accent, #0f766e)' : 'var(--fo-border, #e4e4e7)'"
      />

      <g [attr.opacity]="isOn() ? 1 : 0.35" class="fo-logo-on-label">
        <circle
          cx="127"
          cy="28"
          r="4.5"
          fill="none"
          stroke="var(--fo-accent-contrast, #ffffff)"
          stroke-width="2.2"
        />
        <path
          d="M136.5 32.5V23.5L143.5 32.5V23.5"
          fill="none"
          stroke="var(--fo-accent-contrast, #ffffff)"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </g>

      <circle
        cx="126"
        cy="28"
        r="11.5"
        fill="var(--fo-accent-contrast, #ffffff)"
        class="fo-logo-knob"
        [class.fo-logo-knob--on]="isOn()"
      />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      color: var(--fo-text);
    }

    .fo-logo-wordmark {
      font-family: 'Inter Variable', 'Inter', system-ui, sans-serif;
      font-size: 34px;
      font-weight: 700;
      letter-spacing: -1.1px;
      fill: currentColor;
    }

    .fo-logo-track {
      transition: fill 240ms ease;
    }

    .fo-logo-on-label {
      transition: opacity 240ms ease;
    }

    .fo-logo-knob {
      transform: translateX(0);
      transition: transform 360ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1));
    }

    .fo-logo-knob--on {
      transform: translateX(38px);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoComponent {
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  readonly compact = input<boolean>(false);
  readonly animate = input<boolean>(false);

  protected readonly isOn = signal<boolean>(true);
  private timerId: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      this.clearPendingTimer();
      if (!this.animate() || this.prefersReducedMotion()) {
        this.isOn.set(true);
        return;
      }

      this.isOn.set(false);
      this.timerId = setTimeout(() => {
        this.isOn.set(true);
        this.timerId = null;
      }, 40);
    });

    this.destroyRef.onDestroy(() => this.clearPendingTimer());
  }

  private clearPendingTimer(): void {
    if (this.timerId !== null) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
  }

  private prefersReducedMotion(): boolean {
    const win = this.doc.defaultView;
    if (!win || typeof win.matchMedia !== 'function') {
      return false;
    }
    return win.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
