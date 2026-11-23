import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { buildDemoHomePayload } from '../data-access/home.fixtures';
import { TransactionItem } from '../data-access/home.models';
import { MovementsTableComponent } from './movements-table.component';

describe('MovementsTableComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
  });

  it('renders all 6 required columns and filters rows client-side with instant search', () => {
    const transactions = buildDemoHomePayload({
      view: 'FAMILY',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-11-23',
    }).recentTransactions as TransactionItem[];

    const fixture = TestBed.createComponent(MovementsTableComponent);
    fixture.componentRef.setInput('transactions', transactions);
    fixture.componentRef.setInput('lens', 'NOMINAL');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const headers = Array.from(host.querySelectorAll('thead th')).map((th) =>
      th.textContent?.trim(),
    );
    expect(headers).toEqual([
      'Movimiento',
      'Categoría',
      'Atribuido a / pagó',
      'Cuenta / Cuota',
      'Fecha',
      'Monto',
    ]);
    expect(host.querySelectorAll('tbody tr')).toHaveLength(10);

    const search = host.querySelector(
      '[data-testid="movements-search-input"]',
    ) as HTMLInputElement;
    search.value = 'coto';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const rows = host.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('Supermercado Coto');

    // Non-matching query displays empty search feedback
    search.value = 'inexistente-xyz';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(host.querySelectorAll('tbody tr')).toHaveLength(0);
    expect(host.textContent).toContain('No encontramos movimientos');
  });
});
