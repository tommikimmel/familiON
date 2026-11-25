import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SwUpdate, UnrecoverableStateEvent, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppUpdateService, UPDATE_CHECK_INTERVAL_MS } from './app-update.service';
import { PWA_RELOAD_PAGE } from './pwa.tokens';

interface MockSwUpdate {
  isEnabled: boolean;
  versionUpdates: Subject<VersionEvent>;
  unrecoverable: Subject<UnrecoverableStateEvent>;
  checkForUpdate: ReturnType<typeof vi.fn>;
  activateUpdate: ReturnType<typeof vi.fn>;
}

describe('AppUpdateService', () => {
  let httpMock: HttpTestingController;
  let mockSwUpdate: MockSwUpdate;
  let reloadSpy: ReturnType<typeof vi.fn>;
  let visibilityState: DocumentVisibilityState;

  beforeEach(() => {
    vi.useFakeTimers();
    visibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilityState);

    reloadSpy = vi.fn();
    mockSwUpdate = {
      isEnabled: true,
      versionUpdates: new Subject<VersionEvent>(),
      unrecoverable: new Subject<UnrecoverableStateEvent>(),
      checkForUpdate: vi.fn().mockResolvedValue(false),
      activateUpdate: vi.fn().mockResolvedValue(true),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SwUpdate, useValue: mockSwUpdate },
        { provide: PWA_RELOAD_PAGE, useValue: reloadSpy },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('emits update banner on VERSION_READY and fetches latest version from BFF', async () => {
    const service = TestBed.inject(AppUpdateService);
    expect(service.showBanner()).toBe(false);

    mockSwUpdate.versionUpdates.next({
      type: 'VERSION_DETECTED',
      version: { hash: 'new-hash' },
    });
    expect(service.showBanner()).toBe(false);

    mockSwUpdate.versionUpdates.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'old-hash' },
      latestVersion: { hash: 'new-hash' },
    });

    expect(service.updateAvailable()).toBe(true);
    expect(service.showBanner()).toBe(true);

    const versionReq = httpMock.expectOne('/bff/api/system/version');
    versionReq.flush({
      version: '1.3.0',
      latestRelease: {
        version: '1.4.0',
        title: 'Notificaciones y PWA',
        releasedAt: '2026-11-25T10:00:00Z',
      },
    });
    await Promise.resolve();
    expect(service.availableVersion()).toBe('1.4.0');
  });

  it('activates update and reloads page on applyUpdate', async () => {
    const service = TestBed.inject(AppUpdateService);

    await service.applyUpdate();
    expect(mockSwUpdate.activateUpdate).toHaveBeenCalledTimes(1);
    expect(reloadSpy).toHaveBeenCalledTimes(1);
  });

  it('checks for updates every 6 hours and when returning to foreground', () => {
    TestBed.inject(AppUpdateService);
    expect(mockSwUpdate.checkForUpdate).not.toHaveBeenCalled();

    vi.advanceTimersByTime(UPDATE_CHECK_INTERVAL_MS);
    expect(mockSwUpdate.checkForUpdate).toHaveBeenCalledTimes(1);

    visibilityState = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(mockSwUpdate.checkForUpdate).toHaveBeenCalledTimes(1);

    visibilityState = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    expect(mockSwUpdate.checkForUpdate).toHaveBeenCalledTimes(2);
  });

  it('reloads page when SwUpdate emits unrecoverable', () => {
    TestBed.inject(AppUpdateService);
    mockSwUpdate.unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'hash mismatch' });
    expect(reloadSpy).toHaveBeenCalledTimes(1);
  });

  it('loads changelog entries from BFF', async () => {
    const service = TestBed.inject(AppUpdateService);

    const loadPromise = service.loadChangelog();
    const req = httpMock.expectOne('/bff/api/system/changelog');
    req.flush([
      {
        version: '1.4.0',
        title: 'Notificaciones',
        changelogMarkdown: '- **Centro de notificaciones**',
        isBreaking: false,
        releasedAt: '2026-11-25T10:00:00Z',
      },
    ]);

    const entries = await loadPromise;
    expect(entries.length).toBe(1);
    expect(service.changelog()[0].version).toBe('1.4.0');
  });
});
