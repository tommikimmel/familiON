import { TestBed } from '@angular/core/testing';
import { Me } from '../api/models/me.model';
import { SessionStore } from './session.store';
import { ViewContextStore } from './view-context.store';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'member@familion.test',
    fullName: 'Lucía Pérez',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia Pérez',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD', 'EUR'],
    },
    ...overrides,
  };
}

describe('ViewContextStore', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('initializes with default PERSONAL, NOMINAL, MONTH and today anchorDate', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 15, 12, 0, 0));

    const store = TestBed.inject(ViewContextStore);

    expect(store.view()).toBe('PERSONAL');
    expect(store.lens()).toBe('NOMINAL');
    expect(store.granularity()).toBe('MONTH');
    expect(store.anchorDate()).toBe('2026-03-15');
    expect(store.queryParams()).toEqual({
      view: 'PERSONAL',
      lens: 'NOMINAL',
      granularity: 'MONTH',
      date: '2026-03-15',
    });
  });

  it('persists view, lens, and granularity to localStorage and restores them', () => {
    const store = TestBed.inject(ViewContextStore);

    store.setView('FAMILY');
    store.setLens('CONSTANT_BASE');
    store.setGranularity('WEEK');

    const raw = localStorage.getItem('fo.viewContext');
    expect(raw).not.toBeNull();
    expect(JSON.parse(raw!)).toEqual({
      view: 'FAMILY',
      lens: 'CONSTANT_BASE',
      granularity: 'WEEK',
    });

    TestBed.resetTestingModule();
    const restored = TestBed.inject(ViewContextStore);
    expect(restored.view()).toBe('FAMILY');
    expect(restored.lens()).toBe('CONSTANT_BASE');
    expect(restored.granularity()).toBe('WEEK');
  });

  it('shifts anchorDate by DAY, WEEK, MONTH, and YEAR and supports resetToToday', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 2, 31, 12, 0, 0)); // 2026-03-31

    const store = TestBed.inject(ViewContextStore);

    store.setGranularity('DAY');
    store.shiftPeriod(1);
    expect(store.anchorDate()).toBe('2026-04-01');
    store.shiftPeriod(-1);
    expect(store.anchorDate()).toBe('2026-03-31');

    store.setGranularity('WEEK');
    store.shiftPeriod(1);
    expect(store.anchorDate()).toBe('2026-04-07');
    store.shiftPeriod(-1);
    expect(store.anchorDate()).toBe('2026-03-31');

    store.setGranularity('MONTH');
    store.shiftPeriod(-1); // March 31 -> Feb 28 (clamped)
    expect(store.anchorDate()).toBe('2026-02-28');
    store.shiftPeriod(1);
    expect(store.anchorDate()).toBe('2026-03-28');

    store.setGranularity('YEAR');
    store.shiftPeriod(1);
    expect(store.anchorDate()).toBe('2027-03-28');

    store.resetToToday();
    expect(store.anchorDate()).toBe('2026-03-31');
  });

  it('forces PERSONAL when canSeeFamily becomes false for HIJO with OWN_ONLY visibility', () => {
    const session = TestBed.inject(SessionStore);
    const store = TestBed.inject(ViewContextStore);

    session.setMe(buildMe({ role: 'HIJO', familyVisibility: 'FAMILY_SUMMARY' }));
    expect(store.canSeeFamily()).toBe(true);
    store.setView('FAMILY');
    expect(store.view()).toBe('FAMILY');

    session.setMe(buildMe({ role: 'HIJO', familyVisibility: 'OWN_ONLY' }));
    TestBed.flushEffects();
    expect(store.canSeeFamily()).toBe(false);
    expect(store.view()).toBe('PERSONAL');

    store.setView('FAMILY');
    expect(store.view()).toBe('PERSONAL');

    const persisted = JSON.parse(localStorage.getItem('fo.viewContext') ?? '{}') as { view?: string };
    expect(persisted.view).toBe('PERSONAL');
  });

  it('switches lens to REFERENCE_CURRENCY when family reference currency is selected', () => {
    const session = TestBed.inject(SessionStore);
    session.setMe(buildMe());
    const store = TestBed.inject(ViewContextStore);

    expect(store.activeCurrency()).toBe('ARS');

    store.selectCurrency('USD');
    expect(store.lens()).toBe('REFERENCE_CURRENCY');
    expect(store.activeCurrency()).toBe('USD');

    store.selectCurrency('ARS');
    expect(store.lens()).toBe('NOMINAL');
    expect(store.activeCurrency()).toBe('ARS');
  });
});
