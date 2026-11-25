import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthStatus } from '../auth/auth.models';
import { AuthStore } from '../auth/auth.store';
import { NotificationBadgeService } from '../layout/notification-badge.service';
import {
  BADGE_POLL_INTERVAL_MS,
  NotificationBadgePollerService,
} from './notification-badge-poller.service';

describe('NotificationBadgePollerService', () => {
  let httpMock: HttpTestingController;
  let badge: NotificationBadgeService;
  let authStatus: ReturnType<typeof signal<AuthStatus>>;
  let visibilityState: DocumentVisibilityState;

  beforeEach(() => {
    vi.useFakeTimers();
    visibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visibilityState);

    authStatus = signal<AuthStatus>('anonymous');
    const mockAuthStore: Partial<AuthStore> = {
      status: authStatus.asReadonly(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: mockAuthStore },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    badge = TestBed.inject(NotificationBadgeService);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('fetches unread-count when authenticated and polls every 60s only while visible', async () => {
    TestBed.inject(NotificationBadgePollerService);
    TestBed.tick();

    // Not authenticated yet -> no request
    httpMock.expectNone('/bff/api/notifications/unread-count');

    // Login -> immediate fetch
    authStatus.set('authenticated');
    TestBed.tick();

    const firstReq = httpMock.expectOne('/bff/api/notifications/unread-count');
    firstReq.flush({ count: 4 });
    await Promise.resolve();
    await Promise.resolve();
    expect(badge.unread()).toBe(4);

    // After 60s while visible -> polls again
    vi.advanceTimersByTime(BADGE_POLL_INTERVAL_MS);
    const secondReq = httpMock.expectOne('/bff/api/notifications/unread-count');
    secondReq.flush({ count: 6 });
    await Promise.resolve();
    await Promise.resolve();
    expect(badge.unread()).toBe(6);

    // While hidden -> skips interval poll
    visibilityState = 'hidden';
    document.dispatchEvent(new Event('visibilitychange'));
    vi.advanceTimersByTime(BADGE_POLL_INTERVAL_MS);
    httpMock.expectNone('/bff/api/notifications/unread-count');

    // Returning to visible -> refreshes immediately
    visibilityState = 'visible';
    document.dispatchEvent(new Event('visibilitychange'));
    const resumeReq = httpMock.expectOne('/bff/api/notifications/unread-count');
    resumeReq.flush({ count: 2 });
    await Promise.resolve();
    await Promise.resolve();
    expect(badge.unread()).toBe(2);

    // Logout -> stops polling and resets badge to 0
    authStatus.set('anonymous');
    TestBed.tick();
    expect(badge.unread()).toBe(0);

    vi.advanceTimersByTime(BADGE_POLL_INTERVAL_MS * 2);
    httpMock.expectNone('/bff/api/notifications/unread-count');
  });

  it('exposes manual refresh for post-read synchronization', async () => {
    authStatus.set('authenticated');
    const poller = TestBed.inject(NotificationBadgePollerService);
    TestBed.tick();

    httpMock.expectOne('/bff/api/notifications/unread-count').flush({ count: 3 });
    await Promise.resolve();
    await Promise.resolve();
    expect(badge.unread()).toBe(3);

    const refreshPromise = poller.refresh();
    httpMock.expectOne('/bff/api/notifications/unread-count').flush({ count: 1 });
    const count = await refreshPromise;
    expect(count).toBe(1);
    expect(badge.unread()).toBe(1);
  });
});
