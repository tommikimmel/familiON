import { DOCUMENT } from '@angular/common';
import { DestroyRef, Directive, ElementRef, effect, inject, input } from '@angular/core';

@Directive({
  selector: '[foCountUp]',
  standalone: true,
})
export class CountUpDirective {
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  readonly foCountUp = input.required<number>();
  readonly foCountUpDuration = input<number>(280);
  readonly foCountUpFormatter = input<(value: number) => string>((v) => Math.round(v).toString());

  private currentValue = 0;
  private hasInitialized = false;
  private rafId: number | null = null;

  constructor() {
    effect(() => {
      const target = Number.isFinite(this.foCountUp()) ? this.foCountUp() : 0;
      const duration = this.foCountUpDuration();
      const formatter = this.foCountUpFormatter();

      this.cancelAnimation();

      const from = this.hasInitialized ? this.currentValue : 0;
      this.hasInitialized = true;

      if (duration <= 0 || this.prefersReducedMotion() || !this.canAnimate()) {
        this.currentValue = target;
        this.el.nativeElement.textContent = formatter(target);
        return;
      }

      const win = this.doc.defaultView!;
      const startTime = win.performance?.now?.() ?? Date.now();

      const step = (now: number): void => {
        const elapsed = Math.max(0, now - startTime);
        const progress = Math.min(1, elapsed / duration);
        // Ease-out cubic
        const eased = 1 - (1 - progress) ** 3;
        const value = from + (target - from) * eased;

        this.currentValue = value;
        this.el.nativeElement.textContent = formatter(value);

        if (progress < 1) {
          this.rafId = win.requestAnimationFrame(step);
        } else {
          this.currentValue = target;
          this.el.nativeElement.textContent = formatter(target);
          this.rafId = null;
        }
      };

      this.rafId = win.requestAnimationFrame(step);
    });

    this.destroyRef.onDestroy(() => this.cancelAnimation());
  }

  private cancelAnimation(): void {
    if (this.rafId !== null) {
      this.doc.defaultView?.cancelAnimationFrame?.(this.rafId);
      this.rafId = null;
    }
  }

  private canAnimate(): boolean {
    const win = this.doc.defaultView;
    return Boolean(win && typeof win.requestAnimationFrame === 'function');
  }

  private prefersReducedMotion(): boolean {
    const win = this.doc.defaultView;
    if (!win || typeof win.matchMedia !== 'function') {
      return false;
    }
    return win.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
