import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FamilyVisibility, Role } from '../../../core/api/models/me.model';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { readableTextOn } from '../../../shared/utils/color';
import {
  Member,
  VISIBILITY_OPTIONS,
  getRoleLabel,
  getVisibilityLabel,
} from '../data-access/family.models';
import { normalizeHexColor } from '../data-access/profile.models';

@Component({
  selector: 'fo-family-members-list',
  standalone: true,
  imports: [CardComponent, EmptyStateComponent],
  template: `
    <fo-card padding="md">
      <div class="space-y-4">
        <div class="space-y-0.5">
          <h2 class="text-base font-semibold text-text">Miembros</h2>
          <p class="text-xs text-muted">
            Personas que integran la familia y su nivel de visibilidad sobre las finanzas.
          </p>
        </div>

        @if (members().length === 0) {
          <fo-empty-state
            icon="users"
            title="Sin miembros"
            description="Todavía no hay miembros cargados en esta familia."
          />
        } @else {
          <ul class="divide-y divide-border" aria-label="Miembros de la familia">
            @for (member of members(); track member.id) {
              @let avatarBg = normalizeColor(member.accentColor);
              <li
                data-testid="family-member-row"
                [attr.data-member-id]="member.id"
                class="flex flex-col gap-3 py-3.5 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
              >
                <div class="flex min-w-0 items-center gap-3">
                  <span
                    aria-hidden="true"
                    class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold shadow-2xs"
                    [style.background-color]="avatarBg"
                    [style.color]="contrastFor(avatarBg)"
                  >
                    {{ initialFor(member.fullName) }}
                  </span>

                  <div class="min-w-0">
                    <div class="flex flex-wrap items-center gap-2">
                      <p class="truncate text-sm font-semibold text-text">{{ member.fullName }}</p>
                      <span
                        class="inline-flex items-center rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-muted"
                      >
                        {{ roleLabel(member.role) }}
                      </span>
                      @if (member.id === currentUserId()) {
                        <span
                          class="inline-flex items-center rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent"
                        >
                          Vos
                        </span>
                      }
                    </div>
                    <p class="truncate text-xs text-muted">{{ member.email }}</p>
                  </div>
                </div>

                @if (member.role === 'HIJO') {
                  @if (editable()) {
                    <div class="flex items-center gap-2 sm:shrink-0">
                      <label
                        [for]="'member-visibility-' + member.id"
                        class="text-xs font-medium text-muted"
                      >
                        Visibilidad
                      </label>
                      <select
                        [id]="'member-visibility-' + member.id"
                        data-testid="member-visibility-select"
                        [attr.data-member-id]="member.id"
                        [value]="member.familyVisibility"
                        [disabled]="updatingUserId() === member.id"
                        (change)="onVisibilityChange(member.id, $event)"
                        class="fo-focus-ring rounded-xl border border-border bg-surface px-3 py-1.5 text-xs font-medium text-text transition-colors disabled:opacity-60"
                      >
                        @for (option of visibilityOptions; track option.value) {
                          <option
                            [value]="option.value"
                            [selected]="option.value === member.familyVisibility"
                          >
                            {{ option.label }}
                          </option>
                        }
                      </select>
                    </div>
                  } @else {
                    <span
                      data-testid="member-visibility-readonly"
                      class="inline-flex w-fit items-center rounded-full border border-border bg-surface-2 px-2.5 py-1 text-xs font-medium text-muted"
                    >
                      {{ visibilityLabel(member.familyVisibility) }}
                    </span>
                  }
                }
              </li>
            }
          </ul>
        }
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyMembersListComponent {
  readonly members = input.required<readonly Member[]>();
  readonly editable = input<boolean>(false);
  readonly currentUserId = input<string | null>(null);
  readonly updatingUserId = input<string | null>(null);

  readonly visibilityChange = output<{ userId: string; familyVisibility: FamilyVisibility }>();

  protected readonly visibilityOptions = VISIBILITY_OPTIONS;

  protected normalizeColor(hex: string): string {
    return normalizeHexColor(hex);
  }

  protected contrastFor(hex: string): string {
    return readableTextOn(hex);
  }

  protected initialFor(name: string): string {
    const trimmed = name.trim();
    return trimmed.length > 0 ? trimmed.charAt(0).toUpperCase() : '?';
  }

  protected roleLabel(role: Role): string {
    return getRoleLabel(role);
  }

  protected visibilityLabel(visibility: FamilyVisibility): string {
    return getVisibilityLabel(visibility);
  }

  protected onVisibilityChange(userId: string, event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (!select) {
      return;
    }
    const nextVisibility = select.value as FamilyVisibility;
    this.visibilityChange.emit({ userId, familyVisibility: nextVisibility });
  }
}
