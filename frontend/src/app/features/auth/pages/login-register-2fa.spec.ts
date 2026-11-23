import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { LoginPage } from './login.page';
import { RegisterPage } from './register.page';
import { TwoFactorPage } from './two-factor.page';
import { VerifyEmailPage } from './verify-email.page';

function setInputValue(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('blur', { bubbles: true }));
}

describe('Auth screens: login, register, verify-email, two-factor', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('LoginPage', () => {
    it('submits credentials, respects returnUrl, and supports password visibility toggle', async () => {
      const loginSpy = vi.fn().mockResolvedValue({ requires2fa: false });

      TestBed.configureTestingModule({
        imports: [LoginPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { login: loginSpy } },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({ returnUrl: '/accounts' }) } },
          },
        ],
      });

      const router = TestBed.inject(Router);
      const navUrlSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

      const fixture = TestBed.createComponent(LoginPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      const emailInput = host.querySelector<HTMLInputElement>('#login-email')!;
      const passwordInput = host.querySelector<HTMLInputElement>('#login-password')!;
      expect(emailInput.getAttribute('autocomplete')).toBe('email');
      expect(passwordInput.getAttribute('autocomplete')).toBe('current-password');
      expect(passwordInput.type).toBe('password');

      const toggleBtn = host.querySelector<HTMLButtonElement>(
        'button[aria-label="Mostrar contraseña"]',
      )!;
      toggleBtn.click();
      fixture.detectChanges();
      expect(passwordInput.type).toBe('text');

      setInputValue(emailInput, 'ana@familion.test');
      setInputValue(passwordInput, 'Secret1234');
      fixture.detectChanges();

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(loginSpy).toHaveBeenCalledWith('ana@familion.test', 'Secret1234');
      expect(navUrlSpy).toHaveBeenCalledWith('/accounts');
    });

    it('navigates to /auth/2fa preserving returnUrl when requires2fa is true', async () => {
      const loginSpy = vi.fn().mockResolvedValue({ requires2fa: true, challengeToken: 'ch-1' });

      TestBed.configureTestingModule({
        imports: [LoginPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { login: loginSpy } },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({ returnUrl: '/plan' }) } },
          },
        ],
      });

      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      const fixture = TestBed.createComponent(LoginPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      setInputValue(host.querySelector<HTMLInputElement>('#login-email')!, 'ana@familion.test');
      setInputValue(host.querySelector<HTMLInputElement>('#login-password')!, 'Secret1234');

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(navSpy).toHaveBeenCalledWith(['/auth/2fa'], { queryParams: { returnUrl: '/plan' } });
    });

    it('shows 429 rate-limit message in minutes and maps backend field errors', async () => {
      const rateLimitError = new HttpErrorResponse({
        status: 429,
        headers: new HttpHeaders({ 'Retry-After': '300' }),
        error: { status: 429, detail: 'Too many attempts' },
      });
      const loginSpy = vi.fn().mockRejectedValueOnce(rateLimitError).mockRejectedValueOnce(
        new HttpErrorResponse({
          status: 400,
          error: {
            status: 400,
            errors: [{ field: 'email', message: 'Formato de correo inválido' }],
          },
        }),
      );

      TestBed.configureTestingModule({
        imports: [LoginPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { login: loginSpy } },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
          },
        ],
      });

      const fixture = TestBed.createComponent(LoginPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      setInputValue(host.querySelector<HTMLInputElement>('#login-email')!, 'ana@familion.test');
      setInputValue(host.querySelector<HTMLInputElement>('#login-password')!, 'Secret1234');

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(host.textContent).toContain('Demasiados intentos. Intenta de nuevo en 5 minutos.');

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(host.querySelector('#login-email-error')?.textContent).toContain(
        'Formato de correo inválido',
      );
    });
  });

  describe('RegisterPage', () => {
    it('enforces password policy and navigates to /auth/verify-email on success', async () => {
      const registerSpy = vi.fn().mockResolvedValue({ userId: 'u-1', email: 'ana@familion.test' });

      TestBed.configureTestingModule({
        imports: [RegisterPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { register: registerSpy } },
        ],
      });

      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      const fixture = TestBed.createComponent(RegisterPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      setInputValue(host.querySelector<HTMLInputElement>('#register-full-name')!, 'Ana García');
      setInputValue(host.querySelector<HTMLInputElement>('#register-family-name')!, 'Familia García');
      setInputValue(host.querySelector<HTMLInputElement>('#register-email')!, 'ana@familion.test');
      setInputValue(host.querySelector<HTMLInputElement>('#register-password')!, 'short');
      setInputValue(host.querySelector<HTMLInputElement>('#register-confirm-password')!, 'different');

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();

      expect(registerSpy).not.toHaveBeenCalled();
      expect(host.textContent).toContain('al menos 10 caracteres');
      expect(host.textContent).toContain('Las contraseñas no coinciden');

      setInputValue(host.querySelector<HTMLInputElement>('#register-password')!, 'Password1234');
      setInputValue(
        host.querySelector<HTMLInputElement>('#register-confirm-password')!,
        'Password1234',
      );

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(registerSpy).toHaveBeenCalledWith({
        fullName: 'Ana García',
        familyName: 'Familia García',
        email: 'ana@familion.test',
        password: 'Password1234',
      });
      expect(navSpy).toHaveBeenCalledWith(['/auth/verify-email'], {
        queryParams: { email: 'ana@familion.test' },
      });
    });
  });

  describe('VerifyEmailPage', () => {
    it('verifies OTP code, shows toast, and navigates to /auth/login', async () => {
      const verifySpy = vi.fn().mockResolvedValue(undefined);

      TestBed.configureTestingModule({
        imports: [VerifyEmailPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { verifyEmail: verifySpy } },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: { queryParamMap: convertToParamMap({ email: 'ana@familion.test' }) },
            },
          },
        ],
      });

      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      const toast = TestBed.inject(ToastService);

      const fixture = TestBed.createComponent(VerifyEmailPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      const inputs = Array.from(host.querySelectorAll<HTMLInputElement>('fo-otp-code-input input'));
      const pasteEvent = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent;
      Object.defineProperty(pasteEvent, 'clipboardData', {
        value: { getData: () => '123456' },
      });
      inputs[0].dispatchEvent(pasteEvent);
      fixture.detectChanges();

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(verifySpy).toHaveBeenCalledWith('ana@familion.test', '123456');
      expect(toast.current()?.message).toBe('Correo verificado');
      expect(navSpy).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  describe('TwoFactorPage', () => {
    it('redirects back to /auth/login when there is no pendingChallenge', () => {
      TestBed.configureTestingModule({
        imports: [TwoFactorPage],
        providers: [
          provideRouter([]),
          {
            provide: AuthStore,
            useValue: { pendingChallenge: signal<string | null>(null).asReadonly() },
          },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
          },
        ],
      });

      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      const fixture = TestBed.createComponent(TwoFactorPage);
      fixture.detectChanges();

      expect(navSpy).toHaveBeenCalledWith(['/auth/login'], { replaceUrl: true });
    });

    it('defaults trustDevice to true, counts down 60s, and respects Retry-After on 429', async () => {
      vi.useFakeTimers();
      const verify2faSpy = vi.fn().mockResolvedValue({ requires2fa: false });
      const resend2faSpy = vi.fn().mockRejectedValue(
        new HttpErrorResponse({
          status: 429,
          headers: new HttpHeaders({ 'Retry-After': '90' }),
          error: { status: 429 },
        }),
      );

      TestBed.configureTestingModule({
        imports: [TwoFactorPage],
        providers: [
          provideRouter([]),
          {
            provide: AuthStore,
            useValue: {
              pendingChallenge: signal<string | null>('ch-99').asReadonly(),
              verify2fa: verify2faSpy,
              resend2fa: resend2faSpy,
            },
          },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
          },
        ],
      });

      const fixture = TestBed.createComponent(TwoFactorPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      const checkbox = host.querySelector<HTMLInputElement>('#two-factor-trust-device')!;
      expect(checkbox.checked).toBe(true);

      const resendBtn = host.querySelector<HTMLButtonElement>('[data-testid="resend-2fa-btn"]')!;
      expect(resendBtn.disabled).toBe(true);
      expect(resendBtn.textContent).toContain('60 s');

      vi.advanceTimersByTime(60_000);
      fixture.detectChanges();
      expect(resendBtn.disabled).toBe(false);

      resendBtn.click();
      await Promise.resolve();
      await Promise.resolve();
      fixture.detectChanges();

      expect(resend2faSpy).toHaveBeenCalledTimes(1);
      expect(resendBtn.disabled).toBe(true);
      expect(resendBtn.textContent).toContain('90 s');
    });
  });
});
