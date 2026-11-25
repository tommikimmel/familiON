import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { Vehicle } from './vehicles.models';
import { VehiclesStore } from './vehicles.store';

const MOCK_VEHICLE: Vehicle = {
  id: 'veh-1',
  nickname: 'Cronos gris',
  brand: 'Fiat',
  model: 'Cronos',
  modelYear: 2022,
  licensePlate: 'AF 123 CD',
  primaryFuelType: 'NAFTA_SUPER',
  secondaryFuelType: null,
  currentOdometerKm: 45200,
  vtvExpirationDate: '2027-03-15',
  insuranceExpiration: '2026-12-31',
  insuranceCompany: 'La Caja',
  notes: null,
  isActive: true,
  alerts: [
    {
      kind: 'VTV',
      title: 'VTV próxima',
      dueDate: '2027-03-15',
      dueKm: null,
      severity: 'INFO',
    },
  ],
};

describe('VehiclesStore', () => {
  let store: VehiclesStore;
  let httpMock: HttpTestingController;
  let financeEvents: FinanceEvents;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(VehiclesStore);
    httpMock = TestBed.inject(HttpTestingController);
    financeEvents = TestBed.inject(FinanceEvents);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads vehicles from /bff/api/vehicles', async () => {
    const promise = store.load();
    expect(store.loading()).toBe(true);

    const req = httpMock.expectOne('/bff/api/vehicles');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_VEHICLE]);

    await promise;
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
    expect(store.vehicles()).toEqual([MOCK_VEHICLE]);
  });

  it('creates, updates and removes vehicles while emitting vehicle-changed', async () => {
    const createPromise = store.create({
      nickname: 'Cronos gris',
      brand: 'Fiat',
      model: 'Cronos',
      modelYear: 2022,
      licensePlate: 'AF 123 CD',
      primaryFuelType: 'NAFTA_SUPER',
      currentOdometerKm: 45200,
    });
    const postReq = httpMock.expectOne('/bff/api/vehicles');
    expect(postReq.request.method).toBe('POST');
    postReq.flush(MOCK_VEHICLE);

    const created = await createPromise;
    expect(created.id).toBe('veh-1');
    expect(store.vehicles()).toHaveLength(1);
    expect(financeEvents.last()?.type).toBe('vehicle-changed');

    const patchPromise = store.update('veh-1', { nickname: 'Cronos familiar' });
    const patchReq = httpMock.expectOne('/bff/api/vehicles/veh-1');
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush({ ...MOCK_VEHICLE, nickname: 'Cronos familiar' });

    await patchPromise;
    expect(store.vehicles()[0].nickname).toBe('Cronos familiar');

    const deletePromise = store.remove('veh-1');
    const delReq = httpMock.expectOne('/bff/api/vehicles/veh-1');
    expect(delReq.request.method).toBe('DELETE');
    delReq.flush(null, { status: 204, statusText: 'No Content' });

    await deletePromise;
    expect(store.vehicles()).toHaveLength(0);
  });
});
