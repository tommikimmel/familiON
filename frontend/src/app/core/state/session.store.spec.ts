import { TestBed } from '@angular/core/testing';
import { Me } from '../api/models/me.model';
import { SessionStore } from './session.store';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'adult@familion.test',
    fullName: 'Ana García',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'SYSTEM',
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

describe('SessionStore', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  it('starts empty with isAdult false', () => {
    const store = TestBed.inject(SessionStore);
    expect(store.me()).toBeNull();
    expect(store.isAdult()).toBe(false);
  });

  it('updates me and computes isAdult from role', () => {
    const store = TestBed.inject(SessionStore);

    store.setMe(buildMe({ role: 'ADULTO' }));
    expect(store.me()?.fullName).toBe('Ana García');
    expect(store.isAdult()).toBe(true);

    store.setMe(buildMe({ role: 'HIJO' }));
    expect(store.isAdult()).toBe(false);

    store.setMe(null);
    expect(store.me()).toBeNull();
    expect(store.isAdult()).toBe(false);
  });
});
