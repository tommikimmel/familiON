import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';
import {
  ButtonComponent,
  CardComponent,
  IconComponent,
  SkeletonComponent,
} from '../../../shared/ui';
import {
  formatNotificationTime,
  getNotificationCategoryMeta,
} from '../data-access/notifications.grouping';
import { NotificationGroup, NotificationItem } from '../data-access/notifications.models';

@Component({
  selector: 'fo-notification-list',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, SkeletonComponent],
  template: `
    <div data-testid="notifications-list" class="space-y-5">
      @for (group of groups(); track group.key) {
        <section [attr.aria-label]="group.label" [attr.data-testid]="'notif-group-' + group.key" class="space-y-2">
          <h2 class="px-1 text-xs font-semibold tracking-wide text-muted uppercase">
            {{ group.label }}
          </h2>

          <fo-card padding="none">
            <ul class="divide-y divide-border/70">
              @for (item of group.items; track item.id) {
                <li>
                  <button
                    type="button"
                    [attr.data-testid]="'notif-item-' + item.id"
                    (click)="selectNotification.emit(item)"
                    class="fo-focus-ring flex w-full cursor-pointer items-start gap-3 px-4 py-3.5 text-left transition-colors hover:bg-surface-2/60"
                    [class.bg-accent-soft/25]="item.readAt === null"
                  >
                    <span
                      class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
                      [class]="categoryMeta(item).toneClass"
                      aria-hidden="true"
                    >
                      <fo-icon [name]="categoryMeta(item).icon" [size]="18" />
                    </span>

                    <div class="min-w-0 flex-1">
                      <div class="flex items-start justify-between gap-2">
                        <p
                          class="text-sm text-text"
                          [class.font-semibold]="item.readAt === null"
                          [class.font-medium]="item.readAt !== null"
                        >
                          {{ item.title }}
                        </p>

                        <div class="flex shrink-0 items-center gap-2 pt-0.5">
                          <span class="text-[11px] text-muted tabular-nums">
                            {{ formatTime(item.createdAt) }}
                          </span>
                          @if (item.readAt === null) {
                            <span
                              data-testid="unread-dot"
                              aria-label="No leída"
                              class="inline-block h-2.5 w-2.5 shrink-0 rounded-full bg-accent"
                            ></span>
                          }
                        </div>
                      </div>

                      <p class="mt-0.5 text-xs leading-relaxed text-muted">
                        {{ item.body }}
                      </p>

                      <div class="mt-1.5 flex items-center gap-2 text-[11px] text-muted">
                        <span>{{ categoryMeta(item).label }}</span>
                        @if (item.metadata?.link) {
                          <span aria-hidden="true">·</span>
                          <span class="inline-flex items-center gap-0.5 font-medium text-accent">
                            Ver detalle
                            <fo-icon name="chevron-right" [size]="12" />
                          </span>
                        }
                      </div>
                    </div>
                  </button>
                </li>
              }
            </ul>
          </fo-card>
        </section>
      }

      @if (loadingMore()) {
        <div class="space-y-2 py-2" aria-busy="true">
          <fo-skeleton height="4rem" rounded="lg" />
          <fo-skeleton height="4rem" rounded="lg" />
        </div>
      }

      @if (loadMoreError(); as errMsg) {
        <div
          role="alert"
          class="flex items-center justify-between gap-3 rounded-xl border border-danger/30 bg-danger/10 px-4 py-3 text-xs text-danger"
        >
          <span>{{ errMsg }}</span>
          <fo-button size="sm" variant="ghost" (click)="loadMore.emit()">Reintentar</fo-button>
        </div>
      }

      @if (hasMore()) {
        <div #sentinel class="flex justify-center py-2">
          @if (!loadingMore()) {
            <fo-button
              data-testid="notifications-load-more-btn"
              size="sm"
              variant="ghost"
              (click)="loadMore.emit()"
            >
              Cargar más notificaciones
            </fo-button>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationListComponent {
  readonly groups = input.required<readonly NotificationGroup[]>();
  readonly hasMore = input<boolean>(false);
  readonly loadingMore = input<boolean>(false);
  readonly loadMoreError = input<string | null>(null);

  readonly selectNotification = output<NotificationItem>();
  readonly loadMore = output<void>();

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');

  constructor() {
    effect((onCleanup) => {
      const target = this.sentinel()?.nativeElement;
      if (!target || typeof IntersectionObserver === 'undefined') {
        return;
      }

      const observer = new IntersectionObserver(
        (entries) => {
          if (
            entries.some((entry) => entry.isIntersecting) &&
            this.hasMore() &&
            !this.loadingMore()
          ) {
            this.loadMore.emit();
          }
        },
        { rootMargin: '200px' },
      );

      observer.observe(target);
      onCleanup(() => observer.disconnect());
    });
  }

  protected categoryMeta(item: NotificationItem) {
    return getNotificationCategoryMeta(item.category);
  }

  protected formatTime(createdAt: string): string {
    return formatNotificationTime(createdAt);
  }
}
