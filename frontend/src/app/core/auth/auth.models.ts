import { Role } from '../api/models/me.model';

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthSessionResponse {
  requires2fa: boolean;
  challengeToken?: string;
}

export interface VerifyTwoFactorRequest {
  challengeToken: string;
  code: string;
  trustDevice: boolean;
  deviceName?: string;
}

export interface ResendTwoFactorRequest {
  challengeToken: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  familyName: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
}

export interface VerifyEmailRequest {
  email: string;
  code: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface InvitationDetails {
  email: string;
  role: Role;
  familyName: string;
  expiresAt: string;
}

export interface AcceptInviteRequest {
  token: string;
  fullName: string;
  password: string;
}

export function sanitizeReturnUrl(raw: string | null | undefined): string | null {
  if (!raw) {
    return null;
  }
  const trimmed = raw.trim();
  if (!trimmed.startsWith('/') || trimmed.startsWith('//')) {
    return null;
  }
  const pathname = (trimmed.split('?')[0] ?? trimmed).split('#')[0] ?? trimmed;
  if (pathname === '/auth' || pathname.startsWith('/auth/')) {
    return null;
  }
  return trimmed;
}
