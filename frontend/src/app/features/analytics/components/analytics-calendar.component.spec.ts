import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CalendarView } from '../data-access/analytics.models';
import { AnalyticsCalendarComponent } from './analytics-calendar.component';

const NOV_2026_CALENDAR: CalendarView = {
  from: '2026-11-01',
  to: '2026-11-30',
  days: [
    {
      date: '2026-11-10',
      income: '0',
      expense: '45000',
      items: [
        {
          kind: 'INSTALLMENT',
          refId: 'inst-1',
          title: 'Cuota 3/12 Notebook',
          amount: '45000',
          currencyCode: 'ARS',
        },
        {
          kind: 'RECURRING_DUE',
          refId: 'rec-1',
          title: 'Internet Fibra',
          amount: '28000',
          currencyCode: 'ARS',
        },
        {
          kind: 'VEHICLE_DUE',
          refId: 'veh-1',
          title: 'Seguro Auto',
          amount: '60000',
          currencyCode: 'ARS',
        },
        {
          kind: 'ALLOWANCE',
          refId: 'all-1',
          title: 'Mensualidad Sofía',
          amount: '20000',
          currencyCode: 'ARS',
        },
      ],
    },
    {
      date: '2026-11-15',
      income: '100000',
      expense: '15000',
      items: [
        {
          kind: 'TRANSACTION',
          refId: 'tx-1',
          title: 'Honorarios',
          amount: '100000',
          currencyCode: 'ARS',
        },
      ],
    },
  ],
};

describe('AnalyticsCalendarComponent', () => {
  let fixture: ComponentFixture<AnalyticsCalendarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsCalendarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsCalendarComponent);
  });

  it('renders November 2026 with 6 leading empty cells, marks due items, and lists items by kind on day tap', () => {
    fixture.componentRef.setInput('yearMonth', { year: 2026, month: 11 });
    fixture.componentRef.setInput('calendar', NOV_2026_CALENDAR);
    fixture.componentRef.setInput('todayIso', '2026-11-24');
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('[data-testid="calendar-week-row"]');
    expect(rows).toHaveLength(6);

    const firstRowEmptyCells = rows[0].querySelectorAll('[data-testid="calendar-empty-cell"]');
    expect(firstRowEmptyCells).toHaveLength(6);

    const nov10Button = el.querySelector<HTMLButtonElement>('[data-date="2026-11-10"]');
    const nov15Button = el.querySelector<HTMLButtonElement>('[data-date="2026-11-15"]');
    expect(nov10Button?.querySelector('[data-testid="calendar-due-dot"]')).not.toBeNull();
    expect(nov15Button?.querySelector('[data-testid="calendar-due-dot"]')).toBeNull();

    // Tap Nov 10 -> detail lists all 4 due item kinds
    nov10Button?.click();
    fixture.detectChanges();

    const detail = el.querySelector('[data-testid="calendar-day-detail"]');
    expect(detail?.textContent).toContain('Cuota 3/12 Notebook');
    expect(detail?.textContent).toContain('Cuota');
    expect(detail?.textContent).toContain('Gasto fijo');
    expect(detail?.textContent).toContain('Vencimiento auto');
    expect(detail?.textContent).toContain('Mensualidad');
  });

  it('emits independent month navigation events', () => {
    fixture.componentRef.setInput('yearMonth', { year: 2026, month: 11 });
    fixture.componentRef.setInput('calendar', NOV_2026_CALENDAR);
    fixture.detectChanges();

    const deltas: number[] = [];
    let todayResetCount = 0;
    fixture.componentInstance.shiftMonth.subscribe((d) => deltas.push(d));
    fixture.componentInstance.resetToday.subscribe(() => todayResetCount++);

    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('[data-testid="calendar-prev-month"]')?.click();
    el.querySelector<HTMLButtonElement>('[data-testid="calendar-next-month"]')?.click();
    el.querySelector<HTMLButtonElement>('[data-testid="calendar-today-btn"]')?.click();

    expect(deltas).toEqual([-1, 1]);
    expect(todayResetCount).toBe(1);
  });
});
