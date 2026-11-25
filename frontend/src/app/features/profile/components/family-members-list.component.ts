import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FamilyVisibility, Role } from '../../../core/api/models/me.model';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { readableTextOn } from '../../../shared/utils/color';
import {
  Member,
  VISIBILITY_OPTIONS,
  getRoleLabel,
  getVisibilityLabel,
} from '../data-access/family.models';
import { normalizeHexColor } from '../data-access/profile.models';

interface PendingVisibility {
  userId: string;
  familyVisibility: FamilyVisibility;
}

/**
 * Unified "Miembros" card: members list plus projected content
 * (pending invitations + invite action, adults only).
 */
@Component({
  selector: 'fo-family-members-list',
  standalone: true,
  imports: [ButtonComponent, CardComponent, EmptyStateComponent, IconComponent],
  template: `
    <fo-card padding="lg">
      <div class="space-y-5" data-testid="members-section">
        <div class="flex items-start justify-between gap-3">
          <div class="space-y-1">
            <h2 class="text-base font-semibold text-text">Miembros</h2>
            <p class="text-sm text-muted">Quiénes forman tu familia y qué pueden ver.</p>
          </div>
          @if (canEditVisibility()) {
            @if (editing()) {
              <fo-button
                size="sm"
                variant="ghost"
                data-testid="done-members-btn"
                (click)="stopEdit()"
              >
                Listo
              </fo-button>
            } @else {
              <fo-button
                size="sm"
                variant="soft"
                data-testid="edit-members-btn"
                (click)="editing.set(true)"
              >
                <fo-icon name="pencil" [size]="14" />
                <span>Editar</span>
              </fo-button>
            }
          }
        </div>

        @if (members().length === 0) {
          <fo-empty-state
            icon="users"
            title="Sin miembros"
            description="Todavía no hay miembros en esta familia."
          />
        } @else {
          <ul class="divide-y divide-border" aria-label="Miembros de la familia">
            @for (member of members(); track member.id) {
              @let avatarBg = normalizeColor(member.accentColor);
              @let pending = pendingFor(member.id);
              <li
                data-testid="family-member-row"
                [attr.data-member-id]="member.id"
                class="space-y-3 py-4 first:pt-0 last:pb-0"
              >
                <div
                  class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
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
                        <p class="truncate text-sm font-semibold text-text">
                          {{ member.fullName }}
                        </p>
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
                    @if (editing()) {
                      <div class="flex items-center gap-2 sm:shrink-0">
                        <label
                          [for]="'member-visibility-' + member.id"
                          class="text-xs font-medium text-muted"
                        >
                          Puede ver
                        </label>
                        <select
                          [id]="'member-visibility-' + member.id"
                          data-testid="member-visibility-select"
                          [attr.data-member-id]="member.id"
                          [disabled]="updatingUserId() === member.id"
                          (change)="onVisibilitySelect(member, $event)"
                          class="fo-focus-ring rounded-xl border border-border bg-surface px-3 py-1.5 text-xs font-medium text-text transition-colors disabled:opacity-60"
                        >
                          @for (option of visibilityOptions; track option.value) {
                            <option
                              [value]="option.value"
                              [selected]="
                                option.value === (pending?.familyVisibility ?? member.familyVisibility)
                              "
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
                        Puede ver: {{ visibilityLabel(member.familyVisibility) }}
                      </span>
                    }
                  }
                </div>

                @if (pending) {
                  <div
                    role="alertdialog"
                    [attr.aria-labelledby]="'visibility-confirm-' + member.id"
                    data-testid="visibility-confirm"
                    class="flex flex-col gap-3 rounded-xl border border-warning/40 bg-warning/10 p-3.5 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <p [id]="'visibility-confirm-' + member.id" class="text-sm text-text">
                      ¿Cambiar lo que ve {{ member.fullName }} a
                      <strong class="font-semibold">{{ visibilityLabel(pending.familyVisibility) }}</strong>?
                    </p>
                    <div class="flex shrink-0 justify-end gap-2">
                      <fo-button size="sm" variant="ghost" (click)="pendingVisibility.set(null)">
                        Cancelar
                      </fo-button>
                      <fo-button
                        size="sm"
                        data-testid="confirm-visibility-btn"
                        (click)="confirmVisibility()"
                      >
                        Confirmar
                      </fo-button>
                    </div>
                  </div>
                }
              </li>
            }
          </ul>
        }

        <ng-content />
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
  protected readonly editing = signal(false);
  protected readonly pendingVisibility = signal<PendingVisibility | null>(null);
  /** Only children have a configurable visibility; nothing to edit otherwise. */
  protected readonly canEditVisibility = computed(
    () => this.editable() && this.members().some((m) => m.role === 'HIJO'),
  );

  protected pendingFor(userId: string): PendingVisibility | null {
    const pending = this.pendingVisibility();
    return pending?.userId === userId ? pending : null;
  }

  protected stopEdit(): void {
    this.pendingVisibility.set(null);
    this.editing.set(false);
  }

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

  protected onVisibilitySelect(member: Member, event: Event): void {
    const select = event.target as HTMLSelectElement | null;
    if (!select) {
      return;
    }
    const next = select.value as FamilyVisibility;
    this.pendingVisibility.set(
      next === member.familyVisibility ? null : { userId: member.id, familyVisibility: next },
    );
  }

  protected confirmVisibility(): void {
    const pending = this.pendingVisibility();
    if (pending) {
      this.visibilityChange.emit(pending);
      this.pendingVisibility.set(null);
    }
  }
}
