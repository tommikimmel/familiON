import { TestBed } from '@angular/core/testing';
import { buildDemoHomePayload } from '../data-access/home.fixtures';
import { AnalyticsSummary } from '../data-access/home.models';
import { HeroBalanceComponent } from './hero-balance.component';

describe('HeroBalanceComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders view title, badge, income/expense and animated balance in currency', () => {
    const summary = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-11-23',
    }).summary as AnalyticsSummary;

    const fixture = TestBed.createComponent(HeroBalanceComponent);
    fixture.componentRef.setInput('summary', summary);
    fixture.componentRef.setInput('view', 'FAMILY');
    fixture.componentRef.setInput('countUpDuration', 0);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const text = host.textContent ?? '';

    expect(text).toContain('Balance familiar');
    expect(host.querySelector('[data-testid="hero-savings-chip"]')?.textContent).toContain('% real');
    expect(text).toContain('Ingresos');
    expect(text).toContain('Gastos');
    expect(host.querySelector('[data-testid="hero-balance-amount"]')?.textContent).toContain('$');
    expect(host.querySelector('[data-testid="hero-approximated-note"]')).toBeNull();
  });

  it('displays percentages when lens is INCOME_PCT', () => {
    const summary = buildDemoHomePayload({
      view: 'PERSONAL',
      lens: 'INCOME_PCT',
      granularity: 'MONTH',
      date: '2026-11-23',
    }).summary as AnalyticsSummary;

    const fixture = TestBed.createComponent(HeroBalanceComponent);
    fixture.componentRef.setInput('summary', summary);
    fixture.componentRef.setInput('countUpDuration', 0);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const balanceText =
      host.querySelector('[data-testid="hero-balance-amount"]')?.textContent?.trim() ?? '';
    const incomeText =
      host.querySelector('[data-testid="hero-income-amount"]')?.textContent?.trim() ?? '';
    const expenseText =
      host.querySelector('[data-testid="hero-expense-amount"]')?.textContent?.trim() ?? '';

    expect(balanceText).toContain('%');
    expect(incomeText).toBe('100%');
    expect(expenseText).toBe('75%');
  });

  it('shows inflation approximation notice when lensApproximated is true', () => {
    const summary: AnalyticsSummary = {
      ...(buildDemoHomePayload().summary as AnalyticsSummary),
      lens: 'CONSTANT_BASE',
      lensApproximated: true,
    };

    const fixture = TestBed.createComponent(HeroBalanceComponent);
    fixture.componentRef.setInput('summary', summary);
    fixture.componentRef.setInput('countUpDuration', 0);
    fixture.detectChanges();

    const note = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="hero-approximated-note"]',
    );
    expect(note?.textContent).toContain(
      'Valores aproximados: falta el índice de inflación más reciente',
    );
  });
});
