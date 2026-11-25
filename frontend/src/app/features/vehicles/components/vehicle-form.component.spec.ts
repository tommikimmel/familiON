import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Vehicle, VehicleCreate } from '../data-access/vehicles.models';
import { VehicleFormComponent } from './vehicle-form.component';

@Component({
  standalone: true,
  imports: [VehicleFormComponent],
  template: `
    <fo-vehicle-form
      [vehicle]="vehicle()"
      [fieldErrors]="fieldErrors()"
      (submitted)="lastSubmitted.set($event)"
    />
  `,
})
class VehicleFormHostComponent {
  readonly vehicle = signal<Vehicle | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly lastSubmitted = signal<VehicleCreate | null>(null);
}

describe('VehicleFormComponent', () => {
  it('validates year range (1900-2100) and required license plate', () => {
    const fixture = TestBed.createComponent(VehicleFormHostComponent);
    fixture.detectChanges();

    const formComp = fixture.debugElement.children[0].componentInstance as VehicleFormComponent;

    formComp.form.controls.nickname.setValue('Auto familiar');
    formComp.form.controls.brand.setValue('Toyota');
    formComp.form.controls.model.setValue('Corolla');
    formComp.form.controls.modelYear.setValue('1899');
    formComp.form.controls.licensePlate.setValue('   ');
    formComp.form.controls.currentOdometerKm.setValue('35000');
    fixture.detectChanges();

    expect(formComp.form.controls.modelYear.hasError('modelYear')).toBe(true);
    expect(formComp.form.controls.licensePlate.hasError('required')).toBe(true);
    expect(formComp.form.invalid).toBe(true);

    formComp.form.controls.modelYear.setValue('2101');
    expect(formComp.form.controls.modelYear.hasError('modelYear')).toBe(true);

    formComp.form.controls.modelYear.setValue('2023');
    formComp.form.controls.licensePlate.setValue('af 987 xy');
    fixture.detectChanges();

    expect(formComp.form.valid).toBe(true);

    const formEl = (fixture.nativeElement as HTMLElement).querySelector('form') as HTMLFormElement;
    formEl.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.componentInstance.lastSubmitted()).toEqual({
      nickname: 'Auto familiar',
      brand: 'Toyota',
      model: 'Corolla',
      modelYear: 2023,
      licensePlate: 'AF 987 XY',
      primaryFuelType: 'NAFTA_SUPER',
      secondaryFuelType: null,
      currentOdometerKm: 35000,
      vtvExpirationDate: null,
      insuranceExpiration: null,
      insuranceCompany: null,
      notes: null,
    });
  });

  it('displays server 422 odometer error inline on the odometer field', () => {
    const fixture = TestBed.createComponent(VehicleFormHostComponent);
    fixture.componentInstance.fieldErrors.set({
      currentOdometerKm: 'El odómetro no puede ser menor al último registrado.',
    });
    fixture.detectChanges();

    const errorEl = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="vehicle-odometer-error"]',
    );
    expect(errorEl?.textContent?.trim()).toBe(
      'El odómetro no puede ser menor al último registrado.',
    );
  });
});
