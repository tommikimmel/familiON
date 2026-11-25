import { API } from '../api-config';

export type Role = 'ADULTO' | 'HIJO';
export type FamilyVisibility = 'OWN_ONLY' | 'FAMILY_SUMMARY' | 'FAMILY_FULL';
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';

export interface MeFamily {
  id: string;
  name: string;
  baseCurrency: string;
  referenceCurrency: string;
  referenceRateSource: string;
  timezone: string;
  enabledCurrencies: string[];
}

export interface Me {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  familyVisibility: FamilyVisibility;
  themePreference: ThemePreference;
  accentColor: string;
  twoFactorEnabled: boolean;
  emailVerified: boolean;
  avatarUpdatedAt?: string | null;
  family: MeFamily;
}

/** Profile photo URL (cache-busted by its timestamp), or null to fall back to initials. */
export function avatarUrl(me: Pick<Me, 'avatarUpdatedAt'> | null | undefined): string | null {
  return me?.avatarUpdatedAt
    ? `${API}/auth/me/avatar?v=${encodeURIComponent(me.avatarUpdatedAt)}`
    : null;
}
