import { FamilyVisibility, Role } from '../../../core/api/models/me.model';

export type FamilyCurrencyCode = 'ARS' | 'USD' | 'EUR' | 'USDT';
export type ReferenceCurrencyCode = 'USD' | 'EUR' | 'UVA' | 'ARS';
export type RateSource =
  | 'USD_OFICIAL'
  | 'USD_MEP'
  | 'USD_BLUE'
  | 'EUR_OFICIAL'
  | 'EUR_BLUE'
  | 'UVA';

export interface SelectOption<T = string> {
  readonly value: T;
  readonly label: string;
}

export const SUPPORTED_CURRENCIES: readonly SelectOption<FamilyCurrencyCode>[] = [
  { value: 'ARS', label: 'ARS — Peso argentino' },
  { value: 'USD', label: 'USD — Dólar estadounidense' },
  { value: 'EUR', label: 'EUR — Euro' },
  { value: 'USDT', label: 'USDT — Tether' },
];

export const REFERENCE_CURRENCIES: readonly SelectOption<ReferenceCurrencyCode>[] = [
  { value: 'USD', label: 'USD — Dólar estadounidense' },
  { value: 'EUR', label: 'EUR — Euro' },
  { value: 'UVA', label: 'UVA — Unidad de Valor Adquisitivo' },
  { value: 'ARS', label: 'ARS — Peso argentino' },
];

export const RATE_SOURCES: readonly SelectOption<RateSource>[] = [
  { value: 'USD_OFICIAL', label: 'Dólar oficial' },
  { value: 'USD_MEP', label: 'Dólar MEP' },
  { value: 'USD_BLUE', label: 'Dólar blue' },
  { value: 'EUR_OFICIAL', label: 'Euro oficial' },
  { value: 'EUR_BLUE', label: 'Euro blue' },
  { value: 'UVA', label: 'UVA' },
];

export const VISIBILITY_OPTIONS: readonly SelectOption<FamilyVisibility>[] = [
  { value: 'OWN_ONLY', label: 'Solo lo suyo' },
  { value: 'FAMILY_SUMMARY', label: 'Totales de la familia' },
  { value: 'FAMILY_FULL', label: 'Todo el detalle' },
];

export const ROLE_OPTIONS: readonly SelectOption<Role>[] = [
  { value: 'ADULTO', label: 'Adulto' },
  { value: 'HIJO', label: 'Hijo/a' },
];

export interface FamilyCurrency {
  currencyCode: string;
  preferredRateSource: string | null;
  isActive: boolean;
}

export interface Family {
  id: string;
  name: string;
  baseCurrency: string;
  referenceCurrency: string;
  referenceRateSource: string;
  timezone: string;
  enabledCurrencies: FamilyCurrency[];
}

export interface UpdateFamilySettingsRequest {
  name?: string;
  baseCurrency?: string;
  referenceCurrency?: string;
  referenceRateSource?: string;
  timezone?: string;
}

export interface EnableFamilyCurrencyRequest {
  currencyCode: string;
  preferredRateSource?: string | null;
}

export interface UpdateFamilyCurrencyRequest {
  isActive?: boolean;
  preferredRateSource?: string | null;
}

export interface Member {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  familyVisibility: FamilyVisibility;
  accentColor: string;
  isActive: boolean;
}

export interface UpdateMemberVisibilityRequest {
  familyVisibility: FamilyVisibility;
}

export interface InvitationInviter {
  id: string;
  fullName: string;
}

export interface Invitation {
  id: string;
  email: string;
  role: Role;
  familyVisibility: FamilyVisibility;
  expiresAt: string;
  createdAt: string;
  invitedBy: InvitationInviter;
}

export interface InvitationCreated extends Invitation {
  inviteUrl: string;
}

export interface CreateInvitationRequest {
  email: string;
  role: Role;
  familyVisibility?: FamilyVisibility;
}

export interface TimezoneGroups {
  america: string[];
  other: string[];
}

const FALLBACK_TIMEZONES = [
  'America/Argentina/Buenos_Aires',
  'America/Bogota',
  'America/Lima',
  'America/Mexico_City',
  'America/Montevideo',
  'America/New_York',
  'America/Santiago',
  'America/Sao_Paulo',
  'Europe/Madrid',
  'UTC',
];

export function getSupportedTimezones(ensureTimezone?: string | null): TimezoneGroups {
  let values: string[] = [];
  try {
    const intlApi = Intl as typeof Intl & {
      supportedValuesOf?: (key: string) => string[];
    };
    if (typeof intlApi.supportedValuesOf === 'function') {
      values = intlApi.supportedValuesOf('timeZone');
    }
  } catch {
    values = [];
  }

  const all = values.length > 0 ? [...values] : [...FALLBACK_TIMEZONES];
  if (ensureTimezone && !all.includes(ensureTimezone)) {
    all.push(ensureTimezone);
  }

  const america = all.filter((tz) => tz.startsWith('America/')).sort();
  const other = all.filter((tz) => !tz.startsWith('America/')).sort();
  return { america, other };
}

export function getRateSourceLabel(source: string | null | undefined): string {
  if (!source) {
    return 'Automática';
  }
  return RATE_SOURCES.find((option) => option.value === source)?.label ?? source;
}

export function getVisibilityLabel(visibility: FamilyVisibility): string {
  return VISIBILITY_OPTIONS.find((option) => option.value === visibility)?.label ?? visibility;
}

export function getRoleLabel(role: Role): string {
  return role === 'ADULTO' ? 'Adulto' : 'Hijo/a';
}
