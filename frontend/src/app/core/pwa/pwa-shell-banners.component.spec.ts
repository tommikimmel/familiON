import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SwUpdate, UnrecoverableStateEvent, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PwaShellBannersComponent } from './pwa-shell-banners.component';
import { PwaSplashComponent, SPLASH_TOTAL_DURATION_MS } from './pwa-splash.component';
import { PWA_RELOAD_PAGE } from './pwa.tokens';

describe('PWA UI components', () => {
  let httpMock: HttpTestingController;
  let versionUpdates$: Subject<VersionEvent>;
  let unrecoverable$: Subject<UnrecoverableStateEvent>;
  let reloadSpy: ReturnType<typeof vi.fn>;
  let originalMatchMedia: PropertyDescriptor | undefined;

  beforeEach(() => {
    originalMatchMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia');
    localStorage.clear();
    sessionStorage.clear();
    versionUpdates$ = new Subject<VersionEvent>();
    unrecoverable$ = new Subject<UnrecoverableStateEvent>();
    reloadSpy = vi.fn();

    TestBed.configureTestingModule({
      imports: [PwaShellBannersComponent, PwaSplashComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SwUpdate,
          useValue: {
            isEnabled: true,
            versionUpdates: versionUpdates$.asObservable(),
            unrecoverable: unrecoverable$.asObservable(),
            checkForUpdate: vi.fn().mockResolvedValue(false),
            activateUpdate: vi.fn().mockResolvedValue(true),
          },
        },
        { provide: PWA_RELOAD_PAGE, useValue: reloadSpy },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    if (originalMatchMedia) {
      Object.defineProperty(window, 'matchMedia', originalMatchMedia);
    } else {
      Reflect.deleteProperty(window, 'matchMedia');
    }
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('renders update banner and opens changelog modal safely escaping HTML', async () => {
    const fixture = TestBed.createComponent(PwaShellBannersComponent);
    fixture.detectChanges();

    versionUpdates$.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'v1' },
      latestVersion: { hash: 'v2' },
    });
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/system/version').flush({
      version: '2.1.0',
      latestRelease: { version: '2.1.0', title: 'PWA', releasedAt: '2026-11-25T10:00:00Z' },
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const banner = host.querySelector('[data-testid="pwa-update-banner"]');
    expect(banner?.textContent).toContain('FamiliON v2.1.0 disponible');
    expect(banner?.textContent).toContain('Actualizar ahora');

    const whatsNewBtn = host.querySelector('[data-testid="pwa-whats-new-btn"]') as HTMLButtonElement;
    whatsNewBtn.click();
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/system/changelog').flush([
      {
        version: '2.1.0',
        title: 'Centro de notificaciones',
        changelogMarkdown: '## Novedades\n- **Push** listo\n- <script>alert(1)</script>',
        isBreaking: false,
        releasedAt: '2026-11-25T10:00:00Z',
      },
    ]);
    await fixture.whenStable();
    fixture.detectChanges();

    const modal = host.querySelector('[data-testid="pwa-changelog-modal"]');
    expect(modal?.textContent).toContain('Centro de notificaciones');
    expect(modal?.querySelector('strong')?.textContent).toBe('Push');
    expect(modal?.querySelector('script')).toBeNull();
    expect(modal?.textContent).toContain('<script>alert(1)</script>');
  });

  it('plays standalone splash for <= 600ms and skips when prefers-reduced-motion is enabled', () => {
    vi.useFakeTimers();

    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn((query: string) => ({
        matches: query === '(display-mode: standalone)',
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    const fixture = TestBed.createComponent(PwaSplashComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="pwa-standalone-splash"]')).not.toBeNull();

    vi.advanceTimersByTime(SPLASH_TOTAL_DURATION_MS);
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="pwa-standalone-splash"]')).toBeNull();

    // Verify reduced motion skips splash
    sessionStorage.clear();
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: vi.fn((query: string) => ({
        matches:
          query === '(display-mode: standalone)' || query === '(prefers-reduced-motion: reduce)',
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    const reducedFixture = TestBed.createComponent(PwaSplashComponent);
    reducedFixture.detectChanges();
    expect(
      (reducedFixture.nativeElement as HTMLElement).querySelector(
        '[data-testid="pwa-standalone-splash"]',
      ),
    ).toBeNull();
  });
});
