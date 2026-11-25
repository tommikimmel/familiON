import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { VehicleDashboard } from '../data-access/vehicles.models';
import { VehicleDetailPage } from './vehicle-detail.page';

const FULL_DASHBOARD: VehicleDashboard = {
  vehicle: {
    id: 'veh-1',
    nickname: 'Cronos gris',
    brand: 'Fiat',
    model: 'Cronos',
    modelYear: 2022,
    licensePlate: 'AF 123 CD',
    primaryFuelType: 'NAFTA_SUPER',
    secondaryFuelType: null,
    currentOdometerKm: 45200,
    vtvExpirationDate: null,
    insuranceExpiration: null,
    insuranceCompany: null,
    notes: null,
    isActive: true,
    alerts: [],
  },
  summary: {
    vehicleId: 'veh-1',
    from: '2026-01-01',
    to: '2026-11-24',
    lens: 'NOMINAL',
    unit: 'ARS',
    distanceKm: 10000,
    totalLiters: '800',
    avgKmPerLiter: '12.5',
    avgLitersPer100Km: '8.0',
    costPerKm: '120',
    totalCost: '1200000',
    fuelCost: '900000',
    maintenanceCost: '300000',
    lensApproximated: false,
    monthly: [{ month: '2026-11', fuelCost: '100000', maintenanceCost: '50000', km: 900 }],
    pricePerLiterTrend: [{ date: '2026-11-10', pricePerLiter: '1200', fuelType: 'NAFTA_SUPER' }],
  },
  recentFuelLogs: [],
  recentMaintenance: [],
  alerts: [
    { kind: 'VTV', title: 'VTV vencida', dueDate: '2026-10-01', dueKm: null, severity: 'OVERDUE' },
  ],
};

describe('VehicleDetailPage', () => {
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;

  beforeEach(() => {
    localStorage.clear();
    const paramMap$ = new BehaviorSubject(convertToParamMap({ id: 'veh-1' }));

    TestBed.configureTestingModule({
      imports: [VehicleDetailPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: paramMap$.asObservable() } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('renders KPIs, alerts banner, and action buttons and notes forced NOMINAL lens', () => {
    viewContext.setLens('INCOME_PCT');
    const fixture = TestBed.createComponent(VehicleDetailPage);
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/bff/vehicles/veh-1/dashboard');
    expect(req.request.params.get('lens')).toBe('NOMINAL');
    req.flush(FULL_DASHBOARD);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="vehicle-alerts-banner"]')?.textContent).toContain(
      'VTV vencida',
    );
    expect(host.querySelector('[data-testid="kpi-km-per-liter"]')?.textContent).toContain('12,5');
    expect(host.querySelector('[data-testid="kpi-liters-per-100"]')?.textContent).toContain('8');
    expect(host.querySelector('[data-testid="vehicle-action-fuel"]')?.textContent).toContain(
      'Cargar combustible',
    );
    expect(host.querySelector('[data-testid="vehicle-action-maintenance"]')?.textContent).toContain(
      'Registrar service',
    );
    expect(host.querySelector('[data-testid="vehicle-lens-forced-badge"]')).not.toBeNull();
  });

  it('renders partial degradation notice when summary and logs are null', () => {
    const fixture = TestBed.createComponent(VehicleDetailPage);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/vehicles/veh-1/dashboard').flush({
      vehicle: FULL_DASHBOARD.vehicle,
      summary: null,
      recentFuelLogs: null,
      recentMaintenance: null,
      alerts: null,
    } satisfies VehicleDashboard);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="vehicle-summary-degraded"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="fuel-logs-degraded"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="maintenance-logs-degraded"]')).not.toBeNull();
  });
});
