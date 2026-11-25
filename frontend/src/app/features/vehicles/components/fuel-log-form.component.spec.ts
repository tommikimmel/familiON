import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Account } from '../../../core/finance/finance.models';
import { FuelLogCreate, Vehicle } from '../data-access/vehicles.models';
import { FuelLogFormComponent } from './fuel-log-form.component';

const MOCK_VEHICLE: Vehicle = {
  id: 'veh-1',
  nickname: 'Cronos gris',
  brand: 'Fiat',
  model: 'Cronos',
  modelYear: 2022,
  licensePlate: 'AF 123 CD',
  primaryFuelType: 'NAFTA_PREMIUM',
  secondaryFuelType: null,
  currentOdometerKm: 45200,
  vtvExpirationDate: null,
  insuranceExpiration: null,
  insuranceCompany: null,
  notes: null,
  isActive: true,
  alerts: [],
};

const MOCK_ACCOUNTS: Account[] = [
  {
    id: 'acc-1',
    name: 'Débito Galicia',
    accountType: 'DEBIT_CARD',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '250000',
    ownerUserId: 'u-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

@Component({
  standalone: true,
  imports: [FuelLogFormComponent],
  template: `
    <fo-fuel-log-form
      [vehicle]="vehicle()"
      [accounts]="accounts"
      [defaultAccountId]="'acc-1'"
      [fieldErrors]="fieldErrors()"
      (submitted)="lastSubmitted.set($event)"
    />
  `,
})
class FuelLogFormHostComponent {
  readonly vehicle = signal<Vehicle | null>(MOCK_VEHICLE);
  readonly accounts = MOCK_ACCOUNTS;
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly lastSubmitted = signal<FuelLogCreate | null>(null);
}

describe('FuelLogFormComponent', () => {
  it('prefills odometer, primary fuel, fullTank=true, calculates 3rd value live, and submits', () => {
    const fixture = TestBed.createComponent(FuelLogFormHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const odometerInput = host.querySelector(
      '[data-testid="fuel-odometer-input"]',
    ) as HTMLInputElement;
    expect(odometerInput.value).toBe('45200');

    const switchEl = host.querySelector('[data-testid="fuel-full-tank-switch"]') as HTMLButtonElement;
    expect(switchEl.getAttribute('aria-checked')).toBe('true');

    const litersInput = host.querySelector('[data-testid="fuel-liters-input"]') as HTMLInputElement;
    const priceInput = host.querySelector('[data-testid="fuel-price-input"]') as HTMLInputElement;
    const totalInput = host.querySelector('[data-testid="fuel-total-input"]') as HTMLInputElement;

    litersInput.value = '40';
    litersInput.dispatchEvent(new Event('input'));
    priceInput.value = '1300';
    priceInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(totalInput.value).toBe('52000');
    expect(host.querySelector('[data-testid="fuel-computed-badge-totalAmount"]')).not.toBeNull();

    const formEl = host.querySelector('form') as HTMLFormElement;
    formEl.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const submitted = fixture.componentInstance.lastSubmitted();
    expect(submitted).not.toBeNull();
    expect(submitted?.odometerKm).toBe(45200);
    expect(submitted?.fuelType).toBe('NAFTA_PREMIUM');
    expect(submitted?.liters).toBe('40');
    expect(submitted?.pricePerLiter).toBe('1300');
    expect(submitted?.fullTank).toBe(true);
    expect(submitted?.accountId).toBe('acc-1');
    expect(submitted?.currencyCode).toBe('ARS');
  });

  it('shows 422 odometer error inline on the odometer field', () => {
    const fixture = TestBed.createComponent(FuelLogFormHostComponent);
    fixture.componentInstance.fieldErrors.set({
      odometerKm: 'El odómetro no puede ser menor a 45.200 km.',
    });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="fuel-odometer-error"]')?.textContent?.trim()).toBe(
      'El odómetro no puede ser menor a 45.200 km.',
    );
  });
});
