import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { ToastService } from '../../../shared/ui';
import { FuelLogFormPage } from './fuel-log-form.page';

describe('FuelLogFormPage', () => {
  let httpMock: HttpTestingController;
  let router: Router;
  let financeEvents: FinanceEvents;
  let toast: ToastService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [FuelLogFormPage],
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
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('submits fuel log, emits vehicle-changed and transaction-created, shows toast, and navigates back', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const emitSpy = vi.spyOn(financeEvents, 'emit');

    const fixture = TestBed.createComponent(FuelLogFormPage);
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/api/accounts').flush([
      {
        id: 'acc-1',
        name: 'Débito',
        accountType: 'DEBIT_CARD',
        currencyCode: 'ARS',
        initialBalance: '0',
        currentBalance: '100000',
        ownerUserId: null,
        creditLimit: null,
        closingDay: null,
        dueDay: null,
        isActive: true,
      },
    ]);
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
    const litersInput = host.querySelector('[data-testid="fuel-liters-input"]') as HTMLInputElement;
    const priceInput = host.querySelector('[data-testid="fuel-price-input"]') as HTMLInputElement;
    litersInput.value = '40';
    litersInput.dispatchEvent(new Event('input'));
    priceInput.value = '1200';
    priceInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const postReq = httpMock.expectOne('/bff/api/vehicles/veh-1/fuel-logs');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body.odometerKm).toBe(45200);
    expect(postReq.request.body.accountId).toBe('acc-1');
    postReq.flush({
      id: 'fuel-99',
      fuelDate: '2026-11-24',
      odometerKm: 45200,
      fuelType: 'NAFTA_SUPER',
      liters: '40',
      pricePerLiter: '1200',
      totalAmount: '48000',
      currencyCode: 'ARS',
      fullTank: true,
      stationName: null,
      notes: null,
      transactionId: 'tx-99',
      user: { id: 'u-1', fullName: 'Ana' },
    });

    await fixture.whenStable();
    expect(emitSpy).toHaveBeenCalledWith('vehicle-changed');
    expect(emitSpy).toHaveBeenCalledWith('transaction-created');
    expect(toast.current()?.kind).toBe('success');
    expect(navigateSpy).toHaveBeenCalledWith(['/vehicles', 'veh-1']);
  });
});
