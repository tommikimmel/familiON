import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { buildDemoHomePayload } from '../data-access/home.fixtures';
import { TransactionItem } from '../data-access/home.models';
import { RecentListComponent } from './recent-list.component';

describe('RecentListComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
  });

  it('renders Paid by label, Cuota 2/6 badge, signed amounts and link to /transactions', () => {
    const transactions = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-11-23',
    }).recentTransactions as TransactionItem[];

    const fixture = TestBed.createComponent(RecentListComponent);
    fixture.componentRef.setInput('transactions', transactions);
    fixture.componentRef.setInput('lens', 'NOMINAL');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('li')).toHaveLength(10);

    const paidByBadges = Array.from(
      host.querySelectorAll('[data-testid="paid-by-label"]'),
    ).map((el) => el.textContent?.trim());
    expect(paidByBadges.some((label) => label?.includes('Pagado por Carlos García'))).toBe(true);

    const installmentBadges = Array.from(
      host.querySelectorAll('[data-testid="installment-badge"]'),
    ).map((el) => el.textContent?.trim());
    expect(installmentBadges).toContain('Cuota 2/6');

    // Nominal lens hides lens subtitle
    expect(host.querySelector('[data-testid="transaction-lens-amount"]')).toBeNull();

    // Signed color-coded amounts
    expect(host.querySelector('[data-testid="transaction-amount"].text-success')?.textContent).toContain('+');
    expect(host.querySelector('[data-testid="transaction-amount"].text-danger')?.textContent).toContain('-');

    const link = host.querySelector('a');
    expect(link?.textContent).toContain('Ver todos');
    expect(link?.getAttribute('href')).toBe('/transactions');
  });

  it('shows lensAmount subtitle when lens is not NOMINAL', () => {
    const transactions = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'REFERENCE_CURRENCY',
      granularity: 'MONTH',
      date: '2026-11-23',
    }).recentTransactions as TransactionItem[];

    const fixture = TestBed.createComponent(RecentListComponent);
    fixture.componentRef.setInput('transactions', transactions);
    fixture.componentRef.setInput('lens', 'REFERENCE_CURRENCY');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const subtitles = host.querySelectorAll('[data-testid="transaction-lens-amount"]');
    expect(subtitles.length).toBeGreaterThan(0);
    expect(subtitles[0].textContent).toContain('US$');
  });
});
