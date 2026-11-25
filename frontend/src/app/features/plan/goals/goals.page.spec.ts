import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { Me } from '../../../core/api/models/me.model';
import { SessionStore } from '../../../core/state/session.store';
import { GoalsPage } from './goals.page';
import { SavingsGoal } from './data-access/goals.models';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'ana@example.com',
    fullName: 'Ana García',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

const SAMPLE_GOAL: SavingsGoal = {
  id: 'goal-1',
  name: 'Viaje familiar',
  scope: 'FAMILY',
  owner: { id: 'user-1', fullName: 'Ana García' },
  targetAmount: '1000',
  targetUnit: 'USD',
  targetDate: '2026-12-31',
  status: 'ACTIVE',
  accumulatedInUnit: '250',
  progressPct: '25.0',
  remainingInUnit: '750',
  monthsLeft: 10,
  suggestedMonthlyInUnit: '75',
  contributionsCount: 1,
  lastContributionDate: '2026-02-15',
  accumulatedInBaseToday: '300000',
};

async function settle(fixture: ComponentFixture<GoalsPage>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('GoalsPage', () => {
  let fixture: ComponentFixture<GoalsPage>;
  let httpMock: HttpTestingController;
  let session: SessionStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [GoalsPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    session = TestBed.inject(SessionStore);
    session.setMe(buildMe());
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('renders heading "Metas" and displays goals cards', async () => {
    fixture = TestBed.createComponent(GoalsPage);
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/bff/api/savings-goals');
    req.flush([SAMPLE_GOAL]);

    await settle(fixture);

    const el = fixture.nativeElement as HTMLElement;
    const heading = el.querySelector('h1');
    expect(heading?.textContent?.trim()).toBe('Metas');

    const cardTitle = el.querySelector('fo-goal-card h3');
    expect(cardTitle?.textContent?.trim()).toBe('Viaje familiar');
    expect(el.textContent).toContain('25%');
  });

  it('displays empty state when no goals exist', async () => {
    fixture = TestBed.createComponent(GoalsPage);
    fixture.detectChanges();

    const req = httpMock.expectOne((r) => r.url === '/bff/api/savings-goals');
    req.flush([]);

    await settle(fixture);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('fo-empty-state')).toBeTruthy();
  });
});
