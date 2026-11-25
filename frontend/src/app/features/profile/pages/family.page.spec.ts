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
    const click = (testId: string): void => {
      (el.querySelector(`[data-testid="${testId}"] button`) as HTMLButtonElement).click();
      fixture.detectChanges();
    };

    // Read-only by default, even for adults.
    expect(el.querySelector('[data-testid="family-settings-form"]')).toBeNull();
    expect(el.querySelector('[data-testid="family-settings-readonly"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="open-add-currency-btn"]')).toBeNull();
    expect(el.querySelectorAll('[data-testid="member-visibility-select"]').length).toBe(0);

    // Invitations live inside the unified members card.
    const members = el.querySelector('[data-testid="members-section"]') as HTMLElement;
    expect(members.querySelector('[data-testid="invitations-section"]')).toBeTruthy();

    click('edit-family-settings-btn');
    click('edit-currencies-btn');
    click('edit-members-btn');

    expect(el.querySelector('[data-testid="family-settings-form"]')).toBeTruthy();
    expect(el.querySelector('[data-testid="open-add-currency-btn"]')).toBeTruthy();
    expect(el.querySelectorAll('[data-testid="member-visibility-select"]').length).toBe(1);

    // Visibility change waits for confirmation before hitting the API.
    const select = el.querySelector('[data-testid="member-visibility-select"]') as HTMLSelectElement;
    select.value = 'FAMILY_FULL';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    httpMock.expectNone('/bff/api/family/members/user-child/visibility');
    expect(el.querySelector('[data-testid="visibility-confirm"]')).toBeTruthy();

    const arsSwitch = el.querySelector(
      '[data-testid="currency-toggle-switch"][data-currency="ARS"]',
    ) as HTMLButtonElement;
    const usdSwitch = el.querySelector(
      '[data-testid="currency-toggle-switch"][data-currency="USD"]',
    ) as HTMLButtonElement;

    expect(arsSwitch.disabled).toBe(true);
    expect(usdSwitch.disabled).toBe(false);
  });

  it('asks for confirmation before changing the base currency', async () => {
    session.setMe(buildMe('ADULTO'));
    const fixture = TestBed.createComponent(FamilyPage);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/family').flush(SAMPLE_FAMILY);
    httpMock.expectOne('/bff/api/family/members').flush(SAMPLE_MEMBERS);
    httpMock.expectOne('/bff/api/family/invitations').flush([]);
    await flushMicrotasks();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('[data-testid="edit-family-settings-btn"] button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const base = el.querySelector('#family-base-currency') as HTMLSelectElement;
    base.value = 'USD';
    base.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    (el.querySelector('[data-testid="family-settings-form"]') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    fixture.detectChanges();

    httpMock.expectNone('/bff/api/family/settings');
    expect(el.querySelector('[data-testid="family-settings-confirm"]')).toBeTruthy();

    (el.querySelector('[data-testid="confirm-family-settings-btn"] button') as HTMLButtonElement).click();
    const req = httpMock.expectOne('/bff/api/family/settings');
    expect(req.request.body).toEqual({ baseCurrency: 'USD' });
    req.flush({ ...SAMPLE_FAMILY, baseCurrency: 'USD' });
    await flushMicrotasks();
    // Background /me reload after saving.
    httpMock.match(() => true).forEach((r) => r.flush(buildMe('ADULTO')));
    await flushMicrotasks();
  });
});
