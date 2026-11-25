import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { Role } from '../../../core/api/models/me.model';
import { readableTextOn } from '../../../shared/utils/color';

@Component({
  selector: 'fo-profile-header',
  standalone: true,
  template: `
    <div class="flex items-center gap-4">
      <div
        aria-hidden="true"
        data-testid="profile-avatar"
        class="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-full text-xl font-bold shadow-xs transition-colors duration-150 sm:h-16 sm:w-16"
        [style.background-color]="accentColor()"
        [style.color]="avatarTextColor()"
      >
        @if (showPhoto()) {
          <img
            data-testid="profile-avatar-photo"
            [src]="photoUrl()"
            alt=""
            class="h-full w-full object-cover"
            (error)="failedUrl.set(photoUrl())"
          />
        } @else {
          {{ initial() }}
        }
      </div>

      <div class="min-w-0 flex-1 space-y-1">
        <div class="flex flex-wrap items-center gap-2">
          <h2 class="truncate text-lg font-semibold text-text">{{ fullName() }}</h2>
          <span
            data-testid="profile-role-badge"
            class="inline-flex items-center rounded-full bg-accent-soft px-2.5 py-0.5 text-xs font-medium text-accent"
          >
            {{ roleLabel() }}
          </span>
        </div>

        <p class="truncate text-sm text-muted">{{ email() }}</p>
        <p class="truncate text-xs font-medium text-muted">{{ familyName() }}</p>
      </div>
    </div>
    <div class="mt-4 border-t border-border pt-4">
      <ng-content />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileHeaderComponent {
  readonly fullName = input.required<string>();
  readonly email = input.required<string>();
  readonly role = input.required<Role>();
  readonly familyName = input.required<string>();
  readonly accentColor = input.required<string>();
  readonly photoUrl = input<string | null>(null);

  protected readonly failedUrl = signal<string | null>(null);
  protected readonly showPhoto = computed(
    () => !!this.photoUrl() && this.photoUrl() !== this.failedUrl(),
  );

  protected readonly initial = computed(() => {
    const trimmed = this.fullName().trim();
    return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?';
  });

  protected readonly roleLabel = computed(() => (this.role() === 'ADULTO' ? 'Adulto' : 'Hijo/a'));

  protected readonly avatarTextColor = computed(() => readableTextOn(this.accentColor()));
}
