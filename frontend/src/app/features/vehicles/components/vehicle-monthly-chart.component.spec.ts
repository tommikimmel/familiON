import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { VehicleMonthlyPoint } from '../data-access/vehicles.models';
import { VehicleMonthlyChartComponent } from './vehicle-monthly-chart.component';

@Component({
  standalone: true,
  imports: [VehicleMonthlyChartComponent],
  template: `<fo-vehicle-monthly-chart [monthly]="monthly()" [unit]="'ARS'" />`,
})
class MonthlyChartHostComponent {
  readonly monthly = signal<VehicleMonthlyPoint[]>([
    { month: '2026-10', fuelCost: '120000', maintenanceCost: '30000', km: 1100 },
    { month: '2026-11', fuelCost: '150000', maintenanceCost: '0', km: 1400 },
  ]);
}

describe('VehicleMonthlyChartComponent', () => {
  it('renders stacked fuel and maintenance bars and updates tooltip on slot selection', () => {
    const fixture = TestBed.createComponent(MonthlyChartHostComponent);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('[data-testid="vehicle-monthly-fuel-bar"]')).toHaveLength(2);
    expect(host.querySelectorAll('[data-testid="vehicle-monthly-maintenance-bar"]')).toHaveLength(1);

    const tooltip = host.querySelector('[data-testid="vehicle-monthly-tooltip"]');
    expect(tooltip?.textContent).toContain("Nov '26");

    const slots = host.querySelectorAll('[data-testid="vehicle-monthly-slot"]');
    (slots[0] as SVGElement).dispatchEvent(new MouseEvent('click'));
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="vehicle-monthly-tooltip"]')?.textContent).toContain(
      "Oct '26",
    );
  });
});
