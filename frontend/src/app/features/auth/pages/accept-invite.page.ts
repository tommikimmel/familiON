import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { parseAuthError } from '../../../core/auth/auth-error';
import { InvitationDetails } from '../../../core/auth/auth.models';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { AuthBannerComponent } from '../components/auth-banner.component';
import { PasswordInputComponent } from '../components/password-input.component';
import {
  describePasswordError,
  passwordsMatchValidator,
  strongPasswordValidator,
} from '../utils/password-validators';

type InviteLoadState = 'loading' | 'ready' | 'invalid';

@Component({
  selector: 'fo-accept-invite-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    EmptyStateComponent,
    SkeletonComponent,
    AuthBannerComponent,
    PasswordInputComponent,
  ],
  template: `
    <section class="space-y-5">
      <header class="space-y-1 text-center">
        <h1 class="text-xl font-semibold text-text">Aceptar invitación</h1>
      </header>

      @switch (loadState()) {
        @case ('loading') {
          <div class="space-y-3" aria-busy="true" aria-label="Cargando invitación">
            <fo-skeleton height="3rem" rounded="lg" />
            <fo-skeleton height="2.75rem" rounded="md" />
            <fo-skeleton height="2.75rem" rounded="md" />
            <fo-skeleton height="2.75rem" rounded="md" />
          </div>
        }

        @case ('invalid') {
          <fo-empty-state
            icon="alert-circle"
            title="Invitación no válida"
            description="Este enlace de invitación expiró o ya fue utilizado."
          >
            <a
              routerLink="/auth/login"
              class="fo-focus-ring inline-flex items-center justify-center rounded-xl bg-accent px-4 py-2 text-sm font-medium text-accent-contrast transition-opacity hover:opacity-92"
            >
              Ir a iniciar sesión
            </a>
          </fo-empty-state>
        }

        @case ('ready') {
          @if (invitation(); as inv) {
            <div class="rounded-xl border border-border bg-surface-2/70 px-3.5 py-3 text-center">
              <p class="text-sm font-medium text-text">
                Te invitaron a la familia {{ inv.familyName }} como {{ roleLabel() }}
              </p>
              <p class="mt-1 text-xs text-muted">{{ inv.email }}</p>
            </div>
          }

          @if (generalError(); as message) {
            <fo-auth-banner kind="error" [message]="message" />
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="space-y-4">
            <div class="space-y-1.5">
              <label for="invite-full-name" class="block text-sm font-medium text-text">
                Nombre completo
              </label>
              <input
                id="invite-full-name"
                type="text"
                formControlName="fullName"
                autocomplete="name"
                placeholder="Tu nombre completo"
                [attr.aria-invalid]="fullNameError() ? 'true' : null"
                [attr.aria-describedby]="fullNameError() ? 'invite-full-name-error' : null"
                class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
                [class.border-border]="!fullNameError()"
                [class.border-danger]="!!fullNameError()"
              />
              @if (fullNameError(); as err) {
                <p id="invite-full-name-error" role="alert" class="text-xs font-medium text-danger">
                  {{ err }}
                </p>
              }
            </div>

            <fo-password-input
              inputId="invite-password"
              label="Contraseña"
              formControlName="password"
              autocomplete="new-password"
              placeholder="Mínimo 10 caracteres"
              hint="Usa al menos 10 caracteres, con una letra y un número."
              [error]="passwordError()"
            />

            <fo-password-input
              inputId="invite-confirm-password"
              label="Confirmar contraseña"
              formControlName="confirmPassword"
              autocomplete="new-password"
              placeholder="Repite tu contraseña"
              [error]="confirmPasswordError()"
            />

            <fo-button
              type="submit"
              [fullWidth]="true"
              [loading]="submitting()"
              [disabled]="submitting()"
            >
              Unirme a la familia
            </fo-button>
          </form>

          <p class="text-center text-sm text-muted">
            <a
              routerLink="/auth/login"
              class="fo-focus-ring rounded-md font-medium text-accent hover:underline"
            >
              Ir a iniciar sesión
            </a>
          </p>
        }
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcceptInvitePage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);

  private readonly token = signal<string>(
    this.route.snapshot.queryParamMap.get('token')?.trim() ?? '',
  );

  protected readonly loadState = signal<InviteLoadState>('loading');
  protected readonly invitation = signal<InvitationDetails | null>(null);
  protected readonly roleLabel = computed(() =>
    this.invitation()?.role === 'ADULTO' ? 'Adulto' : 'Hijo/a',
  );

  protected readonly form = this.fb.nonNullable.group(
    {
      fullName: ['', [Validators.required]],
      password: ['', [Validators.required, strongPasswordValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator('password', 'confirmPassword') },
  );

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  ngOnInit(): void {
    void this.loadInvitation();
  }

  protected fullNameError(): string | null {
    const server = this.serverFieldErrors()['fullName'];
    if (server) {
      return server;
    }
    const ctrl = this.form.controls.fullName;
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    if (ctrl.hasError('required')) {
      return 'Ingresa tu nombre completo.';
    }
    return null;
  }

  protected passwordError(): string | null {
    const server = this.serverFieldErrors()['password'];
    if (server) {
      return server;
    }
    const ctrl = this.form.controls.password;
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    return describePasswordError(ctrl.errors);
  }

  protected confirmPasswordError(): string | null {
    const ctrl = this.form.controls.confirmPassword;
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    if (ctrl.hasError('required')) {
      return 'Confirma tu contraseña.';
    }
    if (this.form.hasError('passwordMismatch')) {
      return 'Las contraseñas no coinciden.';
    }
    return null;
  }

  protected async onSubmit(): Promise<void> {
    this.submitted.set(true);
    this.generalError.set(null);
    this.serverFieldErrors.set({});

    if (!this.token() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { fullName, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.form.disable({ emitEvent: false });
    try {
      await this.authStore.acceptInvite(this.token(), fullName.trim(), password);
      await this.router.navigate(['/home']);
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo aceptar la invitación. Intenta de nuevo.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      this.generalError.set(parsed.generalMessage ?? parsed.fieldErrors['token'] ?? null);
    } finally {
      this.form.enable({ emitEvent: false });
      this.submitting.set(false);
    }
  }

  private async loadInvitation(): Promise<void> {
    const currentToken = this.token();
    if (!currentToken) {
      this.loadState.set('invalid');
      return;
    }

    this.loadState.set('loading');
    try {
      const details = await this.authStore.getInvitation(currentToken);
      this.invitation.set(details);
      this.loadState.set('ready');
    } catch {
      this.invitation.set(null);
      this.loadState.set('invalid');
    }
  }
}
