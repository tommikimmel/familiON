import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Me } from '../api/models/me.model';
import { SessionStore } from '../state/session.store';
import { ThemeService } from '../theme/theme.service';
import { AuthApi } from './auth.api';
import {
  AuthSessionResponse,
  AuthStatus,
  InvitationDetails,
  RegisterRequest,
  RegisterResponse,
  sanitizeReturnUrl,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly session = inject(SessionStore);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);

  private readonly _status = signal<AuthStatus>('unknown');
  private readonly _pendingChallenge = signal<string | null>(null);

  private initPromise: Promise<void> | null = null;

  readonly status: Signal<AuthStatus> = this._status.asReadonly();
  readonly pendingChallenge: Signal<string | null> = this._pendingChallenge.asReadonly();
  readonly isAuthenticated: Signal<boolean> = computed(() => this._status() === 'authenticated');

  init(): Promise<void> {
    if (this._status() !== 'unknown') {
      return Promise.resolve();
    }
    if (!this.initPromise) {
      this.initPromise = this.runInit().finally(() => {
        this.initPromise = null;
      });
    }
    return this.initPromise;
  }

  async reloadMe(): Promise<Me> {
    const me = await firstValueFrom(this.api.getMe());
    this.applyAuthenticatedSession(me);
    return me;
  }

  async login(email: string, password: string): Promise<AuthSessionResponse> {
    const response = await firstValueFrom(this.api.login({ email, password }));
    if (response.requires2fa) {
      this._pendingChallenge.set(response.challengeToken ?? null);
      return response;
    }

    this._pendingChallenge.set(null);
    await this.reloadMe();
    return response;
  }

  async verify2fa(
    code: string,
    trustDevice: boolean,
    deviceName?: string,
  ): Promise<AuthSessionResponse> {
    const challengeToken = this._pendingChallenge();
    if (!challengeToken) {
      throw new Error('Missing pending 2FA challenge token.');
    }

    const response = await firstValueFrom(
      this.api.verify2fa({
        challengeToken,
        code,
        trustDevice,
        ...(deviceName ? { deviceName } : {}),
      }),
    );

    this._pendingChallenge.set(null);
    await this.reloadMe();
    return response;
  }

  async resend2fa(): Promise<void> {
    const challengeToken = this._pendingChallenge();
    if (!challengeToken) {
      throw new Error('Missing pending 2FA challenge token.');
    }
    await firstValueFrom(this.api.resend2fa({ challengeToken }));
  }

  async register(payload: RegisterRequest): Promise<RegisterResponse> {
    return firstValueFrom(this.api.register(payload));
  }

  async verifyEmail(email: string, code: string): Promise<void> {
    await firstValueFrom(this.api.verifyEmail({ email, code }));
  }

  async forgotPassword(email: string): Promise<void> {
    await firstValueFrom(this.api.forgotPassword({ email }));
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.api.resetPassword({ token, newPassword }));
  }

  async getInvitation(token: string): Promise<InvitationDetails> {
    return firstValueFrom(this.api.getInvitation(token));
  }

  async acceptInvite(
    token: string,
    fullName: string,
    password: string,
  ): Promise<AuthSessionResponse> {
    const response = await firstValueFrom(this.api.acceptInvite({ token, fullName, password }));
    this._pendingChallenge.set(null);
    await this.reloadMe();
    return response;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } catch {
      // Always clear local session even if remote logout fails.
    }
    this.clearLocalSession();
    await this.router.navigate(['/auth/login']);
  }

  clearLocalSession(): void {
    this.session.setMe(null);
    this._pendingChallenge.set(null);
    this._status.set('anonymous');
  }

  async logoutLocal(returnUrl?: string | null): Promise<void> {
    this.clearLocalSession();
    const safeReturnUrl = sanitizeReturnUrl(returnUrl);
    await this.router.navigate(['/auth/login'], {
      queryParams: safeReturnUrl ? { returnUrl: safeReturnUrl } : undefined,
    });
  }

  private async runInit(): Promise<void> {
    try {
      await firstValueFrom(this.api.csrf());
    } catch {
      // Proceed to session check so transient CSRF failures still resolve status cleanly.
    }

    try {
      const me = await firstValueFrom(this.api.getMe({ skipAuthRedirect: true }));
      this.applyAuthenticatedSession(me);
    } catch {
      this.clearLocalSession();
    }
  }

  private applyAuthenticatedSession(me: Me): void {
    this.session.setMe(me);
    this.theme.apply(me);
    this._pendingChallenge.set(null);
    this._status.set('authenticated');
  }
}
