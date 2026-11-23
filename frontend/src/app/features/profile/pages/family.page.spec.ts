import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Me } from '../../../core/api/models/me.model';
import { SessionStore } from '../../../core/state/session.store';
import { Family, Member } from '../data-access/family.models';
import { FamilyPage } from './family.page';

function buildMe(role: 'ADULTO' | 'HIJO'): Me {
  return {
    id: role === 'ADULTO' ? 'user-adult' : 'user-child',
    email: role === 'ADULTO' ? 'ana@familion.test' : 'lucas@familion.test',
    fullName: role === 'ADULTO' ? 'Ana García' : 'Lucas García',
    role,
    familyVisibility: role === 'ADULTO' ? 'FAMILY_FULL' : 'OWN_ONLY',
    themePreference: 'SYSTEM',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'USD_MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
  };
}

const SAMPLE_FAMILY: Family = {
  id: 'fam-1',
  name: 'Familia García',
  baseCurrency: 'ARS',
  referenceCurrency: 'USD',
  referenceRateSource: 'USD_MEP',
  timezone: 'America/Argentina/Buenos_Aires',
  enabledCurrencies: [
    { currencyCode: 'ARS', preferredRateSource: null, isActive: true },
    { currencyCode: 'USD', preferredRateSource: 'USD_MEP', isActive: true },
  ],
};

const SAMPLE_MEMBERS: Member[] = [
  {
    id: 'user-adult',
    fullName: 'Ana García',
    email: 'ana@familion.test',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    accentColor: '#0F766E',
    isActive: true,
  },
  {
    id: 'user-child',
    fullName: 'Lucas García',
    email: 'lucas@familion.test',
    role: 'HIJO',
    familyVisibility: 'OWN_ONLY',
    accentColor: '#2563EB',
    isActive: true,
  },
];

async function flushMicrotasks(): Promise<void> {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve();
  }
}

describe('FamilyPage', () => {
  let httpMock: HttpTestingController;
  let session: SessionStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('hides all edit controls when the current user has role HIJO', async () => {
    session.setMe(buildMe('HIJO'));
    const fixture = TestBed.createComponent(FamilyPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/family').flush(SAMPLE_FAMILY);
    httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
    httpMock.expectNone('/bff/api/family/invitations');

    await flushMicrotasks();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="family-settings-form"]')).toBeNull();
    expect(el.querySelector('[data-testid="family-settings-readonly"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="open-add-currency-btn"]')).toBeNull();
    expect(el.querySelectorAll('[data-testid="currency-toggle-switch"]').length).toBe(0);
    expect(el.querySelectorAll('[data-testid="member-visibility-select"]').length).toBe(0);
    expect(el.querySelector('[data-testid="member-visibility-readonly"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="invitations-section"]')).toBeNull();
  });

  it('shows edit controls and disables toggling off the base currency for ADULTO', async () => {
    session.setMe(buildMe('ADULTO'));
    const fixture = TestBed.createComponent(FamilyPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/family').flush(SAMPLE_FAMILY);
    httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
    httpMock.expectOne('/bff/api/family/invitations').flush([]);

    await flushMicrotasks();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="family-settings-form"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="open-add-currency-btn"]')).toBeTruthy();
    expect(el.querySelectorAll('[data-testid="member-visibility-select"]').length).toBe(1);
    expect(el.querySelector('[data-testid="invitations-section"]')).toBeTruthy();

    const arsSwitch = el.querySelector(
      '[data-testid="currency-toggle-switch"][data-currency="ARS"]',
    ) as HTMLButtonElement;
    const usdSwitch = el.querySelector(
      '[data-testid="currency-toggle-switch"][data-currency="USD"]',
    ) as HTMLButtonElement;

    expect(arsSwitch.disabled).toBe(true);
    expect(usdSwitch.disabled).toBe(false);
  });
});
