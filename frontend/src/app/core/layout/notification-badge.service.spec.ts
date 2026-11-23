import { TestBed } from '@angular/core/testing';
import { NotificationBadgeService } from './notification-badge.service';

describe('NotificationBadgeService', () => {
  it('starts at 0 and clamps unread counts to non-negative integers', () => {
    const badge = TestBed.inject(NotificationBadgeService);
    expect(badge.unread()).toBe(0);

    badge.set(5);
    expect(badge.unread()).toBe(5);

    badge.set(-3);
    expect(badge.unread()).toBe(0);

    badge.set(4.9);
    expect(badge.unread()).toBe(4);
  });
});
