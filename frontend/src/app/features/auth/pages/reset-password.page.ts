import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { parseAuthError } from '../../../core/auth/auth-error';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AuthBannerComponent } from '../components/auth-banner.component';
import { PasswordInputComponent } from '../components/password-input.component';
import {
  describePasswordError,
  passwordsMatchValidator,
  strongPasswordValidator,
} from '../utils/password-validators';

@Component({
  selector: 'fo-reset-password-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    AuthBannerComponent,
    PasswordInputComponent,
  ],
  template: `
    <section class="space-y-5">
      <header class="space-y-1 text-center">
        <h1 class="text-xl font-semibold text-text">Restablecer contraseña</h1>
        <p class="text-sm text-muted">Elige una nueva contraseña segura para tu cuenta.</p>
      </header>

      @if (!token()) {
        <fo-auth-banner
          kind="error"
          message="El enlace para restablecer la contraseña no es válido o expiró."
        />

        <div class="flex flex-col items-center gap-2 text-sm">
          <a
            routerLink="/auth/forgot-password"
            class="fo-focus-ring rounded-md font-medium text-accent hover:underline"
          >
            Solicitar un nuevo enlace
          </a>
          <a
            routerLink="/auth/login"
            class="fo-focus-ring rounded-md text-xs text-muted hover:text-text hover:underline"
          >
            Volver a iniciar sesión
          </a>
        </div>
      } @else {
        @if (generalError(); as message) {
          <fo-auth-banner kind="error" [message]="message" />
        }

        <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="space-y-4">
          <fo-password-input
            inputId="reset-new-password"
            label="Nueva contraseña"
            formControlName="newPassword"
            autocomplete="new-password"
            placeholder="Mínimo 10 caracteres"
            hint="Usa al menos 10 caracteres, con una letra y un número."
            [error]="passwordError()"
          />

          <fo-password-input
            inputId="reset-confirm-password"
            label="Confirmar contraseña"
            formControlName="confirmPassword"
            autocomplete="new-password"
            placeholder="Repite tu nueva contraseña"
            [error]="confirmPasswordError()"
          />

          <fo-button
            type="submit"
            [fullWidth]="true"
            [loading]="submitting()"
            [disabled]="submitting()"
          >
            Restablecer contraseña
          </fo-button>
        </form>

        <p class="text-center text-sm text-muted">
          <a
            routerLink="/auth/login"
            class="fo-focus-ring rounded-md font-medium text-accent hover:underline"
          >
            Volver a iniciar sesión
          </a>
        </p>
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordPage {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);
  private readonly toast = inject(ToastService);

  protected readonly token = signal<string>(
    this.route.snapshot.queryParamMap.get('token')?.trim() ?? '',
  );

  protected readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, strongPasswordValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator('newPassword', 'confirmPassword') },
  );

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected passwordError(): string | null {
    const server =
      this.serverFieldErrors()['newPassword'] ?? this.serverFieldErrors()['password'];
    if (server) {
      return server;
    }
    const ctrl = this.form.controls.newPassword;
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
      return 'Confirma tu nueva contraseña.';
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

    const { newPassword } = this.form.getRawValue();
    this.submitting.set(true);
    this.form.disable({ emitEvent: false });
    try {
      await this.authStore.resetPassword(this.token(), newPassword);
      this.toast.success('Contraseña restablecida. Ya puedes iniciar sesión.');
      await this.router.navigate(['/auth/login']);
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo restablecer la contraseña. El enlace puede haber expirado.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      this.generalError.set(
        parsed.generalMessage ?? parsed.fieldErrors['token'] ?? null,
      );
    } finally {
      this.form.enable({ emitEvent: false });
      this.submitting.set(false);
    }
  }
}
