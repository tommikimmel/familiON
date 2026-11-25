import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import { NotificationBadgePollerService } from '../../../core/pwa/notification-badge-poller.service';
import { NotificationItem } from '../data-access/notifications.models';
import { NotificationsBellComponent } from './notifications-bell.component';

function makeItem(id: string, overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id,
    category: 'ACTIVITY',
    title: `Ana creó un movimiento ${id}`,
    body: 'Supermercado',
    metadata: { link: '/transactions' },
    readAt: null,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

const panel = () => document.querySelector('[data-testid="notifications-panel"]') as HTMLElement | null;

describe('NotificationsBellComponent', () => {
  let httpMock: HttpTestingController;
  let badge: NotificationBadgeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotificationsBellComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: NotificationBadgePollerService, useValue: { refresh: vi.fn().mockResolvedValue(0) } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    badge = TestBed.inject(NotificationBadgeService);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
  });

  function openPanel() {
    const fixture = TestBed.createComponent(NotificationsBellComponent);
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="notifications-bell"]',
    ) as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/bff/api/notifications').flush({
      content: [makeItem('a'), makeItem('b', { readAt: '2026-10-01T10:00:00Z' })],
      page: { size: 20, number: 0, totalElements: 2, totalPages: 1 },
    });
    fixture.detectChanges();
    return { fixture, trigger };
  }

  it('opens an anchored dialog instead of navigating, with unread state', () => {
    badge.set(3);
    const { trigger } = openPanel();

    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(trigger.getAttribute('aria-haspopup')).toBe('dialog');
    expect(trigger.getAttribute('aria-label')).toBe('Notificaciones (3 sin leer)');
    expect(panel()?.getAttribute('role')).toBe('dialog');
    expect(panel()?.querySelectorAll('[data-testid="unread-dot"]').length).toBe(1);
  });

  it('closes on Escape and restores focus to the bell', () => {
    const { fixture, trigger } = openPanel();

    panel()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(panel()).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(trigger);
  });

  it('closes on outside click', () => {
    const { fixture } = openPanel();

    document.body.click();
    fixture.detectChanges();

    expect(panel()).toBeNull();
  });

  it('marks a notification read, navigates to its link and closes', async () => {
    badge.set(1);
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture } = openPanel();

    (panel()!.querySelector('[data-testid="notif-item-a"]') as HTMLButtonElement).click();
    httpMock.expectOne('/bff/api/notifications/a/read').flush(makeItem('a', { readAt: new Date().toISOString() }));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(badge.unread()).toBe(0);
    expect(navigateSpy).toHaveBeenCalledWith('/transactions');
    expect(panel()).toBeNull();
  });

  it('marks all as read', async () => {
    badge.set(1);
    const { fixture } = openPanel();

    (panel()!.querySelector('[data-testid="mark-all-read-btn"] button') as HTMLButtonElement).click();
    httpMock.expectOne('/bff/api/notifications/read-all').flush(null);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(badge.unread()).toBe(0);
    expect(panel()?.querySelectorAll('[data-testid="unread-dot"]').length).toBe(0);
  });
});
