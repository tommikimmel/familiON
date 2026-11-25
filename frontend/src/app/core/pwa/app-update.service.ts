import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  DestroyRef,
  Injectable,
  Signal,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter, firstValueFrom } from 'rxjs';
import { API } from '../api/api-config';
import { SystemChangelogEntry, SystemVersionResponse } from './pwa.models';
import { PWA_RELOAD_PAGE } from './pwa.tokens';

export const UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly swUpdate = inject(SwUpdate, { optional: true });
  private readonly http = inject(HttpClient);
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reloadPage = inject(PWA_RELOAD_PAGE);

  private readonly _updateAvailable = signal<boolean>(false);
  private readonly _dismissed = signal<boolean>(false);
  private readonly _availableVersion = signal<string | null>(null);
  private readonly _updating = signal<boolean>(false);
  private readonly _changelog = signal<readonly SystemChangelogEntry[]>([]);
  private readonly _changelogLoading = signal<boolean>(false);
  private readonly _changelogError = signal<string | null>(null);

  private checkTimerId: ReturnType<typeof setInterval> | null = null;
  private visibilityListenerAttached = false;

  readonly updateAvailable: Signal<boolean> = this._updateAvailable.asReadonly();
  readonly showBanner: Signal<boolean> = computed(
    () => this._updateAvailable() && !this._dismissed(),
  );
  readonly availableVersion: Signal<string | null> = this._availableVersion.asReadonly();
  readonly updating: Signal<boolean> = this._updating.asReadonly();
  readonly changelog: Signal<readonly SystemChangelogEntry[]> = this._changelog.asReadonly();
  readonly changelogLoading: Signal<boolean> = this._changelogLoading.asReadonly();
  readonly changelogError: Signal<string | null> = this._changelogError.asReadonly();

  private readonly onVisibilityChange = (): void => {
    if (this.doc.visibilityState === 'visible') {
      void this.checkForUpdate();
    }
  };

  constructor() {
    if (this.swUpdate?.isEnabled) {
      this.swUpdate.versionUpdates
        .pipe(
          filter((event): event is VersionReadyEvent => event.type === 'VERSION_READY'),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((event) => {
          this.handleVersionReady(event);
        });

      this.swUpdate.unrecoverable
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => {
          this.reloadPage();
        });

      this.startScheduledChecks();
    }

    this.destroyRef.onDestroy(() => {
      this.stopScheduledChecks();
    });
  }

  async checkForUpdate(): Promise<boolean> {
    if (!this.swUpdate?.isEnabled) {
      return false;
    }
    try {
      return await this.swUpdate.checkForUpdate();
    } catch {
      return false;
    }
  }

  async applyUpdate(): Promise<void> {
    if (this._updating()) {
      return;
    }
    this._updating.set(true);
    try {
      if (this.swUpdate?.isEnabled) {
        await this.swUpdate.activateUpdate();
      }
      this.reloadPage();
    } catch {
      this._updating.set(false);
    }
  }

  dismissBanner(): void {
    this._dismissed.set(true);
  }

  async loadChangelog(): Promise<readonly SystemChangelogEntry[]> {
    if (this._changelogLoading()) {
      return this._changelog();
    }

    this._changelogLoading.set(true);
    this._changelogError.set(null);

    try {
      const entries = await firstValueFrom(
        this.http.get<SystemChangelogEntry[]>(`${API}/system/changelog`),
      );
      const list = Array.isArray(entries) ? entries : [];
      this._changelog.set(list);
      return list;
    } catch {
      this._changelogError.set('No pudimos cargar las novedades de esta versión.');
      return [];
    } finally {
      this._changelogLoading.set(false);
    }
  }

  private handleVersionReady(event: VersionReadyEvent): void {
    this._updateAvailable.set(true);
    this._dismissed.set(false);

    const appDataVersion = this.extractAppDataVersion(event);
    if (appDataVersion && !this._availableVersion()) {
      this._availableVersion.set(appDataVersion);
    }

    void this.fetchLatestVersion();
  }

  private async fetchLatestVersion(): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.http.get<SystemVersionResponse>(`${API}/system/version`),
      );
      const version = response?.latestRelease?.version ?? response?.version ?? null;
      if (version && version.trim().length > 0) {
        this._availableVersion.set(version.trim());
      }
    } catch {
      // Keep fallback version if endpoint is temporarily unavailable.
    }
  }

  private extractAppDataVersion(event: VersionReadyEvent): string | null {
    const appData = event.latestVersion?.appData as Record<string, unknown> | undefined;
    const candidate = appData?.['version'];
    return typeof candidate === 'string' && candidate.trim().length > 0
      ? candidate.trim()
      : null;
  }

  private startScheduledChecks(): void {
    this.checkTimerId = setInterval(() => {
      void this.checkForUpdate();
    }, UPDATE_CHECK_INTERVAL_MS);

    if (!this.visibilityListenerAttached) {
      this.doc.addEventListener('visibilitychange', this.onVisibilityChange);
      this.visibilityListenerAttached = true;
    }
  }

  private stopScheduledChecks(): void {
    if (this.checkTimerId !== null) {
      clearInterval(this.checkTimerId);
      this.checkTimerId = null;
    }
    if (this.visibilityListenerAttached) {
      this.doc.removeEventListener('visibilitychange', this.onVisibilityChange);
      this.visibilityListenerAttached = false;
    }
  }
}
