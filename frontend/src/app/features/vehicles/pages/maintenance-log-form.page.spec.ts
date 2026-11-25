import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { MaintenanceLogFormPage } from './maintenance-log-form.page';

describe('MaintenanceLogFormPage', () => {
  let httpMock: HttpTestingController;
  let router: Router;
  let financeEvents: FinanceEvents;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [MaintenanceLogFormPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'veh-1' }) } },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    financeEvents = TestBed.inject(FinanceEvents);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('displays 422 odometer error on the field when backend rejects odometer', async () => {
    const fixture = TestBed.createComponent(MaintenanceLogFormPage);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/api/accounts').flush([]);
    httpMock.expectOne('/bff/api/vehicles/veh-1').flush({
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
    });

    await Promise.resolve();
    await Promise.resolve();
    await fixture.whenStable();
    fixture.detectChanges();
    TestBed.flushEffects();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const descInput = host.querySelector(
      '[data-testid="maintenance-description-input"]',
    ) as HTMLInputElement;
    descInput.value = 'Service 45.000 km';
    descInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const postReq = httpMock.expectOne('/bff/api/vehicles/veh-1/maintenance-logs');
    postReq.flush(
      {
        status: 422,
        detail: 'El odómetro ingresado es menor al último registrado.',
      },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    await fixture.whenStable();
    fixture.detectChanges();

    expect(
      host.querySelector('[data-testid="maintenance-odometer-error"]')?.textContent?.trim(),
    ).toBe('El odómetro ingresado es menor al último registrado.');
  });

  it('submits zero-cost maintenance without emitting transaction-created and navigates back', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const emitSpy = vi.spyOn(financeEvents, 'emit');

    const fixture = TestBed.createComponent(MaintenanceLogFormPage);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/api/accounts').flush([]);
    httpMock.expectOne('/bff/api/vehicles/veh-1').flush({
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
    });

    await Promise.resolve();
    await Promise.resolve();
    await fixture.whenStable();
    fixture.detectChanges();
    TestBed.flushEffects();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const descInput = host.querySelector(
      '[data-testid="maintenance-description-input"]',
    ) as HTMLInputElement;
    descInput.value = 'Revisión en garantía';
    descInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const postReq = httpMock.expectOne('/bff/api/vehicles/veh-1/maintenance-logs');
    expect(postReq.request.body.accountId).toBeUndefined();
    postReq.flush({
      id: 'm-1',
      maintenanceType: 'OIL_CHANGE',
      maintenanceDate: '2026-11-24',
      odometerKm: 45200,
      description: 'Revisión en garantía',
      costAmount: '0',
      currencyCode: 'ARS',
      transactionId: null,
      user: { id: 'u-1', fullName: 'Ana' },
    });

    await fixture.whenStable();
    expect(emitSpy).toHaveBeenCalledWith('vehicle-changed');
    expect(emitSpy).not.toHaveBeenCalledWith('transaction-created');
    expect(navigateSpy).toHaveBeenCalledWith(['/vehicles', 'veh-1']);
  });
});
