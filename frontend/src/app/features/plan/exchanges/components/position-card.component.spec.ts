import { TestBed } from '@angular/core/testing';
import { CurrencyPosition } from '../data-access/exchanges.models';
import { PositionCardComponent } from './position-card.component';

function buildPosition(overrides: Partial<CurrencyPosition> = {}): CurrencyPosition {
  return {
    currencyCode: 'USD',
    units: '800.0000',
    weightedAvgRate: '1185.000000',
    totalCostBase: '948000.0000',
    currentRate: '1220.000000',
    currentValueBase: '976000.0000',
    unrealizedResultBase: '28000.0000',
    unrealizedResultPct: '2.9536',
    ...overrides,
  };
}

describe('PositionCardComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders a position with positive unrealized result in green', () => {
    const fixture = TestBed.createComponent(PositionCardComponent);
    fixture.componentRef.setInput('position', buildPosition());
    fixture.componentRef.setInput('baseCurrency', 'ARS');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="position-units"]')?.textContent?.trim()).toBe('USD 800');
    expect(host.querySelector('[data-testid="position-avg-rate"]')?.textContent).toContain(
      'Compraste a $1.185 promedio',
    );
    expect(host.querySelector('[data-testid="position-current-rate"]')?.textContent?.trim()).toBe(
      '$1.220',
    );

    const badge = host.querySelector('[data-testid="position-result-badge"]') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('+3%');
    expect(badge.classList.contains('text-success')).toBe(true);

    const amount = host.querySelector('[data-testid="position-result-amount"]') as HTMLElement;
    expect(amount.textContent?.trim()).toContain('+');
    expect(amount.classList.contains('text-success')).toBe(true);
  });

  it('renders a position with negative unrealized result in red', () => {
    const fixture = TestBed.createComponent(PositionCardComponent);
    fixture.componentRef.setInput(
      'position',
      buildPosition({
        currentRate: '1120.000000',
        currentValueBase: '896000.0000',
        unrealizedResultBase: '-52000.0000',
        unrealizedResultPct: '-5.4852',
      }),
    );
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const badge = host.querySelector('[data-testid="position-result-badge"]') as HTMLElement;
    expect(badge.textContent?.trim()).toBe('-5,5%');
    expect(badge.classList.contains('text-danger')).toBe(true);

    const amount = host.querySelector('[data-testid="position-result-amount"]') as HTMLElement;
    expect(amount.textContent?.trim()).toContain('-');
    expect(amount.classList.contains('text-danger')).toBe(true);
  });

  it('renders graceful fallbacks when current rate is unavailable', () => {
    const fixture = TestBed.createComponent(PositionCardComponent);
    fixture.componentRef.setInput(
      'position',
      buildPosition({
        currentRate: null,
        currentValueBase: null,
        unrealizedResultBase: null,
        unrealizedResultPct: null,
      }),
    );
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="position-result-badge"]')).toBeNull();
    expect(host.querySelector('[data-testid="position-current-rate"]')?.textContent?.trim()).toBe(
      'Sin cotización',
    );
    expect(host.querySelector('[data-testid="position-result-amount"]')?.textContent?.trim()).toBe(
      '—',
    );
  });
});
