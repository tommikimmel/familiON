import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { parseAuthError } from '../../../core/auth/auth-error';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { AuthBannerComponent } from '../components/auth-banner.component';

const NEUTRAL_CONFIRMATION =
  'Si el correo está registrado, recibirás un enlace para restablecer la contraseña.';

@Component({
  selector: 'fo-forgot-password-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, AuthBannerComponent],
  template: `
    <section class="space-y-5">
      <header class="space-y-1 text-center">
        <h1 class="text-xl font-semibold text-text">Recuperar contraseña</h1>
        <p class="text-sm text-muted">
          Ingresa tu correo y te enviaremos instrucciones para restablecer tu contraseña.
        </p>
      </header>

      @if (sent()) {
        <fo-auth-banner kind="success" [message]="neutralConfirmation" />
      }

      @if (generalError(); as message) {
        <fo-auth-banner kind="error" [message]="message" />
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="space-y-4">
        <div class="space-y-1.5">
          <label for="forgot-email" class="block text-sm font-medium text-text">
            Correo electrónico
          </label>
          <input
            id="forgot-email"
            type="email"
            formControlName="email"
            autocomplete="email"
            placeholder="nombre@correo.com"
            [attr.aria-invalid]="emailError() ? 'true' : null"
            [attr.aria-describedby]="emailError() ? 'forgot-email-error' : null"
            class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
            [class.border-border]="!emailError()"
            [class.border-danger]="!!emailError()"
          />
          @if (emailError(); as err) {
            <p id="forgot-email-error" role="alert" class="text-xs font-medium text-danger">
              {{ err }}
            </p>
          }
        </div>

        <fo-button
          type="submit"
          [fullWidth]="true"
          [loading]="submitting()"
          [disabled]="submitting()"
        >
          Enviar enlace
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
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordPage {
  private readonly fb = inject(FormBuilder);
  private readonly authStore = inject(AuthStore);

  protected readonly neutralConfirmation = NEUTRAL_CONFIRMATION;

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly sent = signal(false);
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

  protected async onSubmit(): Promise<void> {
    this.submitted.set(true);
    this.generalError.set(null);
    this.serverFieldErrors.set({});

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email } = this.form.getRawValue();
    this.submitting.set(true);
    this.form.disable({ emitEvent: false });
    try {
      await this.authStore.forgotPassword(email.trim());
      this.sent.set(true);
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo procesar la solicitud. Intenta de nuevo.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      this.generalError.set(parsed.generalMessage);
    } finally {
      this.form.enable({ emitEvent: false });
      this.submitting.set(false);
    }
  }
}
