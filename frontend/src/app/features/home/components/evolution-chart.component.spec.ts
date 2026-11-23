import { TestBed } from '@angular/core/testing';
import { buildDemoSeries } from '../data-access/home.fixtures';
import { Series } from '../data-access/home.models';
import { EvolutionChartComponent } from './evolution-chart.component';

describe('EvolutionChartComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('computes scale, proportional bar heights and balance line points from fixtures', () => {
    const series = buildDemoSeries({
      view: 'FAMILY',
      lens: 'NOMINAL',
      date: '2026-11-23',
    });

    const fixture = TestBed.createComponent(EvolutionChartComponent);
    fixture.componentRef.setInput('series', series);
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    const scale = cmp.chartScale();
    const points = cmp.computedPoints();

    expect(points).toHaveLength(6);
    expect(scale.min).toBe(0);
    expect(scale.max).toBeGreaterThan(3_200_000);

    // Highest income month (Nov: 3,200,000) has taller income bar than lowest (Jun: 2,650,000)
    const jun = points[0];
    const nov = points[5];
    expect(nov.incomeBar.height).toBeGreaterThan(jun.incomeBar.height);
    expect(jun.incomeBar.height).toBeGreaterThan(jun.expenseBar.height);

    // Bars sit squarely on zeroY baseline
    expect(jun.incomeBar.y + jun.incomeBar.height).toBeCloseTo(scale.zeroY, 1);
    expect(jun.expenseBar.y + jun.expenseBar.height).toBeCloseTo(scale.zeroY, 1);

    // SVG renders 6 income bars, 6 expense bars, and a 6-point balance polyline
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('[data-testid="evolution-income-bar"]')).toHaveLength(6);
    expect(host.querySelectorAll('[data-testid="evolution-expense-bar"]')).toHaveLength(6);
    expect(host.querySelectorAll('[data-testid="evolution-balance-dot"]')).toHaveLength(6);

    const polyline = host.querySelector('[data-testid="evolution-balance-line"]');
    expect(polyline?.getAttribute('points')?.split(' ')).toHaveLength(6);
  });

  it('handles negative balance values by shifting the zero baseline inside the plot area', () => {
    const deficitSeries: Series = {
      lens: 'NOMINAL',
      unit: 'ARS',
      granularity: 'MONTH',
      points: [
        {
          periodStart: '2026-10-01',
          label: 'Oct',
          income: '100000.0000',
          expense: '150000.0000',
          balance: '-50000.0000',
        },
        {
          periodStart: '2026-11-01',
          label: 'Nov',
          income: '200000.0000',
          expense: '100000.0000',
          balance: '100000.0000',
        },
      ],
    };

    const fixture = TestBed.createComponent(EvolutionChartComponent);
    fixture.componentRef.setInput('series', deficitSeries);
    fixture.detectChanges();

    const cmp = fixture.componentInstance;
    const scale = cmp.chartScale();
    const points = cmp.computedPoints();

    expect(scale.min).toBeLessThan(0);
    // Negative balance point sits below zeroY; positive balance sits above zeroY
    expect(points[0].balancePoint.y).toBeGreaterThan(scale.zeroY);
    expect(points[1].balancePoint.y).toBeLessThan(scale.zeroY);
  });

  it('re-expresses axis and active summary unit when lens changes to INCOME_PCT', () => {
    const pctSeries = buildDemoSeries({
      view: 'PERSONAL',
      lens: 'INCOME_PCT',
      date: '2026-11-23',
    });

    const fixture = TestBed.createComponent(EvolutionChartComponent);
    fixture.componentRef.setInput('series', pctSeries);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const summary = host.querySelector('[data-testid="evolution-active-summary"]');
    expect(summary?.textContent).toContain('%');
    expect(fixture.componentInstance.displayUnit()).toBe('%');
  });
});
