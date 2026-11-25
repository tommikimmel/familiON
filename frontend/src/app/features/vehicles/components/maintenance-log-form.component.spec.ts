import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Account } from '../../../core/finance/finance.models';
import { MaintenanceLogCreate, Vehicle } from '../data-access/vehicles.models';
import { MaintenanceLogFormComponent } from './maintenance-log-form.component';

const MOCK_VEHICLE: Vehicle = {
  id: 'veh-1',
  nickname: 'Cronos gris',
  brand: 'Fiat',
  model: 'Cronos',
  modelYear: 2022,
  licensePlate: 'AF 123 CD',
  primaryFuelType: 'NAFTA_SUPER',
  secondaryFuelType: null,
  currentOdometerKm: 45000,
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
    name: 'Banco Galicia',
    accountType: 'BANK',
    currencyCode: 'ARS',
    initialBalance: '0',
    currentBalance: '500000',
    ownerUserId: 'u-1',
    creditLimit: null,
    closingDay: null,
    dueDay: null,
    isActive: true,
  },
];

@Component({
  standalone: true,
  imports: [MaintenanceLogFormComponent],
  template: `
    <fo-maintenance-log-form
      [vehicle]="vehicle()"
      [accounts]="accounts"
      [defaultAccountId]="defaultAccountId()"
      [fieldErrors]="fieldErrors()"
      (submitted)="lastSubmitted.set($event)"
    />
  `,
})
class MaintenanceFormHostComponent {
  readonly vehicle = signal<Vehicle | null>(MOCK_VEHICLE);
  readonly accounts = MOCK_ACCOUNTS;
  readonly defaultAccountId = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly lastSubmitted = signal<MaintenanceLogCreate | null>(null);
}

describe('MaintenanceLogFormComponent', () => {
  it('requires accountId ONLY when cost > 0', () => {
    const fixture = TestBed.createComponent(MaintenanceFormHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const formComp = fixture.debugElement.children[0]
      .componentInstance as MaintenanceLogFormComponent;

    formComp.form.controls.description.setValue('Control de niveles en garantía');
    formComp.form.controls.costAmount.setValue('0');
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="maintenance-account-group"]')).toBeNull();
    expect(formComp.form.valid).toBe(true);

    // Submit with cost 0 -> omits accountId.
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.lastSubmitted()?.costAmount).toBe('0');
    expect(fixture.componentInstance.lastSubmitted()?.accountId).toBeUndefined();

    // Now set cost > 0 without selecting account -> form becomes invalid.
    formComp.form.controls.costAmount.setValue('85000');
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="maintenance-account-group"]')).not.toBeNull();
    expect(formComp.form.controls.accountId.hasError('required')).toBe(true);
    expect(formComp.form.invalid).toBe(true);

    // Selecting an account makes form valid and submits accountId.
    formComp.form.controls.accountId.setValue('acc-1');
    fixture.detectChanges();
    expect(formComp.form.valid).toBe(true);

    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(fixture.componentInstance.lastSubmitted()?.costAmount).toBe('85000');
    expect(fixture.componentInstance.lastSubmitted()?.accountId).toBe('acc-1');

    // Dropping cost back to 0 clears account requirement again.
    formComp.form.controls.accountId.setValue('');
    formComp.form.controls.costAmount.setValue('0');
    fixture.detectChanges();
    expect(formComp.form.valid).toBe(true);
  });

  it('applies oil change suggestion (+10.000 km / 12 months)', () => {
    const fixture = TestBed.createComponent(MaintenanceFormHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const formComp = fixture.debugElement.children[0]
      .componentInstance as MaintenanceLogFormComponent;

    formComp.form.controls.maintenanceDate.setValue('2026-11-24');
    fixture.detectChanges();

    const suggestionBtn = host.querySelector(
      '[data-testid="maintenance-apply-suggestion"]',
    ) as HTMLButtonElement;
    expect(suggestionBtn).not.toBeNull();
    suggestionBtn.click();
    fixture.detectChanges();

    expect(formComp.form.controls.nextDueKm.value).toBe('55000');
    expect(formComp.form.controls.nextDueDate.value).toBe('2027-11-24');
  });
});
