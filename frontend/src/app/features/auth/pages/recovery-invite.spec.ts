import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AcceptInvitePage } from './accept-invite.page';
import { ForgotPasswordPage } from './forgot-password.page';
import { ResetPasswordPage } from './reset-password.page';

function setInputValue(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('blur', { bubbles: true }));
}

describe('Auth screens: forgot-password, reset-password, accept-invite', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  describe('ForgotPasswordPage', () => {
    it('submits email and displays the neutral confirmation message', async () => {
      const forgotSpy = vi.fn().mockResolvedValue(undefined);

      TestBed.configureTestingModule({
        imports: [ForgotPasswordPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { forgotPassword: forgotSpy } },
        ],
      });

      const fixture = TestBed.createComponent(ForgotPasswordPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      setInputValue(host.querySelector<HTMLInputElement>('#forgot-email')!, 'ana@familion.test');
      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(forgotSpy).toHaveBeenCalledWith('ana@familion.test');
      expect(host.textContent).toContain(
        'Si el correo está registrado, recibirás un enlace para restablecer la contraseña.',
      );
    });
  });

  describe('ResetPasswordPage', () => {
    it('resets password with query token, shows toast, and navigates to /auth/login', async () => {
      const resetSpy = vi.fn().mockResolvedValue(undefined);

      TestBed.configureTestingModule({
        imports: [ResetPasswordPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { resetPassword: resetSpy } },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: { queryParamMap: convertToParamMap({ token: 'reset-tok-1' }) },
            },
          },
        ],
      });

      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      const toast = TestBed.inject(ToastService);

      const fixture = TestBed.createComponent(ResetPasswordPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      const newPwdInput = host.querySelector<HTMLInputElement>('#reset-new-password')!;
      const confirmPwdInput = host.querySelector<HTMLInputElement>('#reset-confirm-password')!;
      expect(newPwdInput.getAttribute('autocomplete')).toBe('new-password');
      expect(confirmPwdInput.getAttribute('autocomplete')).toBe('new-password');

      setInputValue(newPwdInput, 'NewSecure123');
      setInputValue(confirmPwdInput, 'NewSecure123');

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(resetSpy).toHaveBeenCalledWith('reset-tok-1', 'NewSecure123');
      expect(toast.current()?.message).toContain('Contraseña restablecida');
      expect(navSpy).toHaveBeenCalledWith(['/auth/login']);
    });

    it('renders invalid link state when token query parameter is absent', () => {
      TestBed.configureTestingModule({
        imports: [ResetPasswordPage],
        providers: [
          provideRouter([]),
          { provide: AuthStore, useValue: { resetPassword: vi.fn() } },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
          },
        ],
      });

      const fixture = TestBed.createComponent(ResetPasswordPage);
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('form')).toBeNull();
      expect(host.textContent).toContain('no es válido o expiró');
    });
  });

  describe('AcceptInvitePage', () => {
    it('loads invitation details, renders family/role/email, and navigates to /home on submit', async () => {
      const getInvitationSpy = vi.fn().mockResolvedValue({
        email: 'lucas@familion.test',
        role: 'HIJO',
        familyName: 'García',
        expiresAt: '2026-12-01T00:00:00Z',
      });
      const acceptInviteSpy = vi.fn().mockResolvedValue({ requires2fa: false });

      TestBed.configureTestingModule({
        imports: [AcceptInvitePage],
        providers: [
          provideRouter([]),
          {
            provide: AuthStore,
            useValue: {
              getInvitation: getInvitationSpy,
              acceptInvite: acceptInviteSpy,
            },
          },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: { queryParamMap: convertToParamMap({ token: 'inv-123' }) },
            },
          },
        ],
      });

      const router = TestBed.inject(Router);
      const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      const fixture = TestBed.createComponent(AcceptInvitePage);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      expect(getInvitationSpy).toHaveBeenCalledWith('inv-123');
      expect(host.textContent).toContain('Te invitaron a la familia García como Hijo/a');
      expect(host.textContent).toContain('lucas@familion.test');

      setInputValue(host.querySelector<HTMLInputElement>('#invite-full-name')!, 'Lucas García');
      setInputValue(host.querySelector<HTMLInputElement>('#invite-password')!, 'SecurePass123');
      setInputValue(
        host.querySelector<HTMLInputElement>('#invite-confirm-password')!,
        'SecurePass123',
      );

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(acceptInviteSpy).toHaveBeenCalledWith('inv-123', 'Lucas García', 'SecurePass123');
      expect(navSpy).toHaveBeenCalledWith(['/home']);
    });

    it('renders error state with link to login when invitation token is invalid', async () => {
      const getInvitationSpy = vi.fn().mockRejectedValue(new Error('404'));

      TestBed.configureTestingModule({
        imports: [AcceptInvitePage],
        providers: [
          provideRouter([]),
          {
            provide: AuthStore,
            useValue: {
              getInvitation: getInvitationSpy,
              acceptInvite: vi.fn(),
            },
          },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: { queryParamMap: convertToParamMap({ token: 'bad-token' }) },
            },
          },
        ],
      });

      const fixture = TestBed.createComponent(AcceptInvitePage);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const host = fixture.nativeElement as HTMLElement;
      expect(host.textContent).toContain('Invitación no válida');
      expect(host.querySelector('a[href="/auth/login"]')).not.toBeNull();
    });
  });
});
