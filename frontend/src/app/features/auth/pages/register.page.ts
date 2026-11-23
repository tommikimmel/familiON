import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { parseAuthError } from '../../../core/auth/auth-error';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { AuthBannerComponent } from '../components/auth-banner.component';
import { PasswordInputComponent } from '../components/password-input.component';
import {
  describePasswordError,
  passwordsMatchValidator,
  strongPasswordValidator,
} from '../utils/password-validators';

@Component({
  selector: 'fo-register-page',
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
        <h1 class="text-xl font-semibold text-text">Crear cuenta</h1>
        <p class="text-sm text-muted">Crea tu cuenta y espacio familiar en pocos pasos.</p>
      </header>

      @if (generalError(); as message) {
        <fo-auth-banner kind="error" [message]="message" />
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="space-y-4">
        <div class="space-y-1.5">
          <label for="register-full-name" class="block text-sm font-medium text-text">
            Nombre completo
          </label>
          <input
            id="register-full-name"
            type="text"
            formControlName="fullName"
            autocomplete="name"
            placeholder="Ana García"
            [attr.aria-invalid]="fieldError('fullName') ? 'true' : null"
            [attr.aria-describedby]="fieldError('fullName') ? 'register-full-name-error' : null"
            class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
            [class.border-border]="!fieldError('fullName')"
            [class.border-danger]="!!fieldError('fullName')"
          />
          @if (fieldError('fullName'); as err) {
            <p id="register-full-name-error" role="alert" class="text-xs font-medium text-danger">
              {{ err }}
            </p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="register-family-name" class="block text-sm font-medium text-text">
            Nombre de la familia
          </label>
          <input
            id="register-family-name"
            type="text"
            formControlName="familyName"
            autocomplete="organization"
            placeholder="Familia García"
            [attr.aria-invalid]="fieldError('familyName') ? 'true' : null"
            [attr.aria-describedby]="fieldError('familyName') ? 'register-family-name-error' : null"
            class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
            [class.border-border]="!fieldError('familyName')"
            [class.border-danger]="!!fieldError('familyName')"
          />
          @if (fieldError('familyName'); as err) {
            <p id="register-family-name-error" role="alert" class="text-xs font-medium text-danger">
              {{ err }}
            </p>
          }
        </div>

        <div class="space-y-1.5">
          <label for="register-email" class="block text-sm font-medium text-text">
            Correo electrónico
          </label>
          <input
            id="register-email"
            type="email"
            formControlName="email"
            autocomplete="email"
            placeholder="nombre@correo.com"
            [attr.aria-invalid]="fieldError('email') ? 'true' : null"
            [attr.aria-describedby]="fieldError('email') ? 'register-email-error' : null"
            class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
            [class.border-border]="!fieldError('email')"
            [class.border-danger]="!!fieldError('email')"
          />
          @if (fieldError('email'); as err) {
            <p id="register-email-error" role="alert" class="text-xs font-medium text-danger">
              {{ err }}
            </p>
          }
        </div>

        <fo-password-input
          inputId="register-password"
          label="Contraseña"
          formControlName="password"
          autocomplete="new-password"
          placeholder="Mínimo 10 caracteres"
          hint="Usa al menos 10 caracteres, con una letra y un número."
          [error]="passwordError()"
        />

        <fo-password-input
          inputId="register-confirm-password"
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
          Crear cuenta familiar
        </fo-button>
      </form>

      <p class="text-center text-sm text-muted">
        ¿Ya tienes cuenta?
        <a
          routerLink="/auth/login"
          class="fo-focus-ring rounded-md font-medium text-accent hover:underline"
        >
          Iniciar sesión
        </a>
      </p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly form = this.fb.nonNullable.group(
    {
      fullName: ['', [Validators.required]],
      familyName: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, strongPasswordValidator]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator('password', 'confirmPassword') },
  );

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected fieldError(field: 'fullName' | 'familyName' | 'email'): string | null {
    const server = this.serverFieldErrors()[field];
    if (server) {
      return server;
    }
    const ctrl = this.form.controls[field];
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    if (ctrl.hasError('required')) {
      if (field === 'fullName') {
        return 'Ingresa tu nombre completo.';
      }
      if (field === 'familyName') {
        return 'Ingresa el nombre de tu familia.';
      }
      return 'Ingresa tu correo electrónico.';
    }
    if (field === 'email' && ctrl.hasError('email')) {
      return 'Ingresa un correo electrónico válido.';
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

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { fullName, familyName, email, password } = this.form.getRawValue();
    const trimmedEmail = email.trim();

    this.submitting.set(true);
    this.form.disable({ emitEvent: false });
    try {
      const response = await this.authStore.register({
        fullName: fullName.trim(),
        familyName: familyName.trim(),
        email: trimmedEmail,
        password,
      });

      await this.router.navigate(['/auth/verify-email'], {
        queryParams: { email: response.email || trimmedEmail },
      });
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo crear la cuenta. Revisa los datos e intenta de nuevo.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      this.generalError.set(parsed.generalMessage);
    } finally {
      this.form.enable({ emitEvent: false });
      this.submitting.set(false);
    }
  }
}
