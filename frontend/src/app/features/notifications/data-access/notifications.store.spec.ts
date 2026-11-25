import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import { NotificationBadgePollerService } from '../../../core/pwa/notification-badge-poller.service';
import { NotificationItem, NotificationPageResponse } from './notifications.models';
import { NOTIFICATIONS_PAGE_SIZE, NotificationsStore } from './notifications.store';

function makeNotification(id: string, overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id,
    category: 'EXPENSE_ATTRIBUTED',
    title: `Notificación ${id}`,
    body: `Cuerpo ${id}`,
    metadata: { link: `/transactions/${id}` },
    readAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

function makePage(
  content: NotificationItem[],
  number: number,
  totalPages: number,
  totalElements = content.length,
): NotificationPageResponse {
  return {
    content,
    page: {
      size: NOTIFICATIONS_PAGE_SIZE,
      number,
      totalElements,
      totalPages,
    },
  };
}

describe('NotificationsStore', () => {
  let store: NotificationsStore;
  let httpMock: HttpTestingController;
  let badge: NotificationBadgeService;
  let refreshSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    refreshSpy = vi.fn().mockResolvedValue(0);

    TestBed.configureTestingModule({
      providers: [
        NotificationsStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: NotificationBadgePollerService,
          useValue: { refresh: refreshSpy },
        },
      ],
    });

    store = TestBed.inject(NotificationsStore);
    httpMock = TestBed.inject(HttpTestingController);
    badge = TestBed.inject(NotificationBadgeService);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('loads first page and paginates with loadMore without duplicating items', () => {
    store.load();
    expect(store.loading()).toBe(true);

    const page0Req = httpMock.expectOne(
      (req) =>
        req.url === '/bff/api/notifications' &&
        req.params.get('page') === '0' &&
        req.params.get('size') === String(NOTIFICATIONS_PAGE_SIZE) &&
        !req.params.has('unreadOnly'),
    );
    page0Req.flush(makePage([makeNotification('1'), makeNotification('2')], 0, 2, 3));

    expect(store.loading()).toBe(false);
    expect(store.items().map((i) => i.id)).toEqual(['1', '2']);
    expect(store.hasMore()).toBe(true);

    store.loadMore();
    expect(store.loadingMore()).toBe(true);

    const page1Req = httpMock.expectOne(
      (req) =>
        req.url === '/bff/api/notifications' &&
        req.params.get('page') === '1' &&
        req.params.get('size') === String(NOTIFICATIONS_PAGE_SIZE),
    );
    // Include duplicate id '2' to verify deduplication
    page1Req.flush(makePage([makeNotification('2'), makeNotification('3')], 1, 2, 3));

    expect(store.loadingMore()).toBe(false);
    expect(store.items().map((i) => i.id)).toEqual(['1', '2', '3']);
    expect(store.hasMore()).toBe(false);
  });

  it('marks a notification as read, updates item state and decrements badge', async () => {
    badge.set(3);
    const n1 = makeNotification('n-1');
    const n2 = makeNotification('n-2');

    store.load();
    httpMock
      .expectOne((req) => req.url === '/bff/api/notifications')
      .flush(makePage([n1, n2], 0, 1, 2));

    const markPromise = store.markAsRead(n1);
    const patchReq = httpMock.expectOne('/bff/api/notifications/n-1/read');
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush({ ...n1, readAt: '2026-11-25T12:00:00Z' });

    const updated = await markPromise;
    expect(updated.readAt).toBe('2026-11-25T12:00:00Z');
    expect(store.items()[0].readAt).toBe('2026-11-25T12:00:00Z');
    expect(badge.unread()).toBe(2);
    expect(refreshSpy).toHaveBeenCalledTimes(1);
  });

  it('removes read item from list when unreadOnly filter is active', async () => {
    badge.set(2);
    const n1 = makeNotification('n-1');
    const n2 = makeNotification('n-2');

    store.setUnreadOnly(true);
    const listReq = httpMock.expectOne(
      (req) =>
        req.url === '/bff/api/notifications' && req.params.get('unreadOnly') === 'true',
    );
    listReq.flush(makePage([n1, n2], 0, 1, 2));

    const markPromise = store.markAsRead(n1);
    httpMock
      .expectOne('/bff/api/notifications/n-1/read')
      .flush({ ...n1, readAt: '2026-11-25T12:00:00Z' });
    await markPromise;

    expect(store.items().map((i) => i.id)).toEqual(['n-2']);
    expect(badge.unread()).toBe(1);
  });

  it('marks all notifications as read and sets badge to 0', async () => {
    badge.set(5);
    const n1 = makeNotification('n-1');
    const n2 = makeNotification('n-2');

    store.load();
    httpMock
      .expectOne((req) => req.url === '/bff/api/notifications')
      .flush(makePage([n1, n2], 0, 1, 2));

    const readAllPromise = store.markAllAsRead();
    const req = httpMock.expectOne('/bff/api/notifications/read-all');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await readAllPromise;

    expect(store.items().every((i) => i.readAt !== null)).toBe(true);
    expect(badge.unread()).toBe(0);
  });
});
