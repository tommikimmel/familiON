import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API, BFF_BASE } from '../api/api-config';
import { Me } from '../api/models/me.model';
import { SKIP_AUTH_REDIRECT } from './auth.context';
import {
  AcceptInviteRequest,
  AuthSessionResponse,
  ForgotPasswordRequest,
  InvitationDetails,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  ResendTwoFactorRequest,
  ResetPasswordRequest,
  VerifyEmailRequest,
  VerifyTwoFactorRequest,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  csrf(): Observable<void> {
    return this.http.get<void>(`${BFF_BASE}/csrf`);
  }

  getMe(options?: { skipAuthRedirect?: boolean }): Observable<Me> {
    const context = options?.skipAuthRedirect
      ? new HttpContext().set(SKIP_AUTH_REDIRECT, true)
      : undefined;
    return this.http.get<Me>(`${API}/auth/me`, context ? { context } : undefined);
  }

  login(payload: LoginRequest): Observable<AuthSessionResponse> {
    return this.http.post<AuthSessionResponse>(`${BFF_BASE}/auth/login`, payload);
  }

  verify2fa(payload: VerifyTwoFactorRequest): Observable<AuthSessionResponse> {
    return this.http.post<AuthSessionResponse>(`${BFF_BASE}/auth/2fa/verify`, payload);
  }

  resend2fa(payload: ResendTwoFactorRequest): Observable<void> {
    return this.http.post<void>(`${BFF_BASE}/auth/2fa/resend`, payload);
  }

  register(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${BFF_BASE}/auth/register`, payload);
  }

  verifyEmail(payload: VerifyEmailRequest): Observable<void> {
    return this.http.post<void>(`${BFF_BASE}/auth/verify-email`, payload);
  }

  forgotPassword(payload: ForgotPasswordRequest): Observable<void> {
    return this.http.post<void>(`${BFF_BASE}/auth/forgot-password`, payload);
  }

  resetPassword(payload: ResetPasswordRequest): Observable<void> {
    return this.http.post<void>(`${BFF_BASE}/auth/reset-password`, payload);
  }

  getInvitation(token: string): Observable<InvitationDetails> {
    const context = new HttpContext().set(SKIP_AUTH_REDIRECT, true);
    return this.http.get<InvitationDetails>(
      `${API}/auth/invitations/${encodeURIComponent(token)}`,
      { context },
    );
  }

  acceptInvite(payload: AcceptInviteRequest): Observable<AuthSessionResponse> {
    return this.http.post<AuthSessionResponse>(`${BFF_BASE}/auth/register-invite`, payload);
  }

  refresh(): Observable<void> {
    return this.http.post<void>(`${BFF_BASE}/auth/refresh`, null);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${BFF_BASE}/auth/logout`, null);
  }
}
