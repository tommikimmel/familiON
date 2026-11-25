import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { formatTooManyAttemptsMessage, parseAuthError } from '../../../core/auth/auth-error';
import { sanitizeReturnUrl } from '../../../core/auth/auth.models';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { AuthBannerComponent } from '../components/auth-banner.component';
import { PasswordInputComponent } from '../components/password-input.component';

@Component({
  selector: 'fo-login-page',
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
      <header class="space-y-1.5 text-center">
        <h1 class="fo-auth-title">Iniciar sesión</h1>
        <p class="fo-auth-lead">Ingresa con tu cuenta familiar para continuar.</p>
      </header>

      @if (generalError(); as message) {
        <fo-auth-banner kind="error" [message]="message" />
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="space-y-4">
        <div class="space-y-1.5">
          <label for="login-email" class="fo-auth-label">
            Correo electrónico
          </label>
          <input
            id="login-email"
            type="email"
            formControlName="email"
            autocomplete="email"
            placeholder="nombre@correo.com"
            [attr.aria-invalid]="emailError() ? 'true' : null"
            [attr.aria-describedby]="emailError() ? 'login-email-error' : null"
            class="fo-auth-input"
          />
          @if (emailError(); as err) {
            <p id="login-email-error" role="alert" class="fo-auth-error">
              {{ err }}
            </p>
          }
        </div>

        <fo-password-input
          inputId="login-password"
          label="Contraseña"
          formControlName="password"
          autocomplete="current-password"
          placeholder="Tu contraseña"
          [error]="passwordError()"
        />

        <div class="flex justify-end">
          <a
            routerLink="/auth/forgot-password"
            class="fo-auth-link fo-focus-ring text-xs"
          >
            ¿Olvidaste tu contraseña?
          </a>
        </div>

        <fo-button
          type="submit"
          size="lg"
          [fullWidth]="true"
          [loading]="submitting()"
          [disabled]="submitting()"
        >
          Ingresar
        </fo-button>
      </form>

      <p class="fo-auth-foot">
        ¿Nuevo en FamiliON?
        <a
          routerLink="/auth/register"
          class="fo-auth-link fo-focus-ring"
        >
          Crear cuenta familiar
        </a>
      </p>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected emailError(): string | null {
    const server = this.serverFieldErrors()['email'];
    if (server) {
      return server;
    }
    const ctrl = this.form.controls.email;
    if (!ctrl.touched && !this.submitted()) {
      return null;
    }
    if (ctrl.hasError('required')) {
      return 'Ingresa tu correo electrónico.';
    }
    if (ctrl.hasError('email')) {
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
    if (ctrl.hasError('required')) {
      return 'Ingresa tu contraseña.';
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

    const { email, password } = this.form.getRawValue();
    const returnUrl = sanitizeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));

    this.submitting.set(true);
    this.form.disable({ emitEvent: false });
    try {
      const response = await this.authStore.login(email.trim(), password);
      if (response.requires2fa) {
        await this.router.navigate(['/auth/2fa'], {
          queryParams: returnUrl ? { returnUrl } : undefined,
        });
        return;
      }

      await this.router.navigateByUrl(returnUrl ?? '/home');
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo iniciar sesión. Verifica tus credenciales e intenta de nuevo.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      if (parsed.status === 429) {
        this.generalError.set(formatTooManyAttemptsMessage(parsed.retryAfterSeconds));
      } else {
        this.generalError.set(parsed.generalMessage);
      }
    } finally {
      this.form.enable({ emitEvent: false });
      this.submitting.set(false);
    }
  }
}
