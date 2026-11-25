import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { LogoComponent } from '../../shared/ui';
import { InstallPromptService } from './install-prompt.service';

export const SPLASH_FADE_START_MS = 440;
export const SPLASH_TOTAL_DURATION_MS = 600;
const SPLASH_PLAYED_KEY = 'fo.standaloneSplashPlayed';

@Component({
  selector: 'fo-pwa-splash',
  standalone: true,
  imports: [LogoComponent],
  template: `
    @if (visible()) {
      <div
        data-testid="pwa-standalone-splash"
        aria-hidden="true"
        class="fo-pwa-splash fixed inset-0 z-50 flex items-center justify-center bg-bg"
        [class.fo-pwa-splash--leaving]="leaving()"
      >
        <fo-logo [animate]="true" />
      </div>
    }
  `,
  styles: `
    .fo-pwa-splash {
      opacity: 1;
      transition: opacity 160ms ease-out;
    }

    .fo-pwa-splash--leaving {
      opacity: 0;
      pointer-events: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PwaSplashComponent {
  private readonly doc = inject(DOCUMENT);
  private readonly installPrompt = inject(InstallPromptService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly visible = signal<boolean>(false);
  protected readonly leaving = signal<boolean>(false);

  private fadeTimerId: ReturnType<typeof setTimeout> | null = null;
  private hideTimerId: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    if (this.shouldPlaySplash()) {
      this.markPlayed();
      this.visible.set(true);

      this.fadeTimerId = setTimeout(() => {
        this.leaving.set(true);
        this.fadeTimerId = null;
      }, SPLASH_FADE_START_MS);

      this.hideTimerId = setTimeout(() => {
        this.visible.set(false);
        this.hideTimerId = null;
      }, SPLASH_TOTAL_DURATION_MS);
    }

    this.destroyRef.onDestroy(() => {
      if (this.fadeTimerId !== null) {
        clearTimeout(this.fadeTimerId);
        this.fadeTimerId = null;
      }
      if (this.hideTimerId !== null) {
        clearTimeout(this.hideTimerId);
        this.hideTimerId = null;
      }
    });
  }

  private shouldPlaySplash(): boolean {
    if (!this.installPrompt.isStandalone()) {
      return false;
    }
    if (this.prefersReducedMotion()) {
      return false;
    }
    return !this.hasPlayedInSession();
  }

  private prefersReducedMotion(): boolean {
    const win = this.doc.defaultView;
    if (!win || typeof win.matchMedia !== 'function') {
      return false;
    }
    return win.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  private hasPlayedInSession(): boolean {
    try {
      return this.doc.defaultView?.sessionStorage?.getItem(SPLASH_PLAYED_KEY) === '1';
    } catch {
      return false;
    }
  }

  private markPlayed(): void {
    try {
      this.doc.defaultView?.sessionStorage?.setItem(SPLASH_PLAYED_KEY, '1');
    } catch {
      // Ignore storage errors in restricted contexts.
    }
  }
}
