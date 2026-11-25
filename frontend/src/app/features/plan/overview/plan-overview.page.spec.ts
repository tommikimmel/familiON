import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  EMPTY_PLAN_FIXTURE,
  FULL_PLAN_FIXTURE,
  PARTIAL_NULL_PLAN_FIXTURE,
} from './data-access/plan-overview.fixtures';
import { PlanPayload } from './data-access/plan-overview.models';
import { PlanOverviewPage } from './plan-overview.page';

describe('PlanOverviewPage', () => {
  let httpMock: HttpTestingController;

  function renderWithPayload(payload: PlanPayload | null = null) {
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [PlanOverviewPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PlanOverviewPage);
    fixture.detectChanges();

    if (payload !== null) {
      httpMock.expectOne((r) => r.url === '/bff/plan').flush(payload);
      fixture.detectChanges();
    }

    return fixture;
  }

  afterEach(() => {
    httpMock?.verify();
    localStorage.clear();
  });

  it('shows skeleton while loading and renders all 5 sections with full fixture data', () => {
    const fixture = renderWithPayload();
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[data-testid="plan-overview-skeleton"]')).not.toBeNull();

    httpMock.expectOne((r) => r.url === '/bff/plan').flush(FULL_PLAN_FIXTURE);
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="plan-overview-skeleton"]')).toBeNull();
    expect(host.querySelector('[data-testid="plan-overview-grid"]')).not.toBeNull();

    // Este mes (recurring)
    const recurringCard = host.querySelector('[data-testid="plan-recurring-card"]');
    expect(recurringCard).not.toBeNull();
    expect(recurringCard?.querySelector('a[href="/plan/recurring"]')?.textContent).toContain(
      'Ver todo',
    );
    expect(
      recurringCard?.querySelector('[data-testid="recurring-headline"]')?.textContent,
    ).toContain('3 pendientes');
    expect(
      recurringCard?.querySelector('[data-testid="recurring-overdue-list"]')?.textContent,
    ).toContain('Expensas');
    expect(
      recurringCard?.querySelector('[data-testid="recurring-next-due"]')?.textContent,
    ).toContain('Internet fibra');

    // Cuotas (installments + commitment)
    const installmentsCard = host.querySelector('[data-testid="plan-installments-card"]');
    expect(installmentsCard).not.toBeNull();
    expect(installmentsCard?.querySelector('a[href="/plan/installments"]')?.textContent).toContain(
      'Ver todo',
    );
    expect(
      installmentsCard?.querySelector('[data-testid="installments-total"]')?.textContent,
    ).toContain('680.000');
    expect(installmentsCard?.querySelectorAll('[data-testid="commitment-bar"]')).toHaveLength(6);
    expect(
      installmentsCard?.querySelector('[data-testid="installments-next"]')?.textContent,
    ).toContain('Heladera inverter');

    // Divisas (positions)
    const exchangesCard = host.querySelector('[data-testid="plan-exchanges-card"]');
    expect(exchangesCard).not.toBeNull();
    expect(exchangesCard?.querySelector('a[href="/plan/exchanges"]')?.textContent).toContain(
      'Ver todo',
    );
    const chips = exchangesCard?.querySelectorAll('[data-testid="exchange-position-chip"]');
    expect(chips).toHaveLength(2);
    expect(chips?.[0]?.textContent).toContain('1.250 USD');
    expect(chips?.[0]?.textContent).toContain('+8,5%');
    expect(chips?.[1]?.textContent).toContain('-2,3%');
    expect(
      exchangesCard?.querySelector('[data-testid="exchanges-trade-cta"]')?.textContent,
    ).toContain('Registrar compra o venta');

    // Metas (top 3 with progress rings)
    const goalsCard = host.querySelector('[data-testid="plan-goals-card"]');
    expect(goalsCard).not.toBeNull();
    expect(goalsCard?.querySelector('a[href="/plan/goals"]')?.textContent).toContain('Ver todo');
    const goalItems = goalsCard?.querySelectorAll('[data-testid="goal-item"]');
    expect(goalItems).toHaveLength(3);
    expect(goalsCard?.querySelectorAll('[role="progressbar"]')).toHaveLength(3);
    expect(goalsCard?.textContent).toContain('Vacaciones verano');
    expect(goalsCard?.textContent).toContain('65%');
    expect(goalsCard?.textContent).toContain('+1 meta más');

    // Mensualidades (allowances)
    const allowancesCard = host.querySelector('[data-testid="plan-allowances-card"]');
    expect(allowancesCard).not.toBeNull();
    expect(allowancesCard?.querySelector('a[href="/plan/allowances"]')?.textContent).toContain(
      'Ver todo',
    );
    const allowanceItems = allowancesCard?.querySelectorAll('[data-testid="allowance-item"]');
    expect(allowanceItems).toHaveLength(2);
    expect(allowancesCard?.textContent).toContain('Mateo Gómez');
    expect(allowancesCard?.textContent).toContain('Sofía Gómez');
  });

  it('shows a visible intro and orders sections most-used first, each with a description', () => {
    const fixture = renderWithPayload(FULL_PLAN_FIXTURE);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[data-testid="plan-overview-intro"] h1')?.textContent).toContain('Planificación');

    const cards = Array.from(host.querySelectorAll('[data-testid="plan-overview-grid"] [data-testid^="plan-"]'));
    expect(cards.map((el) => el.getAttribute('data-testid'))).toEqual([
      'plan-recurring-card',
      'plan-installments-card',
      'plan-goals-card',
      'plan-exchanges-card',
      'plan-allowances-card',
    ]);
    cards.forEach((card) => {
      expect(card.querySelector('[data-testid="section-description"]')?.textContent?.trim()).toBeTruthy();
    });
  });

  it('renders degraded cards with "No se pudo cargar" and Retry when sections are null', () => {
    const fixture = renderWithPayload(PARTIAL_NULL_PLAN_FIXTURE);
    const host = fixture.nativeElement as HTMLElement;

    const degradedStates = host.querySelectorAll('[data-testid="section-degraded-state"]');
    expect(degradedStates).toHaveLength(5);
    degradedStates.forEach((el) => {
      expect(el.textContent).toContain('No se pudo cargar');
      expect(el.textContent).toContain('Reintentar');
    });

    const firstRetryBtn = host.querySelector(
      '[data-testid="section-retry-btn"] button',
    ) as HTMLButtonElement;
    firstRetryBtn.click();
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/plan').flush(FULL_PLAN_FIXTURE);
    fixture.detectChanges();

    expect(host.querySelectorAll('[data-testid="section-degraded-state"]')).toHaveLength(0);
    expect(host.querySelector('[data-testid="recurring-headline"]')).not.toBeNull();
  });

  it('handles partial degradation inside Cuotas when commitment is null but installments succeeded', () => {
    const fixture = renderWithPayload({
      ...FULL_PLAN_FIXTURE,
      commitment: null,
    });
    const host = fixture.nativeElement as HTMLElement;

    const installmentsCard = host.querySelector('[data-testid="plan-installments-card"]');
    expect(installmentsCard?.querySelector('[data-testid="installments-total"]')).not.toBeNull();
    expect(
      installmentsCard?.querySelector('[data-testid="commitment-partial-error"]'),
    ).not.toBeNull();
    expect(
      installmentsCard?.querySelector('[data-testid="installments-commitment-bars"]'),
    ).toBeNull();
  });

  it('shows creation CTAs for empty sections and hides Mensualidades when allowances is empty', () => {
    const fixture = renderWithPayload(EMPTY_PLAN_FIXTURE);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('[data-testid="recurring-empty-cta"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="installments-empty-cta"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="exchanges-empty-cta"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="goals-empty-cta"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="plan-allowances-card"]')).toBeNull();
  });

  it('renders global error state on HTTP failure and reloads when Reintentar is clicked', () => {
    const fixture = renderWithPayload();
    const host = fixture.nativeElement as HTMLElement;

    httpMock
      .expectOne((r) => r.url === '/bff/plan')
      .flush({ status: 500, detail: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="plan-overview-error"]')).not.toBeNull();
    expect(host.textContent).toContain('Error interno');

    const retryBtn = host.querySelector(
      '[data-testid="plan-overview-retry-btn"] button',
    ) as HTMLButtonElement;
    retryBtn.click();
    fixture.detectChanges();

    httpMock.expectOne((r) => r.url === '/bff/plan').flush(FULL_PLAN_FIXTURE);
    fixture.detectChanges();

    expect(host.querySelector('[data-testid="plan-overview-error"]')).toBeNull();
    expect(host.querySelector('[data-testid="plan-overview-grid"]')).not.toBeNull();
  });
});
