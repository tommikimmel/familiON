import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { parseAuthError } from '../../../core/auth/auth-error';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AuthBannerComponent } from '../components/auth-banner.component';
import { OtpCodeInputComponent } from '../components/otp-code-input.component';

@Component({
  selector: 'fo-verify-email-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonComponent,
    AuthBannerComponent,
    OtpCodeInputComponent,
  ],
  template: `
    <section class="space-y-5">
      <header class="space-y-1 text-center">
        <h1 class="text-xl font-semibold text-text">Verificar correo</h1>
        @if (queryEmail(); as email) {
          <p class="text-sm text-muted">
            Ingresa el código de 6 dígitos que enviamos a
            <span class="font-medium text-text">{{ email }}</span>.
          </p>
        } @else {
          <p class="text-sm text-muted">
            Ingresa tu correo y el código de 6 dígitos que recibiste.
          </p>
        }
      </header>

      @if (generalError(); as message) {
        <fo-auth-banner kind="error" [message]="message" />
      }

      <form [formGroup]="form" (ngSubmit)="onSubmit()" novalidate class="space-y-4">
        @if (!queryEmail()) {
          <div class="space-y-1.5">
            <label for="verify-email-address" class="block text-sm font-medium text-text">
              Correo electrónico
            </label>
            <input
              id="verify-email-address"
              type="email"
              formControlName="email"
              autocomplete="email"
              placeholder="nombre@correo.com"
              [attr.aria-invalid]="emailError() ? 'true' : null"
              [attr.aria-describedby]="emailError() ? 'verify-email-address-error' : null"
              class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
              [class.border-border]="!emailError()"
              [class.border-danger]="!!emailError()"
            />
            @if (emailError(); as err) {
              <p id="verify-email-address-error" role="alert" class="text-xs font-medium text-danger">
                {{ err }}
              </p>
            }
          </div>
        }

        <fo-otp-code-input
          inputIdPrefix="verify-email-code"
          label="Código de verificación"
          [value]="code()"
          [disabled]="submitting()"
          [error]="codeError()"
          (codeChange)="onCodeChange($event)"
        />

        <fo-button
          type="submit"
          [fullWidth]="true"
          [loading]="submitting()"
          [disabled]="submitting() || !canSubmit()"
        >
          Verificar
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
export class VerifyEmailPage {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authStore = inject(AuthStore);
  private readonly toast = inject(ToastService);

  protected readonly queryEmail = signal<string>(
    this.route.snapshot.queryParamMap.get('email')?.trim() ?? '',
  );

  protected readonly form = this.fb.nonNullable.group({
    email: [this.queryEmail(), [Validators.required, Validators.email]],
  });

  protected readonly code = signal<string>('');
  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly generalError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected readonly canSubmit = computed(() => this.code().length === 6);

  protected onCodeChange(nextCode: string): void {
    this.code.set(nextCode);
    if (this.serverFieldErrors()['code']) {
      this.serverFieldErrors.update((current) => {
        const copy = { ...current };
        delete copy['code'];
        return copy;
      });
    }
  }

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

  protected codeError(): string | null {
    const server = this.serverFieldErrors()['code'];
    if (server) {
      return server;
    }
    if (this.submitted() && this.code().length < 6) {
      return 'Ingresa los 6 dígitos del código.';
    }
    return null;
  }

  protected async onSubmit(): Promise<void> {
    this.submitted.set(true);
    this.generalError.set(null);
    this.serverFieldErrors.set({});

    if (this.form.invalid || this.code().length !== 6) {
      this.form.markAllAsTouched();
      return;
    }

    const email = this.form.controls.email.value.trim();

    this.submitting.set(true);
    try {
      await this.authStore.verifyEmail(email, this.code());
      this.toast.success('Correo verificado');
      await this.router.navigate(['/auth/login']);
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo verificar el código. Verifica que sea correcto e intenta de nuevo.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      this.generalError.set(parsed.generalMessage);
    } finally {
      this.submitting.set(false);
    }
  }
}
