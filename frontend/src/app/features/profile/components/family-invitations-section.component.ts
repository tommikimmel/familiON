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

const FIELD_CLASS =
  'fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors';

/** Pending invitations + invite action; rendered inside the unified "Miembros" card. */
@Component({
  selector: 'fo-family-invitations-section',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent],
  template: `
    <div data-testid="invitations-section" class="space-y-4">
      @if (invitations().length > 0) {
        <div class="space-y-3 border-t border-border pt-5">
          <h3 class="text-sm font-semibold text-text">
            Esperando respuesta ({{ invitations().length }})
          </h3>
          <ul class="divide-y divide-border" aria-label="Invitaciones pendientes">
            @for (invitation of invitations(); track invitation.id) {
              @let busy = revokingId() === invitation.id || resendingId() === invitation.id;
              <li
                data-testid="invitation-row"
                [attr.data-invitation-id]="invitation.id"
                class="space-y-3 py-4 first:pt-0 last:pb-0"
              >
                <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                  <div class="flex min-w-0 items-center gap-3">
                    <span
                      aria-hidden="true"
                      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-dashed border-border text-muted"
                    >
                      <fo-icon name="user" [size]="18" />
                    </span>
                    <div class="min-w-0">
                      <div class="flex flex-wrap items-center gap-2">
                        <p class="truncate text-sm font-semibold text-text">
                          {{ invitation.email }}
                        </p>
                        <span
                          class="inline-flex items-center rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-muted"
                        >
                          {{ roleLabel(invitation.role) }}
                        </span>
                        <span
                          class="inline-flex items-center rounded-full bg-warning/10 px-2 py-0.5 text-[11px] font-medium text-warning"
                        >
                          Pendiente
                        </span>
                      </div>
                      <p class="text-xs text-muted">
                        Vence el {{ formatDate(invitation.expiresAt) }}
                        @if (invitation.role === 'HIJO') {
                          · Puede ver: {{ visibilityLabel(invitation.familyVisibility) }}
                        }
                      </p>
                    </div>
                  </div>

                  @if (confirmingRevokeId() !== invitation.id) {
                    <div class="flex shrink-0 gap-2">
                      <fo-button
                        size="sm"
                        variant="soft"
                        data-testid="resend-invitation-btn"
                        [loading]="resendingId() === invitation.id"
                        [disabled]="busy"
                        (click)="resendInvitation.emit(invitation)"
                      >
                        Reenviar
                      </fo-button>
                      <fo-button
                        size="sm"
                        variant="ghost"
                        data-testid="revoke-invitation-btn"
                        [disabled]="busy"
                        (click)="confirmingRevokeId.set(invitation.id)"
                      >
                        Cancelar
                      </fo-button>
                    </div>
                  }
                </div>

                @if (confirmingRevokeId() === invitation.id) {
                  <div
                    role="alertdialog"
                    [attr.aria-labelledby]="'revoke-confirm-' + invitation.id"
                    data-testid="revoke-confirm"
                    class="flex flex-col gap-3 rounded-xl border border-danger/30 bg-danger/10 p-3.5 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <p [id]="'revoke-confirm-' + invitation.id" class="text-sm text-text">
                      ¿Cancelar la invitación? El enlace dejará de funcionar.
                    </p>
                    <div class="flex shrink-0 justify-end gap-2">
                      <fo-button
                        size="sm"
                        variant="ghost"
                        [disabled]="busy"
                        (click)="confirmingRevokeId.set(null)"
                      >
                        No
                      </fo-button>
                      <fo-button
                        size="sm"
                        variant="danger"
                        data-testid="confirm-revoke-invitation-btn"
                        [loading]="revokingId() === invitation.id"
                        (click)="onConfirmRevoke(invitation.id)"
                      >
                        Sí, cancelar
                      </fo-button>
                    </div>
                  </div>
                }
              </li>
            }
          </ul>
        </div>
      }

      @if (lastCreated(); as created) {
        <div
          data-testid="created-invitation-banner"
          class="space-y-3 rounded-xl border border-accent/40 bg-accent-soft/60 p-4"
        >
          <div class="flex items-start justify-between gap-2">
            <div class="space-y-0.5">
              <p class="text-sm font-semibold text-text">Invitación enviada</p>
              <p class="text-xs text-muted">
                Le mandamos un correo a {{ created.email }}. También podés pasarle este enlace.
              </p>
            </div>
            <button
              type="button"
              aria-label="Cerrar"
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

      @if (showForm()) {
        <form
          [formGroup]="form"
          (ngSubmit)="onSubmit()"
          novalidate
          data-testid="invitation-form"
          class="space-y-4 rounded-xl border border-border bg-surface-2/50 p-4"
        >
          <p class="text-sm font-semibold text-text">Invitar a alguien</p>
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
                placeholder="nombre@correo.com"
                [attr.aria-invalid]="emailError() ? 'true' : null"
                [attr.aria-describedby]="emailError() ? 'invite-email-error' : null"
                [class]="fieldClass"
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
                [class]="fieldClass + ' border-border'"
              >
                @for (option of roleOptions; track option.value) {
                  <option [value]="option.value">{{ option.label }}</option>
                }
              </select>
            </div>

            @if (isChildRole()) {
              <div class="space-y-1.5" data-testid="invite-visibility-field">
                <label for="invite-visibility" class="block text-sm font-medium text-text">
                  Qué puede ver
                </label>
                <select
                  id="invite-visibility"
                  formControlName="familyVisibility"
                  [class]="fieldClass + ' border-border'"
                >
                  @for (option of visibilityOptions; track option.value) {
                    <option [value]="option.value">{{ option.label }}</option>
                  }
                </select>
              </div>
            }
          </div>

          <div class="flex flex-wrap justify-end gap-2">
            <fo-button
              size="sm"
              variant="ghost"
              data-testid="cancel-invite-form-btn"
              [disabled]="creating()"
              (click)="closeForm()"
            >
              Cancelar
            </fo-button>
            <fo-button type="submit" size="sm" [loading]="creating()" [disabled]="creating()">
              Enviar invitación
            </fo-button>
          </div>
        </form>
      } @else {
        <fo-button
          variant="soft"
          data-testid="open-invite-form-btn"
          (click)="showForm.set(true)"
        >
          <fo-icon name="plus" [size]="16" />
          <span>Invitar a alguien</span>
        </fo-button>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FamilyInvitationsSectionComponent {
  private readonly fb = inject(FormBuilder);

  readonly invitations = input.required<readonly Invitation[]>();
  readonly lastCreated = input<InvitationCreated | null>(null);
  readonly creating = input<boolean>(false);
  readonly revokingId = input<string | null>(null);
  readonly resendingId = input<string | null>(null);
  readonly canShare = input<boolean>(false);

  readonly createInvitation = output<CreateInvitationRequest>();
  readonly revokeInvitation = output<string>();
  readonly resendInvitation = output<Invitation>();
  readonly copyLink = output<string>();
  readonly shareLink = output<InvitationCreated>();
  readonly dismissCreated = output<void>();

  protected readonly fieldClass = FIELD_CLASS;
  protected readonly roleOptions = ROLE_OPTIONS;
  protected readonly visibilityOptions = VISIBILITY_OPTIONS;
  protected readonly submitted = signal(false);
  protected readonly showForm = signal(false);
  protected readonly confirmingRevokeId = signal<string | null>(null);
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
        this.closeForm();
      }
    });
  }

  protected closeForm(): void {
    this.submitted.set(false);
    this.form.controls.email.reset('');
    this.showForm.set(false);
  }

  protected emailError(): string | null {
    const ctrl = this.form.controls.email;
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    if (ctrl.hasError('required')) {
      return 'Ingresá un correo electrónico.';
    }
    if (ctrl.hasError('email')) {
      return 'Ingresá un correo electrónico válido.';
    }
    return null;
  }

  protected onRoleChange(): void {
    this.selectedRole.set(this.form.controls.role.value);
  }

  protected onConfirmRevoke(id: string): void {
    this.revokeInvitation.emit(id);
    this.confirmingRevokeId.set(null);
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
