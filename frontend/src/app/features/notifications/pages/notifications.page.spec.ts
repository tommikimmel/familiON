import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { BehaviorSubject, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import { NotificationBadgePollerService } from '../../../core/pwa/notification-badge-poller.service';
import { NotificationItem } from '../data-access/notifications.models';
import { NotificationsPage } from './notifications.page';

@Component({ standalone: true, template: '<p>Target</p>' })
class DummyTargetComponent {}

function makeNotification(id: string, overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id,
    category: 'VEHICLE_ALERT',
    title: `Alerta ${id}`,
    body: `Detalle ${id}`,
    metadata: { link: '/vehicles/1' },
    readAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('NotificationsPage', () => {
  let httpMock: HttpTestingController;
  let router: Router;
  let badge: NotificationBadgeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotificationsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'vehicles/1', component: DummyTargetComponent }]),
        {
          provide: NotificationBadgePollerService,
          useValue: { refresh: vi.fn().mockResolvedValue(0) },
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    badge = TestBed.inject(NotificationBadgeService);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  it('renders grouped notifications, marks read and navigates on click', async () => {
    badge.set(1);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();

    const listReq = httpMock.expectOne((req) => req.url === '/bff/api/notifications');
    listReq.flush({
      content: [
        makeNotification('n-today'),
        makeNotification('n-old', {
          readAt: '2026-10-01T10:00:00Z',
          createdAt: '2026-10-01T09:00:00Z',
        }),
      ],
      page: { size: 20, number: 0, totalElements: 2, totalPages: 1 },
    });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('h1')?.textContent?.trim()).toBe('Notificaciones');
    expect(host.querySelector('[data-testid="notif-group-TODAY"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="notif-group-EARLIER"]')).not.toBeNull();
    expect(host.querySelectorAll('[data-testid="unread-dot"]').length).toBe(1);

    const itemBtn = host.querySelector('[data-testid="notif-item-n-today"]') as HTMLButtonElement;
    itemBtn.click();

    const patchReq = httpMock.expectOne('/bff/api/notifications/n-today/read');
    patchReq.flush({
      ...makeNotification('n-today'),
      readAt: new Date().toISOString(),
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith('/vehicles/1');
    expect(badge.unread()).toBe(0);
    expect(host.querySelectorAll('[data-testid="unread-dot"]').length).toBe(0);
  });

  it('toggles unread-only filter and marks all as read', async () => {
    badge.set(2);
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();

    httpMock
      .expectOne((req) => req.url === '/bff/api/notifications')
      .flush({
        content: [makeNotification('n-1')],
        page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
      });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const filterBtn = host.querySelector('[data-testid="unread-only-filter"]') as HTMLButtonElement;
    filterBtn.click();
    fixture.detectChanges();

    const filteredReq = httpMock.expectOne(
      (req) =>
        req.url === '/bff/api/notifications' && req.params.get('unreadOnly') === 'true',
    );
    filteredReq.flush({
      content: [makeNotification('n-1')],
      page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
    });
    fixture.detectChanges();
    expect(filterBtn.getAttribute('aria-checked')).toBe('true');

    const markAllBtn = host.querySelector(
      '[data-testid="mark-all-read-btn"] button',
    ) as HTMLButtonElement;
    markAllBtn.click();

    const readAllReq = httpMock.expectOne('/bff/api/notifications/read-all');
    expect(readAllReq.request.method).toBe('POST');
    readAllReq.flush(null, { status: 204, statusText: 'No Content' });

    await fixture.whenStable();
    fixture.detectChanges();

    expect(badge.unread()).toBe(0);
    expect(host.querySelector('[data-testid="notifications-unread-empty"]')).not.toBeNull();
  });

  it('hides push opt-in card when SwPush is not available and shows it when enabled with publicKey', async () => {
    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.detectChanges();

    httpMock
      .expectOne((req) => req.url === '/bff/api/notifications')
      .flush({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="push-opt-in-card"]'),
    ).toBeNull();

    // Re-configure with enabled SwPush
    httpMock.verify();
    TestBed.resetTestingModule();

    const mockSwPush = {
      isEnabled: true,
      subscription: new BehaviorSubject<PushSubscription | null>(null),
      notificationClicks: new Subject(),
      requestSubscription: vi.fn(),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
    };

    TestBed.configureTestingModule({
      imports: [NotificationsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SwPush, useValue: mockSwPush },
        {
          provide: NotificationBadgePollerService,
          useValue: { refresh: vi.fn().mockResolvedValue(0) },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);

    const enabledFixture = TestBed.createComponent(NotificationsPage);
    enabledFixture.detectChanges();

    httpMock
      .expectOne((req) => req.url === '/bff/api/notifications')
      .flush({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
    httpMock
      .expectOne('/bff/api/notifications/push-config')
      .flush({ publicKey: 'VAPID-PUBLIC-KEY' });

    await enabledFixture.whenStable();
    enabledFixture.detectChanges();

    const card = (enabledFixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="push-opt-in-card"]',
    );
    expect(card).not.toBeNull();
    expect(card?.textContent).toContain('Avisos en este dispositivo');
  });
});
