import { DOCUMENT } from '@angular/common';
import {
  DestroyRef,
  Injectable,
  Signal,
  computed,
  inject,
  signal,
} from '@angular/core';
import { BeforeInstallPromptEvent, InstallPromptMode } from './pwa.models';
import { PWA_NOW } from './pwa.tokens';

export const INSTALL_PROMPT_DISMISSED_KEY = 'fo.installPromptDismissedAt';
export const INSTALL_PROMPT_COOLDOWN_MS = 30 * 24 * 60 * 60 * 1000;

interface IosNavigator extends Navigator {
  standalone?: boolean;
}

@Injectable({ providedIn: 'root' })
export class InstallPromptService {
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly now = inject(PWA_NOW);

  private readonly _mode = signal<InstallPromptMode>('none');
  private readonly _installed = signal<boolean>(false);

  private deferredPrompt: BeforeInstallPromptEvent | null = null;

  readonly mode: Signal<InstallPromptMode> = this._mode.asReadonly();
  readonly shouldShow: Signal<boolean> = computed(() => this._mode() !== 'none');

  private readonly onBeforeInstallPrompt = (event: Event): void => {
    const promptEvent = event as BeforeInstallPromptEvent;
    if (typeof promptEvent.prompt !== 'function') {
      return;
    }
    promptEvent.preventDefault();
    this.deferredPrompt = promptEvent;
    this.evaluate();
  };

  private readonly onAppInstalled = (): void => {
    this.deferredPrompt = null;
    this._installed.set(true);
    this._mode.set('none');
  };

  constructor() {
    const win = this.doc.defaultView;
    if (win) {
      win.addEventListener('beforeinstallprompt', this.onBeforeInstallPrompt);
      win.addEventListener('appinstalled', this.onAppInstalled);
    }

    this.evaluate();

    this.destroyRef.onDestroy(() => {
      if (win) {
        win.removeEventListener('beforeinstallprompt', this.onBeforeInstallPrompt);
        win.removeEventListener('appinstalled', this.onAppInstalled);
      }
    });
  }

  evaluate(): void {
    if (this._installed() || this.isStandalone() || this.isWithinCooldown()) {
      this._mode.set('none');
      return;
    }

    if (this.deferredPrompt !== null) {
      this._mode.set('native');
      return;
    }

    if (this.isIosSafari()) {
      this._mode.set('ios');
      return;
    }

    this._mode.set('none');
  }

  async install(): Promise<'accepted' | 'dismissed' | 'unavailable'> {
    const promptEvent = this.deferredPrompt;
    if (!promptEvent) {
      return 'unavailable';
    }

    this.deferredPrompt = null;
    try {
      await promptEvent.prompt();
      const choice = await promptEvent.userChoice;
      if (choice.outcome === 'accepted') {
        this._installed.set(true);
        this._mode.set('none');
      } else {
        this.dismiss();
      }
      return choice.outcome;
    } catch {
      this.dismiss();
      return 'dismissed';
    }
  }

  dismiss(): void {
    this.saveDismissedAt(this.now());
    this._mode.set('none');
  }

  isStandalone(): boolean {
    const win = this.doc.defaultView;
    if (!win) {
      return false;
    }
    if (
      typeof win.matchMedia === 'function' &&
      win.matchMedia('(display-mode: standalone)').matches
    ) {
      return true;
    }
    const nav = win.navigator as IosNavigator | undefined;
    return nav?.standalone === true;
  }

  isIosSafari(): boolean {
    const win = this.doc.defaultView;
    const nav = win?.navigator;
    if (!nav) {
      return false;
    }

    const ua = nav.userAgent ?? '';
    const isIosDevice =
      /iphone|ipad|ipod/i.test(ua) ||
      (/macintosh/i.test(ua) && typeof nav.maxTouchPoints === 'number' && nav.maxTouchPoints > 1);
    if (!isIosDevice) {
      return false;
    }

    const isSafari = /safari/i.test(ua) && !/crios|fxios|edgios|opios|chrome|android/i.test(ua);
    return isSafari;
  }

  private isWithinCooldown(): boolean {
    const dismissedAt = this.readDismissedAt();
    if (dismissedAt === null) {
      return false;
    }
    const elapsed = this.now() - dismissedAt;
    return elapsed >= 0 && elapsed < INSTALL_PROMPT_COOLDOWN_MS;
  }

  private readDismissedAt(): number | null {
    const storage = this.getStorage();
    if (!storage) {
      return null;
    }
    try {
      const raw = storage.getItem(INSTALL_PROMPT_DISMISSED_KEY);
      if (!raw) {
        return null;
      }
      const asNumber = Number(raw);
      if (Number.isFinite(asNumber) && asNumber > 0) {
        return asNumber;
      }
      const asDate = Date.parse(raw);
      return Number.isFinite(asDate) ? asDate : null;
    } catch {
      return null;
    }
  }

  private saveDismissedAt(timestampMs: number): void {
    const storage = this.getStorage();
    if (!storage) {
      return;
    }
    try {
      storage.setItem(INSTALL_PROMPT_DISMISSED_KEY, new Date(timestampMs).toISOString());
    } catch {
      // Ignore storage quota / private browsing errors.
    }
  }

  private getStorage(): Storage | null {
    try {
      return this.doc.defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  }
}
