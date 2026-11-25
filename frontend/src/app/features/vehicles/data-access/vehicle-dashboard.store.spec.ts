import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { VehicleDashboardStore, resolveVehicleLens } from './vehicle-dashboard.store';
import { VehicleDashboard } from './vehicles.models';

const MOCK_DASHBOARD: VehicleDashboard = {
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
    distanceKm: 12000,
    totalLiters: '950.0000',
    avgKmPerLiter: '12.6300',
    avgLitersPer100Km: '7.9200',
    costPerKm: '135.5000',
    totalCost: '1626000.0000',
    fuelCost: '1126000.0000',
    maintenanceCost: '500000.0000',
    lensApproximated: false,
    monthly: [
      { month: '2026-10', fuelCost: '120000', maintenanceCost: '0', km: 1100 },
      { month: '2026-11', fuelCost: '135000', maintenanceCost: '85000', km: 1250 },
    ],
    pricePerLiterTrend: [
      { date: '2026-10-05', pricePerLiter: '1150', fuelType: 'NAFTA_SUPER' },
      { date: '2026-11-10', pricePerLiter: '1220', fuelType: 'NAFTA_SUPER' },
    ],
  },
  recentFuelLogs: [
    {
      id: 'fuel-1',
      fuelDate: '2026-11-10',
      odometerKm: 45200,
      fuelType: 'NAFTA_SUPER',
      liters: '40.00',
      pricePerLiter: '1220.00',
      totalAmount: '48800.00',
      currencyCode: 'ARS',
      fullTank: true,
      stationName: 'YPF Centro',
      notes: null,
      transactionId: 'tx-1',
      user: { id: 'u-1', fullName: 'Ana' },
    },
  ],
  recentMaintenance: [
    {
      id: 'maint-1',
      maintenanceType: 'OIL_CHANGE',
      maintenanceDate: '2026-11-01',
      odometerKm: 44500,
      description: 'Cambio de aceite y filtro',
      workshopName: 'Lubricentro Sur',
      costAmount: '85000.00',
      currencyCode: 'ARS',
      accountId: 'acc-1',
      nextDueKm: 54500,
      nextDueDate: '2027-11-01',
      notes: undefined,
      transactionId: 'tx-2',
      user: { id: 'u-1', fullName: 'Ana' },
    },
  ],
  alerts: [
    { kind: 'VTV', title: 'VTV próxima', dueDate: '2026-12-10', dueKm: null, severity: 'WARNING' },
    {
      kind: 'SERVICE_KM',
      title: 'Service vencido',
      dueDate: null,
      dueKm: 45000,
      severity: 'OVERDUE',
    },
  ],
};

describe('VehicleDashboardStore', () => {
  let httpMock: HttpTestingController;
  let viewContext: ViewContextStore;
  let financeEvents: FinanceEvents;

  function setup(): VehicleDashboardStore {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [VehicleDashboardStore, provideHttpClient(), provideHttpClientTesting()],
    });

    httpMock = TestBed.inject(HttpTestingController);
    viewContext = TestBed.inject(ViewContextStore);
    financeEvents = TestBed.inject(FinanceEvents);
    return TestBed.inject(VehicleDashboardStore);
  }

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('forces NOMINAL lens when ViewContextStore lens is INCOME_PCT', () => {
    expect(resolveVehicleLens('INCOME_PCT')).toBe('NOMINAL');
    expect(resolveVehicleLens('CONSTANT_BASE')).toBe('CONSTANT_BASE');
    expect(resolveVehicleLens('REFERENCE_CURRENCY')).toBe('REFERENCE_CURRENCY');

    const store = setup();
    viewContext.setLens('INCOME_PCT');
    store.setVehicleId('veh-1');
    TestBed.flushEffects();

    const req = httpMock.expectOne((r) => r.url === '/bff/vehicles/veh-1/dashboard');
    expect(req.request.params.get('lens')).toBe('NOMINAL');
    expect(store.effectiveLens()).toBe('NOMINAL');
    expect(store.lensForcedNominal()).toBe(true);

    req.flush(MOCK_DASHBOARD);

    expect(store.loading()).toBe(false);
    expect(store.alerts()?.[0].severity).toBe('OVERDUE');
    expect(store.alerts()?.[1].severity).toBe('WARNING');
  });

  it('passes through non-INCOME_PCT lenses and handles null degraded sections safely', () => {
    const store = setup();
    viewContext.setLens('CONSTANT_BASE');
    store.setVehicleId('veh-1');
    TestBed.flushEffects();

    const req = httpMock.expectOne((r) => r.url === '/bff/vehicles/veh-1/dashboard');
    expect(req.request.params.get('lens')).toBe('CONSTANT_BASE');
    expect(store.lensForcedNominal()).toBe(false);

    req.flush({
      vehicle: MOCK_DASHBOARD.vehicle,
      summary: null,
      recentFuelLogs: null,
      recentMaintenance: [],
      alerts: null,
    } satisfies VehicleDashboard);

    expect(store.vehicle()).toEqual(MOCK_DASHBOARD.vehicle);
    expect(store.summary()).toBeNull();
    expect(store.recentFuelLogs()).toBeNull();
    expect(store.recentMaintenance()).toEqual([]);
    expect(store.alerts()).toBeNull();
    expect(store.hasDegradedSections()).toBe(true);
    expect(store.error()).toBeNull();
  });

  it('sets notFound when dashboard returns 404', () => {
    const store = setup();
    store.setVehicleId('missing-id');
    TestBed.flushEffects();

    const req = httpMock.expectOne((r) => r.url === '/bff/vehicles/missing-id/dashboard');
    req.flush({ status: 404, detail: 'Vehicle not found.' }, { status: 404, statusText: 'Not Found' });

    expect(store.notFound()).toBe(true);
    expect(store.error()).toBeNull();
    expect(store.dashboard()).toBeNull();
  });

  it('deletes a fuel log, emits finance events, and triggers dashboard reload', async () => {
    const store = setup();
    store.setVehicleId('veh-1');
    TestBed.flushEffects();

    httpMock.expectOne((r) => r.url === '/bff/vehicles/veh-1/dashboard').flush(MOCK_DASHBOARD);

    const delPromise = store.deleteFuelLog('fuel-1');
    const delReq = httpMock.expectOne('/bff/api/vehicles/veh-1/fuel-logs/fuel-1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null, { status: 204, statusText: 'No Content' });

    await delPromise;
    expect(financeEvents.last()?.type).toBe('transaction-deleted');
    expect(store.recentFuelLogs()).toEqual([]);

    TestBed.flushEffects();
    httpMock
      .expectOne((r) => r.url === '/bff/vehicles/veh-1/dashboard')
      .flush({ ...MOCK_DASHBOARD, recentFuelLogs: [] });
  });
});
