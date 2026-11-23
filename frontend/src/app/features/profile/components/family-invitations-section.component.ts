import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FamilyVisibility, Role } from '../../../core/api/models/me.model';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import {
  CreateInvitationRequest,
  Invitation,
  InvitationCreated,
  ROLE_OPTIONS,
  VISIBILITY_OPTIONS,
  getRoleLabel,
  getVisibilityLabel,
} from '../data-access/family.models';

function formatShortDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

@Component({
  selector: 'fo-family-invitations-section',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, CardComponent, EmptyStateComponent, IconComponent],
  template: `
    <fo-card padding="md" data-testid="invitations-section">
      <div class="space-y-5">
        <div class="space-y-0.5">
          <h2 class="text-base font-semibold text-text">Invitaciones</h2>
          <p class="text-xs text-muted">
            Invita a otros integrantes de tu familia y comparte el enlace generado.
          </p>
        </div>

        <form
          [formGroup]="form"
          (ngSubmit)="onSubmit()"
          novalidate
          data-testid="invitation-form"
          class="space-y-4 rounded-xl border border-border bg-surface-2/50 p-4"
        >
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="space-y-1.5 sm:col-span-2">
              <label for="invite-email" class="block text-sm font-medium text-text">
                Correo electrónico
              </label>
              <input
                id="invite-email"
                type="email"
                formControlName="email"
                autocomplete="email"
                placeholder="integrante@correo.com"
                [attr.aria-invalid]="emailError() ? 'true' : null"
                [attr.aria-describedby]="emailError() ? 'invite-email-error' : null"
                class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors"
                [class.border-border]="!emailError()"
                [class.border-danger]="!!emailError()"
              />
              @if (emailError(); as error) {
                <p id="invite-email-error" role="alert" class="text-xs font-medium text-danger">
                  {{ error }}
                </p>
              }
            </div>

            <div class="space-y-1.5">
              <label for="invite-role" class="block text-sm font-medium text-text">Rol</label>
              <select
                id="invite-role"
                formControlName="role"
                (change)="onRoleChange()"
                class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
              >
                @for (option of roleOptions; track option.value) {
                  <option [value]="option.value">{{ option.label }}</option>
                }
              </select>
            </div>

            @if (isChildRole()) {
              <div class="space-y-1.5" data-testid="invite-visibility-field">
                <label for="invite-visibility" class="block text-sm font-medium text-text">
                  Visibilidad de la familia
                </label>
                <select
                  id="invite-visibility"
                  formControlName="familyVisibility"
                  class="fo-focus-ring block w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
                >
                  @for (option of visibilityOptions; track option.value) {
                    <option [value]="option.value">{{ option.label }}</option>
                  }
                </select>
              </div>
            }
          </div>

          <div class="flex justify-end">
            <fo-button type="submit" size="sm" [loading]="creating()" [disabled]="creating()">
              <fo-icon name="plus" [size]="16" />
              <span>Crear invitación</span>
            </fo-button>
          </div>
        </form>

        @if (lastCreated(); as created) {
          <div
            data-testid="created-invitation-banner"
            class="space-y-3 rounded-xl border border-accent/40 bg-accent-soft/60 p-4"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="space-y-0.5">
                <p class="text-sm font-semibold text-text">Enlace de invitación listo</p>
                <p class="text-xs text-muted">
                  Comparte este enlace con {{ created.email }} para que se sume a la familia.
                </p>
              </div>
              <button
                type="button"
                aria-label="Cerrar enlace"
                (click)="dismissCreated.emit()"
                class="fo-focus-ring inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface hover:text-text"
              >
                <fo-icon name="x" [size]="16" />
              </button>
            </div>

            <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
              <input
                type="text"
                readonly
                aria-label="Enlace de invitación"
                data-testid="created-invitation-url"
                [value]="created.inviteUrl"
                class="fo-focus-ring min-w-0 flex-1 rounded-xl border border-border bg-surface px-3 py-2 font-mono text-xs text-text"
              />
              <div class="flex shrink-0 items-center gap-2">
                <fo-button
                  size="sm"
                  data-testid="copy-invite-link-btn"
                  (click)="copyLink.emit(created.inviteUrl)"
                >
                  Copiar enlace
                </fo-button>
                @if (canShare()) {
                  <fo-button
                    size="sm"
                    variant="ghost"
                    data-testid="share-invite-link-btn"
                    (click)="shareLink.emit(created)"
                  >
                    Compartir
                  </fo-button>
                }
              </div>
            </div>
          </div>
        }

        <div class="space-y-3">
          <h3 class="text-sm font-semibold text-text">Invitaciones pendientes</h3>

          @if (invitations().length === 0) {
            <fo-empty-state
              icon="users"
              title="Sin invitaciones pendientes"
              description="Cuando crees una invitación, aparecerá aquí hasta que sea aceptada o revocada."
            />
          } @else {
            <ul class="divide-y divide-border" aria-label="Lista de invitaciones pendientes">
              @for (invitation of invitations(); track invitation.id) {
                <li
                  data-testid="invitation-row"
                  [attr.data-invitation-id]="invitation.id"
                  class="flex flex-col gap-3 py-3 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div class="min-w-0 space-y-1">
                    <div class="flex flex-wrap items-center gap-2">
                      <p class="truncate text-sm font-medium text-text">{{ invitation.email }}</p>
                      <span
                        class="inline-flex items-center rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-muted"
                      >
                        {{ roleLabel(invitation.role) }}
                      </span>
                      @if (invitation.role === 'HIJO') {
                        <span
                          class="inline-flex items-center rounded-full bg-accent-soft px-2 py-0.5 text-[11px] font-medium text-accent"
                        >
                          {{ visibilityLabel(invitation.familyVisibility) }}
                        </span>
                      }
                    </div>
                    <p class="text-xs text-muted">
                      Invitado por {{ invitation.invitedBy.fullName }} · Vence el
                      {{ formatDate(invitation.expiresAt) }}
                    </p>
                  </div>

                  <fo-button
                    size="sm"
                    variant="ghost"
                    data-testid="revoke-invitation-btn"
                    [loading]="revokingId() === invitation.id"
                    [disabled]="revokingId() === invitation.id"
                    (click)="revokeInvitation.emit(invitation.id)"
                  >
                    Revocar
                  </fo-button>
                </li>
              }
            </ul>
          }
        </div>
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyInvitationsSectionComponent {
  private readonly fb = inject(FormBuilder);

  readonly invitations = input.required<readonly Invitation[]>();
  readonly lastCreated = input<InvitationCreated | null>(null);
  readonly creating = input<boolean>(false);
  readonly revokingId = input<string | null>(null);
  readonly canShare = input<boolean>(false);

  readonly createInvitation = output<CreateInvitationRequest>();
  readonly revokeInvitation = output<string>();
  readonly copyLink = output<string>();
  readonly shareLink = output<InvitationCreated>();
  readonly dismissCreated = output<void>();

  protected readonly roleOptions = ROLE_OPTIONS;
  protected readonly visibilityOptions = VISIBILITY_OPTIONS;
  protected readonly submitted = signal(false);
  protected readonly selectedRole = signal<Role>('ADULTO');
  protected readonly isChildRole = computed(() => this.selectedRole() === 'HIJO');

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    role: ['ADULTO' as Role, [Validators.required]],
    familyVisibility: ['OWN_ONLY' as FamilyVisibility, [Validators.required]],
  });

  constructor() {
    effect(() => {
      if (this.lastCreated()) {
        this.submitted.set(false);
        this.form.controls.email.reset('');
      }
    });
  }

  protected emailError(): string | null {
    const ctrl = this.form.controls.email;
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    if (ctrl.hasError('required')) {
      return 'Ingresa un correo electrónico.';
    }
    if (ctrl.hasError('email')) {
      return 'Ingresa un correo electrónico válido.';
    }
    return null;
  }

  protected onRoleChange(): void {
    this.selectedRole.set(this.form.controls.role.value);
  }

  protected onSubmit(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.creating()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const payload: CreateInvitationRequest = {
      email: raw.email.trim().toLowerCase(),
      role: raw.role,
      ...(raw.role === 'HIJO' ? { familyVisibility: raw.familyVisibility } : {}),
    };
    this.createInvitation.emit(payload);
  }

  protected roleLabel(role: Role): string {
    return getRoleLabel(role);
  }

  protected visibilityLabel(visibility: FamilyVisibility): string {
    return getVisibilityLabel(visibility);
  }

  protected formatDate(iso: string): string {
    return formatShortDate(iso);
  }
}
