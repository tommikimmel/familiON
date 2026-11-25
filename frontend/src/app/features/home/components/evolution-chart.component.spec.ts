import { TestBed } from '@angular/core/testing';
import { buildDemoSeries } from '../data-access/home.fixtures';
import { Series } from '../data-access/home.models';
import { EvolutionChartComponent } from './evolution-chart.component';

function render(series: Series, lens: Series['lens'] = series.lens) {
  const fixture = TestBed.createComponent(EvolutionChartComponent);
  fixture.componentRef.setInput('series', series);
  fixture.componentRef.setInput('lens', lens);
  fixture.detectChanges();
  return fixture;
}

describe('EvolutionChartComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders one expense column per month scaled to the highest month', () => {
    const series = buildDemoSeries({ view: 'FAMILY', lens: 'NOMINAL', date: '2026-11-23' });
    const fixture = render(series);

    const columns = fixture.componentInstance.columns();
    expect(columns).toHaveLength(6);
    const tallest = Math.max(...columns.map((c) => c.height));
    expect(tallest).toBe(96);
    expect(columns.every((c) => c.height >= 14)).toBe(true);

    const host = fixture.nativeElement as HTMLElement;
    const bars = host.querySelectorAll('[data-testid="evolution-expense-bar"]');
    expect(bars).toHaveLength(6);
    expect(bars[5].classList).toContain('is-current');
    expect(bars[0].classList).not.toContain('is-current');
    expect(host.textContent).toContain('Evolución de Gastos (6 meses)');
    expect(host.textContent).toContain('Nominal');
  });

  it('marks past months as inflation adjusted with the constant base lens', () => {
    const series = buildDemoSeries({ view: 'FAMILY', lens: 'CONSTANT_BASE', date: '2026-11-23' });
    const fixture = render(series, 'CONSTANT_BASE');

    const host = fixture.nativeElement as HTMLElement;
    const bars = host.querySelectorAll('[data-testid="evolution-expense-bar"]');
    expect(bars[0].classList).toContain('is-ipc');
    expect(host.textContent).toContain('Meses pasados traídos a plata de hoy (IPC)');
  });

  it('labels values in percent when the lens is INCOME_PCT', () => {
    const series = buildDemoSeries({ view: 'PERSONAL', lens: 'INCOME_PCT', date: '2026-11-23' });
    const fixture = render(series, 'INCOME_PCT');

    expect(fixture.componentInstance.displayUnit()).toBe('%');
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="evolution-value"]')?.textContent).toContain('%');
  });
});
