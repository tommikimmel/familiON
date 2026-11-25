import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  INSTALL_PROMPT_COOLDOWN_MS,
  INSTALL_PROMPT_DISMISSED_KEY,
  InstallPromptService,
} from './install-prompt.service';
import { BeforeInstallPromptEvent } from './pwa.models';
import { PWA_NOW } from './pwa.tokens';

function setMatchMedia(standalone: boolean): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn((query: string) => ({
      matches: query === '(display-mode: standalone)' ? standalone : false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

function setUserAgent(userAgent: string, maxTouchPoints = 0): void {
  Object.defineProperty(window.navigator, 'userAgent', {
    configurable: true,
    value: userAgent,
  });
  Object.defineProperty(window.navigator, 'maxTouchPoints', {
    configurable: true,
    value: maxTouchPoints,
  });
}

function createBeforeInstallPromptEvent(
  outcome: 'accepted' | 'dismissed' = 'accepted',
): BeforeInstallPromptEvent {
  const event = new Event('beforeinstallprompt', { cancelable: true }) as BeforeInstallPromptEvent;
  Object.defineProperty(event, 'prompt', {
    value: vi.fn().mockResolvedValue(undefined),
  });
  Object.defineProperty(event, 'userChoice', {
    value: Promise.resolve({ outcome, platform: 'web' }),
  });
  return event;
}

describe('InstallPromptService', () => {
  const originalUa = window.navigator.userAgent;
  const originalMatchMedia = window.matchMedia;
  let currentTimeMs: number;

  beforeEach(() => {
    localStorage.clear();
    currentTimeMs = Date.parse('2026-11-25T12:00:00.000Z');
    setMatchMedia(false);
    setUserAgent(
      'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36',
    );
    delete (window.navigator as Navigator & { standalone?: boolean }).standalone;

    TestBed.configureTestingModule({
      providers: [{ provide: PWA_NOW, useValue: () => currentTimeMs }],
    });
  });

  afterEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    setUserAgent(originalUa);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: originalMatchMedia,
    });
    delete (window.navigator as Navigator & { standalone?: boolean }).standalone;
    vi.restoreAllMocks();
  });

  it('never shows install banner when already running in display-mode: standalone', () => {
    setMatchMedia(true);
    setUserAgent(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
      5,
    );

    const service = TestBed.inject(InstallPromptService);
    expect(service.isStandalone()).toBe(true);
    expect(service.shouldShow()).toBe(false);

    const evt = createBeforeInstallPromptEvent('accepted');
    window.dispatchEvent(evt);
    expect(service.shouldShow()).toBe(false);
  });

  it('captures beforeinstallprompt on Android/Chrome and respects 30-day dismissal cooldown', async () => {
    const service = TestBed.inject(InstallPromptService);
    expect(service.shouldShow()).toBe(false);

    const evt = createBeforeInstallPromptEvent('dismissed');
    window.dispatchEvent(evt);

    expect(evt.defaultPrevented).toBe(true);
    expect(service.mode()).toBe('native');
    expect(service.shouldShow()).toBe(true);

    // Dismissing stores timestamp and suppresses for 30 days
    service.dismiss();
    expect(service.shouldShow()).toBe(false);
    expect(localStorage.getItem(INSTALL_PROMPT_DISMISSED_KEY)).toBeTruthy();

    // 29 days later -> still suppressed
    currentTimeMs += INSTALL_PROMPT_COOLDOWN_MS - 60_000;
    window.dispatchEvent(createBeforeInstallPromptEvent('accepted'));
    expect(service.shouldShow()).toBe(false);

    // After 30 days -> allowed again
    currentTimeMs += 120_000;
    const secondEvt = createBeforeInstallPromptEvent('accepted');
    window.dispatchEvent(secondEvt);
    expect(service.shouldShow()).toBe(true);

    const outcome = await service.install();
    expect(outcome).toBe('accepted');
    expect(service.shouldShow()).toBe(false);
  });

  it('shows iOS Safari 2-step guide when not standalone and respects 30-day cooldown', () => {
    setUserAgent(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
      5,
    );

    const service = TestBed.inject(InstallPromptService);
    expect(service.mode()).toBe('ios');
    expect(service.shouldShow()).toBe(true);

    service.dismiss();
    expect(service.shouldShow()).toBe(false);

    currentTimeMs += INSTALL_PROMPT_COOLDOWN_MS - 1000;
    service.evaluate();
    expect(service.shouldShow()).toBe(false);

    currentTimeMs += 2000;
    service.evaluate();
    expect(service.mode()).toBe('ios');
    expect(service.shouldShow()).toBe(true);
  });
});
