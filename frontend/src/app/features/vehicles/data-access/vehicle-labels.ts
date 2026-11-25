import { FoIconName } from '../../../shared/ui';
import {
  FuelType,
  MaintenanceType,
  PrimaryFuelType,
  VehicleAlert,
  VehicleAlertSeverity,
} from './vehicles.models';

export const FUEL_TYPE_OPTIONS: readonly FuelType[] = [
  'NAFTA_SUPER',
  'NAFTA_PREMIUM',
  'DIESEL',
  'DIESEL_PREMIUM',
  'GNC',
  'ELECTRICO',
];

export const PRIMARY_FUEL_TYPE_OPTIONS: readonly PrimaryFuelType[] = [
  ...FUEL_TYPE_OPTIONS,
  'HIBRIDO',
];

export const FUEL_TYPE_LABEL: Record<PrimaryFuelType, string> = {
  NAFTA_SUPER: 'Nafta súper',
  NAFTA_PREMIUM: 'Nafta premium',
  DIESEL: 'Diésel',
  DIESEL_PREMIUM: 'Diésel premium',
  GNC: 'GNC',
  ELECTRICO: 'Eléctrico',
  HIBRIDO: 'Híbrido',
};

export const MAINTENANCE_TYPE_OPTIONS: readonly MaintenanceType[] = [
  'SERVICE',
  'OIL_CHANGE',
  'FILTERS',
  'TIRES',
  'BRAKES',
  'TIMING_BELT',
  'BATTERY',
  'SUSPENSION',
  'ALIGNMENT',
  'VTV',
  'REPAIR',
  'OTHER',
];

export const MAINTENANCE_TYPE_LABEL: Record<MaintenanceType, string> = {
  SERVICE: 'Service',
  OIL_CHANGE: 'Cambio de aceite',
  FILTERS: 'Filtros',
  TIRES: 'Cubiertas',
  BRAKES: 'Frenos',
  TIMING_BELT: 'Correa de distribución',
  BATTERY: 'Batería',
  SUSPENSION: 'Suspensión',
  ALIGNMENT: 'Alineación y balanceo',
  VTV: 'VTV',
  REPAIR: 'Reparación',
  OTHER: 'Otro',
};

export const MAINTENANCE_TYPE_ICON: Record<MaintenanceType, FoIconName> = {
  SERVICE: 'wrench',
  OIL_CHANGE: 'fuel',
  FILTERS: 'sparkles',
  TIRES: 'car',
  BRAKES: 'siren',
  TIMING_BELT: 'repeat',
  BATTERY: 'plug-zap',
  SUSPENSION: 'car',
  ALIGNMENT: 'arrow-left-right',
  VTV: 'shield',
  REPAIR: 'wrench',
  OTHER: 'circle-ellipsis',
};

export interface MaintenanceDueSuggestion {
  addKm: number | null;
  addMonths: number | null;
  label: string;
}

export const MAINTENANCE_DUE_SUGGESTIONS: Partial<
  Record<MaintenanceType, MaintenanceDueSuggestion>
> = {
  OIL_CHANGE: { addKm: 10000, addMonths: 12, label: '+10.000 km / 12 meses' },
  SERVICE: { addKm: 10000, addMonths: 12, label: '+10.000 km / 12 meses' },
  FILTERS: { addKm: 10000, addMonths: 12, label: '+10.000 km / 12 meses' },
  VTV: { addKm: null, addMonths: 12, label: '+12 meses' },
  TIRES: { addKm: 40000, addMonths: null, label: '+40.000 km' },
  ALIGNMENT: { addKm: 10000, addMonths: 12, label: '+10.000 km / 12 meses' },
  TIMING_BELT: { addKm: 60000, addMonths: 48, label: '+60.000 km / 48 meses' },
};

const SEVERITY_ORDER: Record<VehicleAlertSeverity, number> = {
  OVERDUE: 0,
  WARNING: 1,
  INFO: 2,
};

export const ALERT_SEVERITY_LABEL: Record<VehicleAlertSeverity, string> = {
  OVERDUE: 'Vencido',
  WARNING: 'Próximo',
  INFO: 'Aviso',
};

const kmFormatter = new Intl.NumberFormat('es-AR', {
  maximumFractionDigits: 0,
});

const decimalFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

const MONTH_SHORT = [
  'Ene',
  'Feb',
  'Mar',
  'Abr',
  'May',
  'Jun',
  'Jul',
  'Ago',
  'Sep',
  'Oct',
  'Nov',
  'Dic',
];

export function formatKm(km: number | null | undefined): string {
  if (km == null || !Number.isFinite(km)) {
    return '—';
  }
  return `${kmFormatter.format(Math.round(km))} km`;
}

export function formatDecimal(value: number | string | null | undefined, suffix = ''): string {
  if (value == null || value === '') {
    return '—';
  }
  const num = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(num)) {
    return '—';
  }
  const formatted = decimalFormatter.format(num);
  return suffix ? `${formatted} ${suffix}` : formatted;
}

export function formatIsoDateShort(isoDate: string | null | undefined): string {
  if (!isoDate) {
    return '—';
  }
  const parts = isoDate.slice(0, 10).split('-');
  if (parts.length !== 3) {
    return isoDate;
  }
  const [year, monthStr, dayStr] = parts;
  const monthIdx = Number.parseInt(monthStr, 10) - 1;
  const day = Number.parseInt(dayStr, 10);
  if (!Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11 || !Number.isFinite(day)) {
    return isoDate;
  }
  return `${day} ${MONTH_SHORT[monthIdx]} ${year}`;
}

export function formatMonthShort(yearMonth: string): string {
  const parts = yearMonth.split('-');
  if (parts.length < 2) {
    return yearMonth;
  }
  const monthIdx = Number.parseInt(parts[1], 10) - 1;
  if (!Number.isFinite(monthIdx) || monthIdx < 0 || monthIdx > 11) {
    return yearMonth;
  }
  const shortYear = parts[0].slice(-2);
  return `${MONTH_SHORT[monthIdx]} '${shortYear}`;
}

export function sortVehicleAlerts(alerts: readonly VehicleAlert[]): VehicleAlert[] {
  return [...alerts].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]);
}

export function getTodayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function addMonthsToIsoDate(isoDate: string, months: number): string {
  const parts = isoDate.slice(0, 10).split('-');
  if (parts.length !== 3) {
    return getTodayIsoDate();
  }
  const year = Number.parseInt(parts[0], 10);
  const monthIdx = Number.parseInt(parts[1], 10) - 1;
  const day = Number.parseInt(parts[2], 10);
  if (!Number.isFinite(year) || !Number.isFinite(monthIdx) || !Number.isFinite(day)) {
    return getTodayIsoDate();
  }
  const totalMonths = year * 12 + monthIdx + months;
  const targetYear = Math.floor(totalMonths / 12);
  const targetMonth = ((totalMonths % 12) + 12) % 12;
  const maxDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const clampedDay = Math.min(day, maxDay);
  return `${targetYear}-${String(targetMonth + 1).padStart(2, '0')}-${String(clampedDay).padStart(2, '0')}`;
}

export function defaultFuelTypeForVehicle(
  primary: PrimaryFuelType,
  secondary: FuelType | null,
): FuelType {
  if (primary === 'HIBRIDO') {
    return secondary ?? 'NAFTA_SUPER';
  }
  return primary;
}
