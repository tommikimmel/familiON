import { Decimal } from '../../../core/finance/finance.models';
import { PageMetadata } from '../../../core/finance/transactions.models';
import { Lens } from '../../../core/state/view-context.store';

export type FuelType =
  | 'NAFTA_SUPER'
  | 'NAFTA_PREMIUM'
  | 'DIESEL'
  | 'DIESEL_PREMIUM'
  | 'GNC'
  | 'ELECTRICO';

export type PrimaryFuelType = FuelType | 'HIBRIDO';

export type VehicleAlertKind = 'SERVICE_KM' | 'SERVICE_DATE' | 'VTV' | 'INSURANCE';

export type VehicleAlertSeverity = 'INFO' | 'WARNING' | 'OVERDUE';

export interface VehicleAlert {
  kind: VehicleAlertKind;
  title: string;
  dueDate: string | null;
  dueKm: number | null;
  severity: VehicleAlertSeverity;
}

export interface Vehicle {
  id: string;
  nickname: string;
  brand: string;
  model: string;
  modelYear: number;
  licensePlate: string;
  primaryFuelType: PrimaryFuelType;
  secondaryFuelType: FuelType | null;
  currentOdometerKm: number;
  vtvExpirationDate: string | null;
  insuranceExpiration: string | null;
  insuranceCompany: string | null;
  notes: string | null;
  isActive: boolean;
  alerts: VehicleAlert[];
}

export interface VehicleCreate {
  nickname: string;
  brand: string;
  model: string;
  modelYear: number;
  licensePlate: string;
  primaryFuelType: PrimaryFuelType;
  secondaryFuelType?: FuelType | null;
  currentOdometerKm: number;
  vtvExpirationDate?: string | null;
  insuranceExpiration?: string | null;
  insuranceCompany?: string | null;
  notes?: string | null;
}

export interface VehicleUpdate extends Partial<VehicleCreate> {
  isActive?: boolean;
}

export interface VehicleUserSummary {
  id: string;
  fullName: string;
}

export interface FuelLogCreate {
  fuelDate?: string;
  odometerKm: number;
  fuelType: FuelType;
  liters?: Decimal;
  pricePerLiter?: Decimal;
  totalAmount?: Decimal;
  currencyCode: string;
  fullTank: boolean;
  stationName?: string;
  notes?: string;
  accountId: string;
  attributedToUserId?: string;
}

export interface FuelLog {
  id: string;
  fuelDate: string;
  odometerKm: number;
  fuelType: FuelType;
  liters: Decimal;
  pricePerLiter: Decimal;
  totalAmount: Decimal;
  currencyCode: string;
  fullTank: boolean;
  stationName: string | null;
  notes: string | null;
  transactionId: string | null;
  user: VehicleUserSummary;
}

export type MaintenanceType =
  | 'SERVICE'
  | 'OIL_CHANGE'
  | 'FILTERS'
  | 'TIRES'
  | 'BRAKES'
  | 'TIMING_BELT'
  | 'BATTERY'
  | 'SUSPENSION'
  | 'ALIGNMENT'
  | 'VTV'
  | 'REPAIR'
  | 'OTHER';

export interface MaintenanceLogCreate {
  maintenanceType: MaintenanceType;
  maintenanceDate?: string;
  odometerKm: number;
  description: string;
  workshopName?: string;
  costAmount: Decimal;
  currencyCode: string;
  accountId?: string;
  nextDueKm?: number;
  nextDueDate?: string;
  notes?: string;
}

export interface MaintenanceLog extends MaintenanceLogCreate {
  id: string;
  maintenanceDate: string;
  workshopName?: string;
  transactionId: string | null;
  user: VehicleUserSummary;
}

export interface VehicleMonthlyPoint {
  month: string;
  fuelCost: Decimal;
  maintenanceCost: Decimal;
  km: number;
}

export interface VehiclePriceTrendPoint {
  date: string;
  pricePerLiter: Decimal;
  fuelType: FuelType;
}

export interface VehicleSummary {
  vehicleId: string;
  from: string;
  to: string;
  lens: Lens | string;
  unit: string;
  distanceKm: number;
  totalLiters: Decimal;
  avgKmPerLiter: Decimal | null;
  avgLitersPer100Km: Decimal | null;
  costPerKm: Decimal | null;
  totalCost: Decimal;
  fuelCost: Decimal;
  maintenanceCost: Decimal;
  lensApproximated: boolean;
  monthly: VehicleMonthlyPoint[];
  pricePerLiterTrend: VehiclePriceTrendPoint[];
}

export interface VehicleDashboard {
  vehicle: Vehicle | null;
  summary: VehicleSummary | null;
  recentFuelLogs: FuelLog[] | null;
  recentMaintenance: MaintenanceLog[] | null;
  alerts: VehicleAlert[] | null;
}

export interface VehiclePage<T> {
  content: T[];
  page?: PageMetadata;
}
