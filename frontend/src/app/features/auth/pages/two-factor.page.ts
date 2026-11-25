import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { parseAuthError } from '../../../core/auth/auth-error';
import { sanitizeReturnUrl } from '../../../core/auth/auth.models';
import { AuthStore } from '../../../core/auth/auth.store';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AuthBannerComponent } from '../components/auth-banner.component';
import { OtpCodeInputComponent } from '../components/otp-code-input.component';

const DEFAULT_RESEND_COOLDOWN_SECONDS = 60;

@Component({
  selector: 'fo-two-factor-page',
  standalone: true,
  imports: [RouterLink, ButtonComponent, AuthBannerComponent, OtpCodeInputComponent],
  template: `
    <section class="space-y-5">
      <header class="space-y-1.5 text-center">
        <h1 class="fo-auth-title">Verificación en dos pasos</h1>
        <p class="fo-auth-lead">
          Ingresa el código de 6 dígitos que enviamos a tu correo electrónico.
        </p>
      </header>

      @if (generalError(); as message) {
        <fo-auth-banner kind="error" [message]="message" />
      }

      <form (submit)="onSubmit($event)" novalidate class="space-y-4">
        <fo-otp-code-input
          inputIdPrefix="two-factor-code"
          label="Código de verificación"
          [value]="code()"
          [disabled]="submitting()"
          [error]="codeError()"
          (codeChange)="onCodeChange($event)"
        />

        <label
          for="two-factor-trust-device"
          class="flex cursor-pointer items-center justify-between gap-3 rounded-[12px] border border-border bg-bg px-3 py-2.5 text-[12.5px] font-medium text-text select-none"
        >
          <span>Confiar en este dispositivo por 30 días</span>
          <input
            id="two-factor-trust-device"
            type="checkbox"
            role="switch"
            [checked]="trustDevice()"
            [disabled]="submitting()"
            (change)="onTrustDeviceChange($event)"
            class="fo-auth-switch fo-focus-ring"
          />
        </label>

        <fo-button
          type="submit"
          size="lg"
          [fullWidth]="true"
          [loading]="submitting()"
          [disabled]="submitting() || !canSubmit()"
        >
          Verificar
        </fo-button>
      </form>

      <div class="flex flex-col items-center gap-2 text-sm">
        <button
          type="button"
          data-testid="resend-2fa-btn"
          [disabled]="resending() || cooldownSeconds() > 0"
          (click)="onResend()"
          class="fo-auth-link fo-focus-ring"
        >
          @if (cooldownSeconds() > 0) {
            Reenviar código en {{ cooldownSeconds() }} s
          } @else {
            Reenviar código
          }
        </button>

        <a
          routerLink="/auth/login"
          class="fo-focus-ring rounded-md text-xs text-muted hover:text-text hover:underline"
        >
          Volver a iniciar sesión
        </a>
      </div>
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TwoFactorPage implements OnInit {
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly code = signal<string>('');
  protected readonly trustDevice = signal<boolean>(true);
  protected readonly submitting = signal<boolean>(false);
  protected readonly submitted = signal<boolean>(false);
  protected readonly resending = signal<boolean>(false);
  protected readonly cooldownSeconds = signal<number>(DEFAULT_RESEND_COOLDOWN_SECONDS);
  protected readonly generalError = signal<string | null>(null);
  protected readonly serverFieldErrors = signal<Record<string, string>>({});

  protected readonly canSubmit = computed(() => this.code().length === 6);

  private countdownTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopCooldown());
  }

  ngOnInit(): void {
    if (!this.authStore.pendingChallenge()) {
      void this.router.navigate(['/auth/login'], { replaceUrl: true });
      return;
    }
    this.startCooldown(DEFAULT_RESEND_COOLDOWN_SECONDS);
  }

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

  protected onTrustDeviceChange(event: Event): void {
    this.trustDevice.set((event.target as HTMLInputElement).checked);
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

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.submitted.set(true);
    this.generalError.set(null);
    this.serverFieldErrors.set({});

    if (this.code().length !== 6) {
      return;
    }

    const returnUrl = sanitizeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));

    this.submitting.set(true);
    try {
      await this.authStore.verify2fa(this.code(), this.trustDevice());
      await this.router.navigateByUrl(returnUrl ?? '/home');
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'Código inválido o expirado. Intenta de nuevo.',
      );
      this.serverFieldErrors.set(parsed.fieldErrors);
      this.generalError.set(parsed.generalMessage);
    } finally {
      this.submitting.set(false);
    }
  }

  protected async onResend(): Promise<void> {
    if (this.resending() || this.cooldownSeconds() > 0) {
      return;
    }

    this.generalError.set(null);
    this.resending.set(true);
    try {
      await this.authStore.resend2fa();
      this.toast.success('Enviamos un nuevo código a tu correo');
      this.startCooldown(DEFAULT_RESEND_COOLDOWN_SECONDS);
    } catch (error: unknown) {
      const parsed = parseAuthError(
        error,
        'No se pudo reenviar el código. Intenta de nuevo en unos momentos.',
      );
      if (parsed.status === 429) {
        const waitSeconds = parsed.retryAfterSeconds ?? DEFAULT_RESEND_COOLDOWN_SECONDS;
        this.startCooldown(waitSeconds);
        this.generalError.set(
          parsed.generalMessage ??
            `Espera ${waitSeconds} segundos antes de solicitar un nuevo código.`,
        );
      } else {
        this.generalError.set(parsed.generalMessage);
      }
    } finally {
      this.resending.set(false);
    }
  }

  private startCooldown(seconds: number): void {
    this.stopCooldown();
    const initial = Math.max(1, Math.ceil(seconds));
    this.cooldownSeconds.set(initial);
    this.countdownTimer = setInterval(() => {
      const next = this.cooldownSeconds() - 1;
      if (next <= 0) {
        this.cooldownSeconds.set(0);
        this.stopCooldown();
      } else {
        this.cooldownSeconds.set(next);
      }
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.countdownTimer !== null) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }
}
