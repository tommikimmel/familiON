import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Series } from '../data-access/analytics.models';
import { AnalyticsEvolutionChartComponent } from './analytics-evolution-chart.component';

const MOCK_SERIES_NOMINAL: Series = {
  lens: 'NOMINAL',
  unit: 'ARS',
  granularity: 'MONTH',
  points: [
    { periodStart: '2026-09-01', label: 'Sep', income: '400000', expense: '300000', balance: '100000' },
    { periodStart: '2026-10-01', label: 'Oct', income: '450000', expense: '320000', balance: '130000' },
    { periodStart: '2026-11-01', label: 'Nov', income: '500000', expense: '350000', balance: '150000' },
  ],
};

describe('AnalyticsEvolutionChartComponent', () => {
  let fixture: ComponentFixture<AnalyticsEvolutionChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsEvolutionChartComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsEvolutionChartComponent);
  });

  it('renders bars, balance line, and updates tooltip on point selection', () => {
    fixture.componentRef.setInput('series', MOCK_SERIES_NOMINAL);
    fixture.componentRef.setInput('granularity', 'MONTH');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('[data-testid="analytics-evolution-income-bar"]')).toHaveLength(3);
    expect(el.querySelectorAll('[data-testid="analytics-evolution-expense-bar"]')).toHaveLength(3);
    expect(el.querySelector('[data-testid="analytics-evolution-balance-line"]')).not.toBeNull();

    // Defaults to last point (Nov)
    const tooltip = el.querySelector('[data-testid="analytics-evolution-tooltip"]');
    expect(tooltip?.textContent).toContain('Nov');

    // Click first slot (Sep)
    const slots = el.querySelectorAll('[data-testid="analytics-evolution-slot"]');
    slots[0].dispatchEvent(new MouseEvent('click'));
    fixture.detectChanges();

    expect(el.querySelector('[data-testid="analytics-evolution-tooltip"]')?.textContent).toContain(
      'Sep',
    );
  });

  it('re-expresses values when lens changes to INCOME_PCT', () => {
    fixture.componentRef.setInput('series', {
      ...MOCK_SERIES_NOMINAL,
      lens: 'INCOME_PCT',
      unit: '%',
      points: [
        { periodStart: '2026-11-01', label: 'Nov', income: '100', expense: '70', balance: '30' },
      ],
    });
    fixture.detectChanges();

    const tooltip = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="analytics-evolution-tooltip"]',
    );
    expect(tooltip?.textContent).toContain('70%');
    expect(tooltip?.textContent).toContain('30%');
  });
});
