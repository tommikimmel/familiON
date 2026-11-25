import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { NotificationBadgeService } from '../../../core/layout/notification-badge.service';
import {
  ButtonComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { sanitizeInternalLink } from '../data-access/notifications.grouping';
import { NotificationItem } from '../data-access/notifications.models';
import { NotificationsStore } from '../data-access/notifications.store';
import { NotificationListComponent } from './notification-list.component';

const PANEL_ID = 'fo-notifications-panel';

/** Header bell: opens the notifications/activity feed as a non-modal popover anchored to it. */
@Component({
  selector: 'fo-notifications-bell',
  standalone: true,
  providers: [NotificationsStore],
  imports: [
    ButtonComponent,
    CdkConnectedOverlay,
    CdkOverlayOrigin,
    EmptyStateComponent,
    IconComponent,
    NotificationListComponent,
    SkeletonComponent,
  ],
  template: `
    <button
      #trigger
      type="button"
      cdkOverlayOrigin
      #origin="cdkOverlayOrigin"
      data-testid="notifications-bell"
      [attr.aria-label]="label()"
      aria-haspopup="dialog"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="open() ? panelId : null"
      (click)="toggle()"
      class="fo-icon-btn fo-focus-ring relative"
    >
      <fo-icon name="bell" [size]="15" />
      @if (badge.unread() > 0) {
        <span data-testid="notification-badge" class="fo-notif-dot">
          <span class="sr-only">{{ badgeLabel() }}</span>
        </span>
      }
    </button>

    <ng-template
      cdkConnectedOverlay
      [cdkConnectedOverlayOrigin]="origin"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayPositions]="positions"
      [cdkConnectedOverlayViewportMargin]="8"
      [cdkConnectedOverlayPush]="true"
      (overlayOutsideClick)="onOutsideClick($event)"
      (overlayKeydown)="onOverlayKeydown($event)"
      (attach)="onAttach()"
      (detach)="open.set(false)"
    >
      <div
        #panel
        [id]="panelId"
        role="dialog"
        aria-labelledby="fo-notifications-panel-title"
        tabindex="-1"
        data-testid="notifications-panel"
        class="fo-notif-panel"
      >
        <header class="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
          <h2 id="fo-notifications-panel-title" class="text-sm font-semibold text-text">
            Notificaciones
          </h2>
          <fo-button
            data-testid="mark-all-read-btn"
            variant="ghost"
            size="sm"
            [disabled]="!store.hasUnread()"
            [loading]="store.markingAll()"
            (click)="onMarkAllAsRead()"
          >
            <fo-icon name="check" [size]="14" />
            <span>Marcar todas como leídas</span>
          </fo-button>
        </header>

        <div class="fo-notif-scroll px-3 py-3">
          @if (store.loading()) {
            <div data-testid="notifications-loading-skeleton" class="space-y-2" aria-busy="true">
              @for (row of [1, 2, 3, 4]; track row) {
                <fo-skeleton height="3.75rem" rounded="md" />
              }
            </div>
          } @else if (store.error(); as errorMessage) {
            <div data-testid="notifications-error-state" role="alert" class="py-6 text-center">
              <p class="text-sm text-muted">{{ errorMessage }}</p>
              <div class="mt-3">
                <fo-button size="sm" (click)="store.reload()">Reintentar</fo-button>
              </div>
            </div>
          } @else if (store.items().length === 0) {
            <fo-empty-state
              data-testid="notifications-empty-state"
              icon="bell"
              title="Sin notificaciones"
              description="Acá vas a ver todos los cambios que hagan en tu familia."
            />
          } @else {
            <fo-notification-list
              [groups]="store.groups()"
              [hasMore]="store.hasMore()"
              [loadingMore]="store.loadingMore()"
              [loadMoreError]="store.loadMoreError()"
              (selectNotification)="onSelect($event)"
              (loadMore)="store.loadMore()"
            />
          }
        </div>
      </div>
    </ng-template>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .fo-notif-dot {
      position: absolute;
      top: 6px;
      right: 7px;
      width: 6px;
      height: 6px;
      border-radius: 999px;
      background: var(--fo-accent);
    }

    .fo-notif-panel {
      display: flex;
      flex-direction: column;
      width: min(400px, calc(100vw - 16px));
      max-height: min(560px, calc(100dvh - 88px));
      background: var(--fo-surface);
      border: 1px solid var(--fo-border);
      border-radius: 14px;
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.18);
      outline: none;
      overflow: hidden;
      animation: fo-notif-in 0.2s var(--fo-ease-spring);
      transform-origin: top right;
    }

    .fo-notif-scroll {
      overflow-y: auto;
      overscroll-behavior: contain;
    }

    @keyframes fo-notif-in {
      from {
        opacity: 0;
        transform: translateY(-6px) scale(0.97);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .fo-notif-panel {
        animation: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsBellComponent {
  protected readonly store = inject(NotificationsStore);
  protected readonly badge = inject(NotificationBadgeService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');

  protected readonly panelId = PANEL_ID;
  protected readonly open = signal(false);

  protected readonly positions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
  ];

  protected readonly badgeLabel = computed(() => {
    const count = this.badge.unread();
    return count > 99 ? '99+' : String(count);
  });

  protected readonly label = computed(() =>
    this.badge.unread() > 0
      ? `Notificaciones (${this.badgeLabel()} sin leer)`
      : 'Notificaciones',
  );

  protected toggle(): void {
    if (this.open()) {
      this.close(true);
      return;
    }
    this.open.set(true);
    this.store.load();
  }

  protected onAttach(): void {
    queueMicrotask(() => this.panel()?.nativeElement.focus());
  }

  protected onOutsideClick(event: MouseEvent): void {
    // The trigger handles its own clicks (toggle); ignoring it here avoids close-then-reopen.
    if (!this.trigger().nativeElement.contains(event.target as Node)) {
      this.close(false);
    }
  }

  protected onOverlayKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close(true);
    }
  }

  protected async onSelect(item: NotificationItem): Promise<void> {
    if (item.readAt === null) {
      try {
        await this.store.markAsRead(item);
      } catch {
        this.toast.error('No pudimos marcar la notificación como leída.');
      }
    }
    const link = sanitizeInternalLink(item.metadata?.link);
    if (link) {
      this.close(false);
      await this.router.navigateByUrl(link);
    }
  }

  protected async onMarkAllAsRead(): Promise<void> {
    try {
      await this.store.markAllAsRead();
    } catch {
      this.toast.error('No pudimos marcar todas las notificaciones como leídas.');
    }
  }

  private close(restoreFocus: boolean): void {
    this.open.set(false);
    if (restoreFocus) {
      this.trigger().nativeElement.focus();
    }
  }
}
