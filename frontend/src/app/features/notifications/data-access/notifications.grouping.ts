import { FoIconName } from '../../../shared/ui';
import {
  NotificationCategory,
  NotificationGroup,
  NotificationGroupKey,
  NotificationItem,
} from './notifications.models';

export interface NotificationCategoryMeta {
  icon: FoIconName;
  label: string;
  toneClass: string;
}

const CATEGORY_META: Record<NotificationCategory, NotificationCategoryMeta> = {
  APP_UPDATE: {
    icon: 'sparkles',
    label: 'Actualización',
    toneClass: 'bg-accent-soft text-accent',
  },
  EXPENSE_ATTRIBUTED: {
    icon: 'users',
    label: 'Gasto compartido',
    toneClass: 'bg-accent-soft text-accent',
  },
  ALLOWANCE: {
    icon: 'piggy-bank',
    label: 'Mensualidad',
    toneClass: 'bg-success/12 text-success',
  },
  FIXED_EXPENSE_DUE: {
    icon: 'calendar-range',
    label: 'Gasto fijo',
    toneClass: 'bg-warning/15 text-warning',
  },
  VEHICLE_ALERT: {
    icon: 'car',
    label: 'Vehículo',
    toneClass: 'bg-danger/12 text-danger',
  },
  SYSTEM: {
    icon: 'bell',
    label: 'Sistema',
    toneClass: 'bg-surface-2 text-muted',
  },
  ACTIVITY: {
    icon: 'pencil',
    label: 'Actividad',
    toneClass: 'bg-surface-2 text-text',
  },
};

const GROUP_LABELS: Record<NotificationGroupKey, string> = {
  TODAY: 'Hoy',
  THIS_WEEK: 'Esta semana',
  EARLIER: 'Anteriores',
};

const GROUP_ORDER: readonly NotificationGroupKey[] = ['TODAY', 'THIS_WEEK', 'EARLIER'];

const MS_PER_DAY = 24 * 60 * 60 * 1000;

export function getNotificationCategoryMeta(category: NotificationCategory): NotificationCategoryMeta {
  return CATEGORY_META[category] ?? CATEGORY_META.SYSTEM;
}

function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function classifyNotificationGroup(
  createdAtIso: string,
  referenceNow: Date = new Date(),
): NotificationGroupKey {
  const created = new Date(createdAtIso);
  if (Number.isNaN(created.getTime())) {
    return 'EARLIER';
  }

  const todayStart = startOfLocalDay(referenceNow).getTime();
  const createdDayStart = startOfLocalDay(created).getTime();
  const diffDays = Math.round((todayStart - createdDayStart) / MS_PER_DAY);

  if (diffDays <= 0) {
    return 'TODAY';
  }
  if (diffDays < 7) {
    return 'THIS_WEEK';
  }
  return 'EARLIER';
}

export function groupNotifications(
  items: readonly NotificationItem[],
  referenceNow: Date = new Date(),
): NotificationGroup[] {
  const buckets: Record<NotificationGroupKey, NotificationItem[]> = {
    TODAY: [],
    THIS_WEEK: [],
    EARLIER: [],
  };

  for (const item of items) {
    const key = classifyNotificationGroup(item.createdAt, referenceNow);
    buckets[key].push(item);
  }

  return GROUP_ORDER.filter((key) => buckets[key].length > 0).map((key) => ({
    key,
    label: GROUP_LABELS[key],
    items: buckets[key],
  }));
}

export function formatNotificationTime(
  createdAtIso: string,
  referenceNow: Date = new Date(),
): string {
  const created = new Date(createdAtIso);
  if (Number.isNaN(created.getTime())) {
    return '';
  }

  const diffMs = referenceNow.getTime() - created.getTime();
  const diffMinutes = Math.floor(diffMs / 60_000);

  if (diffMinutes < 1 && diffMs >= 0) {
    return 'Ahora';
  }
  if (diffMinutes >= 1 && diffMinutes < 60) {
    return `Hace ${diffMinutes} min`;
  }

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours >= 1 && diffHours < 24 && classifyNotificationGroup(createdAtIso, referenceNow) === 'TODAY') {
    return `Hace ${diffHours} h`;
  }

  const todayStart = startOfLocalDay(referenceNow).getTime();
  const createdDayStart = startOfLocalDay(created).getTime();
  const diffDays = Math.round((todayStart - createdDayStart) / MS_PER_DAY);

  const timeFormatter = new Intl.DateTimeFormat('es-AR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

  if (diffDays === 1) {
    return `Ayer, ${timeFormatter.format(created)}`;
  }

  const dateFormatter = new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: 'short',
    ...(created.getFullYear() !== referenceNow.getFullYear() ? { year: 'numeric' } : {}),
  });

  return `${dateFormatter.format(created)} · ${timeFormatter.format(created)}`;
}

export function sanitizeInternalLink(link: unknown): string | null {
  if (typeof link !== 'string') {
    return null;
  }
  const trimmed = link.trim();
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
    return null;
  }
  return trimmed;
}
