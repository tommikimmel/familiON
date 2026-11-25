import { HttpErrorResponse } from '@angular/common/http';
import {
  DestroyRef,
  Injectable,
  Signal,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Subscription, firstValueFrom } from 'rxjs';
import { ProblemDetail } from '../../../core/api/problem-detail';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import { NotificationBadgePollerService } from '../../../core/pwa/notification-badge-poller.service';
import { NotificationsApi } from './notifications.api';
import { groupNotifications } from './notifications.grouping';
import { NotificationGroup, NotificationItem } from './notifications.models';

export const NOTIFICATIONS_PAGE_SIZE = 20;

const DEFAULT_LOAD_ERROR = 'No pudimos cargar las notificaciones. Intentá nuevamente.';
const DEFAULT_LOAD_MORE_ERROR = 'No pudimos cargar más notificaciones.';

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ProblemDetail> | null | undefined;
    if (body && typeof body.detail === 'string' && body.detail.trim().length > 0) {
      return body.detail.trim();
    }
    if (body && typeof body.title === 'string' && body.title.trim().length > 0) {
      return body.title.trim();
    }
  }
  return fallback;
}

@Injectable()
export class NotificationsStore {
  private readonly api = inject(NotificationsApi);
  private readonly badge = inject(NotificationBadgeService);
  private readonly badgePoller = inject(NotificationBadgePollerService, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  private readonly _items = signal<readonly NotificationItem[]>([]);
  private readonly _unreadOnly = signal<boolean>(false);
  private readonly _page = signal<number>(0);
  private readonly _totalElements = signal<number>(0);
  private readonly _totalPages = signal<number>(0);
  private readonly _loading = signal<boolean>(false);
  private readonly _loadingMore = signal<boolean>(false);
  private readonly _markingAll = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);
  private readonly _loadMoreError = signal<string | null>(null);

  private listSub: Subscription | null = null;
  private loadMoreSub: Subscription | null = null;
  private generation = 0;

  readonly items: Signal<readonly NotificationItem[]> = this._items.asReadonly();
  readonly unreadOnly: Signal<boolean> = this._unreadOnly.asReadonly();
  readonly page: Signal<number> = this._page.asReadonly();
  readonly totalElements: Signal<number> = this._totalElements.asReadonly();
  readonly totalPages: Signal<number> = this._totalPages.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly loadingMore: Signal<boolean> = this._loadingMore.asReadonly();
  readonly markingAll: Signal<boolean> = this._markingAll.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();
  readonly loadMoreError: Signal<string | null> = this._loadMoreError.asReadonly();

  readonly groups: Signal<readonly NotificationGroup[]> = computed(() =>
    groupNotifications(this._items()),
  );

  readonly hasMore: Signal<boolean> = computed(
    () => this._totalPages() > 0 && this._page() + 1 < this._totalPages(),
  );

  readonly hasUnread: Signal<boolean> = computed(
    () => this.badge.unread() > 0 || this._items().some((item) => item.readAt === null),
  );

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.listSub?.unsubscribe();
      this.loadMoreSub?.unsubscribe();
    });
  }

  load(): void {
    this.listSub?.unsubscribe();
    this.loadMoreSub?.unsubscribe();
    this.loadMoreSub = null;

    const currentGeneration = ++this.generation;
    this._loading.set(true);
    this._loadingMore.set(false);
    this._error.set(null);
    this._loadMoreError.set(null);

    this.listSub = this.api
      .list({
        unreadOnly: this._unreadOnly(),
        page: 0,
        size: NOTIFICATIONS_PAGE_SIZE,
      })
      .subscribe({
        next: (response) => {
          if (currentGeneration !== this.generation) {
            return;
          }
          this._items.set(response.content);
          this._page.set(response.page.number);
          this._totalElements.set(response.page.totalElements);
          this._totalPages.set(response.page.totalPages);
          this._loading.set(false);
        },
        error: (err: unknown) => {
          if (currentGeneration !== this.generation) {
            return;
          }
          this._items.set([]);
          this._page.set(0);
          this._totalElements.set(0);
          this._totalPages.set(0);
          this._error.set(extractErrorMessage(err, DEFAULT_LOAD_ERROR));
          this._loading.set(false);
        },
      });
  }

  reload(): void {
    this.load();
  }

  setUnreadOnly(unreadOnly: boolean): void {
    if (this._unreadOnly() === unreadOnly) {
      return;
    }
    this._unreadOnly.set(unreadOnly);
    this.load();
  }

  toggleUnreadOnly(): void {
    this.setUnreadOnly(!this._unreadOnly());
  }

  loadMore(): void {
    if (this._loading() || this._loadingMore() || !this.hasMore()) {
      return;
    }

    const currentGeneration = this.generation;
    const nextPage = this._page() + 1;
    this._loadingMore.set(true);
    this._loadMoreError.set(null);

    this.loadMoreSub?.unsubscribe();
    this.loadMoreSub = this.api
      .list({
        unreadOnly: this._unreadOnly(),
        page: nextPage,
        size: NOTIFICATIONS_PAGE_SIZE,
      })
      .subscribe({
        next: (response) => {
          if (currentGeneration !== this.generation) {
            return;
          }
          const existingIds = new Set(this._items().map((item) => item.id));
          const merged = [
            ...this._items(),
            ...response.content.filter((item) => !existingIds.has(item.id)),
          ];
          this._items.set(merged);
          this._page.set(response.page.number);
          this._totalElements.set(response.page.totalElements);
          this._totalPages.set(response.page.totalPages);
          this._loadingMore.set(false);
        },
        error: (err: unknown) => {
          if (currentGeneration !== this.generation) {
            return;
          }
          this._loadMoreError.set(extractErrorMessage(err, DEFAULT_LOAD_MORE_ERROR));
          this._loadingMore.set(false);
        },
      });
  }

  async markAsRead(item: NotificationItem): Promise<NotificationItem> {
    if (item.readAt !== null) {
      return item;
    }

    const updated = await firstValueFrom(this.api.markAsRead(item.id));
    const resolvedItem: NotificationItem = {
      ...item,
      ...updated,
      readAt: updated.readAt ?? new Date().toISOString(),
    };

    if (this._unreadOnly()) {
      this._items.update((list) => list.filter((entry) => entry.id !== item.id));
      this._totalElements.update((total) => Math.max(0, total - 1));
    } else {
      this._items.update((list) =>
        list.map((entry) => (entry.id === item.id ? resolvedItem : entry)),
      );
    }

    const currentUnread = this.badge.unread();
    if (currentUnread > 0) {
      this.badge.set(currentUnread - 1);
    }
    void this.badgePoller?.refresh();

    return resolvedItem;
  }

  async markAllAsRead(): Promise<void> {
    if (this._markingAll()) {
      return;
    }
    this._markingAll.set(true);
    try {
      await firstValueFrom(this.api.markAllAsRead());
      const nowIso = new Date().toISOString();
      if (this._unreadOnly()) {
        this._items.set([]);
        this._page.set(0);
        this._totalElements.set(0);
        this._totalPages.set(0);
      } else {
        this._items.update((list) =>
          list.map((item) => (item.readAt === null ? { ...item, readAt: nowIso } : item)),
        );
      }
      this.badge.set(0);
    } finally {
      this._markingAll.set(false);
    }
  }
}
