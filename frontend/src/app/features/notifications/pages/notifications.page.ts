import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { NotificationListComponent } from '../components/notification-list.component';
import { sanitizeInternalLink } from '../data-access/notifications.grouping';
import { NotificationItem } from '../data-access/notifications.models';
import { NotificationsStore } from '../data-access/notifications.store';

@Component({
  selector: 'fo-notifications-page',
  standalone: true,
  providers: [NotificationsStore],
  imports: [
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    IconComponent,
    NotificationListComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-3xl space-y-5">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="text-xl font-semibold text-text">Notificaciones</h1>
          <p class="mt-0.5 text-sm text-muted">
            Avisos de gastos, vencimientos, vehículos y novedades de FamiliON.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <button
            type="button"
            role="switch"
            data-testid="unread-only-filter"
            [attr.aria-checked]="store.unreadOnly()"
            (click)="store.toggleUnreadOnly()"
            class="fo-focus-ring inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors"
            [class.border-accent]="store.unreadOnly()"
            [class.bg-accent-soft]="store.unreadOnly()"
            [class.text-accent]="store.unreadOnly()"
            [class.border-border]="!store.unreadOnly()"
            [class.bg-surface]="!store.unreadOnly()"
            [class.text-muted]="!store.unreadOnly()"
          >
            <span
              aria-hidden="true"
              class="inline-block h-2 w-2 rounded-full transition-colors"
              [class.bg-accent]="store.unreadOnly()"
              [class.bg-border]="!store.unreadOnly()"
            ></span>
            <span>Solo no leídas</span>
          </button>

          <fo-button
            data-testid="mark-all-read-btn"
            variant="ghost"
            size="sm"
            [disabled]="!store.hasUnread()"
            [loading]="store.markingAll()"
            (click)="onMarkAllAsRead()"
          >
            <fo-icon name="check" [size]="15" />
            <span>Marcar todas como leídas</span>
          </fo-button>
        </div>
      </header>

      @if (store.loading()) {
        <div data-testid="notifications-loading-skeleton" class="space-y-4" aria-busy="true">
          @for (group of [1, 2]; track group) {
            <div class="space-y-2">
              <fo-skeleton width="7rem" height="0.875rem" />
              <fo-card padding="md">
                <div class="space-y-3">
                  @for (row of [1, 2, 3]; track row) {
                    <fo-skeleton height="3.75rem" rounded="md" />
                  }
                </div>
              </fo-card>
            </div>
          }
        </div>
      } @else if (store.error(); as errorMessage) {
        <fo-card padding="lg" data-testid="notifications-error-state">
          <div class="flex flex-col items-center py-6 text-center">
            <div
              class="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-danger/12 text-danger"
            >
              <fo-icon name="alert-circle" [size]="24" />
            </div>
            <h2 class="text-base font-semibold text-text">No pudimos cargar las notificaciones</h2>
            <p class="mt-1 max-w-sm text-sm text-muted">{{ errorMessage }}</p>
            <div class="mt-4">
              <fo-button data-testid="notifications-retry-btn" (click)="store.reload()">
                Reintentar
              </fo-button>
            </div>
          </div>
        </fo-card>
      } @else if (store.items().length === 0) {
        @if (store.unreadOnly()) {
          <fo-empty-state
            data-testid="notifications-unread-empty"
            icon="check-circle"
            title="Estás al día"
            description="No tenés notificaciones sin leer en este momento."
          >
            <fo-button variant="ghost" (click)="store.setUnreadOnly(false)">
              Ver todas
            </fo-button>
          </fo-empty-state>
        } @else {
          <fo-empty-state
            data-testid="notifications-empty-state"
            icon="bell"
            title="Sin notificaciones"
            description="Cuando haya novedades importantes para tu familia aparecerán acá."
          />
        }
      } @else {
        <fo-notification-list
          [groups]="store.groups()"
          [hasMore]="store.hasMore()"
          [loadingMore]="store.loadingMore()"
          [loadMoreError]="store.loadMoreError()"
          (selectNotification)="onSelectNotification($event)"
          (loadMore)="store.loadMore()"
        />
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsPage {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  constructor() {
    this.store.load();
  }

  protected async onSelectNotification(item: NotificationItem): Promise<void> {
    if (item.readAt === null) {
      try {
        await this.store.markAsRead(item);
      } catch {
        this.toast.error('No pudimos marcar la notificación como leída.');
      }
    }

    const link = sanitizeInternalLink(item.metadata?.link);
    if (link) {
      await this.router.navigateByUrl(link);
    }
  }

  protected async onMarkAllAsRead(): Promise<void> {
    try {
      await this.store.markAllAsRead();
      this.toast.success('Todas las notificaciones marcadas como leídas');
    } catch {
      this.toast.error('No pudimos marcar todas las notificaciones como leídas.');
    }
  }
}
