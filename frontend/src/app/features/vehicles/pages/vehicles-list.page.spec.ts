import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Me } from '../../../core/api/models/me.model';
import { SessionStore } from '../../../core/state/session.store';
import { Vehicle } from '../data-access/vehicles.models';
import { VehiclesListPage } from './vehicles-list.page';

function mockMe(role: 'ADULTO' | 'HIJO'): Me {
  return {
    id: 'u-1',
    email: 'test@familion.test',
    fullName: 'Test User',
    role,
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'SYSTEM',
    accentColor: '#0f766e',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
  };
}

const SAMPLE_VEHICLE: Vehicle = {
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
  alerts: [
    {
      kind: 'SERVICE_KM',
      title: 'Service vencido',
      dueDate: null,
      dueKm: 45000,
      severity: 'OVERDUE',
    },
  ],
};

describe('VehiclesListPage', () => {
  let httpMock: HttpTestingController;
  let session: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VehiclesListPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows empty state with CTA for ADULTO and hides CTA for HIJO', async () => {
    session.setMe(mockMe('ADULTO'));
    const fixture = TestBed.createComponent(VehiclesListPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/vehicles').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="vehicles-empty-add-btn"]')).not.toBeNull();

    session.setMe(mockMe('HIJO'));
    fixture.detectChanges();
    expect(host.querySelector('[data-testid="vehicles-empty-add-btn"]')).toBeNull();
    expect(host.querySelector('[data-testid="vehicles-add-btn"]')).toBeNull();
  });

  it('renders vehicle cards with alerts when vehicles exist', async () => {
    session.setMe(mockMe('ADULTO'));
    const fixture = TestBed.createComponent(VehiclesListPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/vehicles').flush([SAMPLE_VEHICLE]);
    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Cronos gris');
    expect(host.textContent).toContain('AF 123 CD');
    expect(host.querySelector('[data-testid="vehicle-alert-OVERDUE"]')).not.toBeNull();
  });
});
