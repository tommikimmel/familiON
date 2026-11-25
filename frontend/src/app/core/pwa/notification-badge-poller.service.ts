import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, effect, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API } from '../api/api-config';
import { AuthStore } from '../auth/auth.store';
import { NotificationBadgeService } from '../layout/notification-badge.service';

export const BADGE_POLL_INTERVAL_MS = 60_000;

interface UnreadCountPayload {
  count: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationBadgePollerService {
  private readonly http = inject(HttpClient);
  private readonly authStore = inject(AuthStore);
  private readonly badge = inject(NotificationBadgeService);
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private pollTimerId: ReturnType<typeof setInterval> | null = null;
  private visibilityListenerAttached = false;
  private wasAuthenticated = false;
  private inFlight: Promise<number | null> | null = null;

  private readonly onVisibilityChange = (): void => {
    if (this.authStore.status() !== 'authenticated') {
      return;
    }
    if (this.isDocumentVisible()) {
      void this.refresh();
    }
  };

  constructor() {
    effect(() => {
      const authenticated = this.authStore.status() === 'authenticated';
      if (authenticated) {
        this.wasAuthenticated = true;
        this.startPolling();
      } else {
        this.stopPolling();
        if (this.wasAuthenticated) {
          this.wasAuthenticated = false;
          this.badge.set(0);
        }
      }
    });

    this.destroyRef.onDestroy(() => {
      this.stopPolling();
    });
  }

  refresh(): Promise<number | null> {
    if (this.authStore.status() !== 'authenticated') {
      return Promise.resolve(null);
    }

    if (this.inFlight) {
      return this.inFlight;
    }

    const request = this.fetchUnreadCount();
    this.inFlight = request;
    return request;
  }

  private async fetchUnreadCount(): Promise<number | null> {
    try {
      const response = await firstValueFrom(
        this.http.get<UnreadCountPayload>(`${API}/notifications/unread-count`),
      );
      const count = typeof response?.count === 'number' ? response.count : 0;
      if (this.authStore.status() === 'authenticated') {
        this.badge.set(count);
      }
      return count;
    } catch {
      return null;
    } finally {
      this.inFlight = null;
    }
  }

  private startPolling(): void {
    if (!this.visibilityListenerAttached) {
      this.doc.addEventListener('visibilitychange', this.onVisibilityChange);
      this.visibilityListenerAttached = true;
    }

    if (this.pollTimerId === null) {
      this.pollTimerId = setInterval(() => {
        if (this.isDocumentVisible()) {
          void this.refresh();
        }
      }, BADGE_POLL_INTERVAL_MS);
    }

    void this.refresh();
  }

  private stopPolling(): void {
    if (this.pollTimerId !== null) {
      clearInterval(this.pollTimerId);
      this.pollTimerId = null;
    }
    if (this.visibilityListenerAttached) {
      this.doc.removeEventListener('visibilitychange', this.onVisibilityChange);
      this.visibilityListenerAttached = false;
    }
  }

  private isDocumentVisible(): boolean {
    return this.doc.visibilityState !== 'hidden';
  }
}
