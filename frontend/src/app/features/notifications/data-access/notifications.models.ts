export type NotificationCategory =
  | 'APP_UPDATE'
  | 'EXPENSE_ATTRIBUTED'
  | 'ALLOWANCE'
  | 'FIXED_EXPENSE_DUE'
  | 'VEHICLE_ALERT'
  | 'SYSTEM';

export interface NotificationMetadata {
  link?: string;
  [key: string]: unknown;
}

export interface NotificationItem {
  id: string;
  category: NotificationCategory;
  title: string;
  body: string;
  metadata: NotificationMetadata | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface NotificationPageResponse {
  content: NotificationItem[];
  page: NotificationPageMetadata;
}

export interface UnreadCountResponse {
  count: number;
}

export interface PushConfigResponse {
  publicKey: string | null;
}

export interface PushSubscriptionPayload {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
  userAgent: string;
}

export interface PushUnsubscribePayload {
  endpoint: string;
}

export type NotificationGroupKey = 'TODAY' | 'THIS_WEEK' | 'EARLIER';

export interface NotificationGroup {
  key: NotificationGroupKey;
  label: string;
  items: readonly NotificationItem[];
}
