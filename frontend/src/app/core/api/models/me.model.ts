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
  family: MeFamily;
}
