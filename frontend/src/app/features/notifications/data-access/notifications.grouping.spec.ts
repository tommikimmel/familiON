import { describe, expect, it } from 'vitest';
import {
  classifyNotificationGroup,
  getNotificationCategoryMeta,
  groupNotifications,
  sanitizeInternalLink,
} from './notifications.grouping';
import { NotificationItem } from './notifications.models';

function buildNotification(overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id: 'n-1',
    category: 'SYSTEM',
    title: 'Aviso',
    body: 'Detalle',
    metadata: null,
    readAt: null,
    createdAt: '2026-11-25T10:00:00.000Z',
    ...overrides,
  };
}

describe('notifications.grouping', () => {
  const referenceNow = new Date(2026, 10, 25, 15, 0, 0);

  it('classifies items into Hoy, Esta semana and Anteriores', () => {
    const today = new Date(2026, 10, 25, 9, 30, 0).toISOString();
    const threeDaysAgo = new Date(2026, 10, 22, 12, 0, 0).toISOString();
    const tenDaysAgo = new Date(2026, 10, 15, 12, 0, 0).toISOString();

    expect(classifyNotificationGroup(today, referenceNow)).toBe('TODAY');
    expect(classifyNotificationGroup(threeDaysAgo, referenceNow)).toBe('THIS_WEEK');
    expect(classifyNotificationGroup(tenDaysAgo, referenceNow)).toBe('EARLIER');

    const grouped = groupNotifications(
      [
        buildNotification({ id: '1', createdAt: today }),
        buildNotification({ id: '2', createdAt: threeDaysAgo }),
        buildNotification({ id: '3', createdAt: tenDaysAgo }),
      ],
      referenceNow,
    );

    expect(grouped.map((g) => g.label)).toEqual(['Hoy', 'Esta semana', 'Anteriores']);
    expect(grouped[0].items.map((i) => i.id)).toEqual(['1']);
    expect(grouped[1].items.map((i) => i.id)).toEqual(['2']);
    expect(grouped[2].items.map((i) => i.id)).toEqual(['3']);
  });

  it('maps each notification category to a valid icon and label', () => {
    expect(getNotificationCategoryMeta('APP_UPDATE').icon).toBe('sparkles');
    expect(getNotificationCategoryMeta('EXPENSE_ATTRIBUTED').icon).toBe('users');
    expect(getNotificationCategoryMeta('ALLOWANCE').icon).toBe('piggy-bank');
    expect(getNotificationCategoryMeta('FIXED_EXPENSE_DUE').icon).toBe('calendar-range');
    expect(getNotificationCategoryMeta('VEHICLE_ALERT').icon).toBe('car');
    expect(getNotificationCategoryMeta('SYSTEM').icon).toBe('bell');
  });

  it('accepts only relative internal links', () => {
    expect(sanitizeInternalLink('/transactions?id=12')).toBe('/transactions?id=12');
    expect(sanitizeInternalLink('  /vehicles/42 ')).toBe('/vehicles/42');
    expect(sanitizeInternalLink('//evil.example/path')).toBeNull();
    expect(sanitizeInternalLink('https://evil.example')).toBeNull();
    expect(sanitizeInternalLink('javascript:alert(1)')).toBeNull();
    expect(sanitizeInternalLink(null)).toBeNull();
  });
});
