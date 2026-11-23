import { DOCUMENT } from '@angular/common';
import { Injectable, Signal, computed, effect, inject, signal } from '@angular/core';
import { SessionStore } from './session.store';

export type View = 'PERSONAL' | 'FAMILY';
export type Lens = 'NOMINAL' | 'CONSTANT_BASE' | 'REFERENCE_CURRENCY' | 'INCOME_PCT';
export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

export interface ViewContextQueryParams {
  view: View;
  lens: Lens;
  granularity: Granularity;
  date: string;
}

interface PersistedViewContext {
  view: View;
  lens: Lens;
  granularity: Granularity;
}

const STORAGE_KEY = 'fo.viewContext';
const DEFAULT_VIEW: View = 'PERSONAL';
const DEFAULT_LENS: Lens = 'NOMINAL';
const DEFAULT_GRANULARITY: Granularity = 'MONTH';
const DEFAULT_BASE_CURRENCY = 'ARS';
const DEFAULT_REFERENCE_CURRENCY = 'USD';

function isView(value: unknown): value is View {
  return value === 'PERSONAL' || value === 'FAMILY';
}

function isLens(value: unknown): value is Lens {
  return (
    value === 'NOMINAL' ||
    value === 'CONSTANT_BASE' ||
    value === 'REFERENCE_CURRENCY' ||
    value === 'INCOME_PCT'
  );
}

function isGranularity(value: unknown): value is Granularity {
  return value === 'DAY' || value === 'WEEK' || value === 'MONTH' || value === 'YEAR';
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatUtcDate(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function daysInUtcMonth(year: number, monthIndex: number): number {
  return new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
}

function shiftIsoDate(isoDate: string, granularity: Granularity, direction: -1 | 1): string {
  const [yStr, mStr, dStr] = isoDate.split('-');
  const year = Number(yStr);
  const monthIndex = Number(mStr) - 1;
  const day = Number(dStr);

  if (!Number.isFinite(year) || !Number.isFinite(monthIndex) || !Number.isFinite(day)) {
    return formatLocalDate(new Date());
  }

  switch (granularity) {
    case 'DAY': {
      const shifted = new Date(Date.UTC(year, monthIndex, day + direction));
      return formatUtcDate(shifted);
    }
    case 'WEEK': {
      const shifted = new Date(Date.UTC(year, monthIndex, day + direction * 7));
      return formatUtcDate(shifted);
    }
    case 'MONTH': {
      const totalMonths = year * 12 + monthIndex + direction;
      const targetYear = Math.floor(totalMonths / 12);
      const targetMonth = ((totalMonths % 12) + 12) % 12;
      const clampedDay = Math.min(day, daysInUtcMonth(targetYear, targetMonth));
      return formatUtcDate(new Date(Date.UTC(targetYear, targetMonth, clampedDay)));
    }
    case 'YEAR': {
      const targetYear = year + direction;
      const clampedDay = Math.min(day, daysInUtcMonth(targetYear, monthIndex));
      return formatUtcDate(new Date(Date.UTC(targetYear, monthIndex, clampedDay)));
    }
  }
}

@Injectable({ providedIn: 'root' })
export class ViewContextStore {
  private readonly doc = inject(DOCUMENT);
  private readonly session = inject(SessionStore);

  private readonly initial = this.readPersisted();

  private readonly _view = signal<View>(this.initial.view);
  private readonly _lens = signal<Lens>(this.initial.lens);
  private readonly _granularity = signal<Granularity>(this.initial.granularity);
  private readonly _anchorDate = signal<string>(formatLocalDate(new Date()));
  private readonly _selectedCurrencyOverride = signal<string | null>(null);

  readonly canSeeFamily: Signal<boolean> = computed(() => {
    const me = this.session.me();
    if (!me) {
      return true;
    }
    return me.role === 'ADULTO' || me.familyVisibility !== 'OWN_ONLY';
  });

  readonly view: Signal<View> = computed(() => (this.canSeeFamily() ? this._view() : 'PERSONAL'));
  readonly lens: Signal<Lens> = this._lens.asReadonly();
  readonly granularity: Signal<Granularity> = this._granularity.asReadonly();
  readonly anchorDate: Signal<string> = this._anchorDate.asReadonly();

  readonly activeCurrency: Signal<string> = computed(() => {
    const family = this.session.me()?.family;
    const baseCurrency = family?.baseCurrency ?? DEFAULT_BASE_CURRENCY;
    const referenceCurrency = family?.referenceCurrency ?? DEFAULT_REFERENCE_CURRENCY;

    if (this._lens() === 'REFERENCE_CURRENCY') {
      return referenceCurrency;
    }

    const override = this._selectedCurrencyOverride();
    if (override) {
      return override;
    }
    return baseCurrency;
  });

  readonly queryParams: Signal<ViewContextQueryParams> = computed(() => ({
    view: this.view(),
    lens: this._lens(),
    granularity: this._granularity(),
    date: this._anchorDate(),
  }));

  constructor() {
    effect(() => {
      if (!this.canSeeFamily() && this._view() === 'FAMILY') {
        this._view.set('PERSONAL');
        this.persist();
      }
    });
  }

  setView(v: View): void {
    const nextView: View = v === 'FAMILY' && !this.canSeeFamily() ? 'PERSONAL' : v;
    this._view.set(nextView);
    this.persist();
  }

  setLens(l: Lens): void {
    this._lens.set(l);
    if (l === 'REFERENCE_CURRENCY' || l === 'CONSTANT_BASE') {
      this._selectedCurrencyOverride.set(null);
    }
    this.persist();
  }

  selectCurrency(currencyCode: string): void {
    const code = currencyCode.trim().toUpperCase();
    const family = this.session.me()?.family;
    const baseCurrency = family?.baseCurrency ?? DEFAULT_BASE_CURRENCY;
    const referenceCurrency = family?.referenceCurrency ?? DEFAULT_REFERENCE_CURRENCY;

    if (code === referenceCurrency) {
      this._selectedCurrencyOverride.set(null);
      this.setLens('REFERENCE_CURRENCY');
      return;
    }

    if (code === baseCurrency) {
      this._selectedCurrencyOverride.set(null);
      if (this._lens() === 'REFERENCE_CURRENCY') {
        this.setLens('NOMINAL');
      }
      return;
    }

    this._selectedCurrencyOverride.set(code);
    if (this._lens() === 'REFERENCE_CURRENCY' || this._lens() === 'CONSTANT_BASE') {
      this.setLens('NOMINAL');
    }
  }

  setGranularity(g: Granularity): void {
    this._granularity.set(g);
    this.persist();
  }

  shiftPeriod(direction: -1 | 1): void {
    this._anchorDate.update((current) => shiftIsoDate(current, this._granularity(), direction));
  }

  resetToToday(): void {
    this._anchorDate.set(formatLocalDate(new Date()));
  }

  private readPersisted(): PersistedViewContext {
    try {
      const raw = this.doc.defaultView?.localStorage?.getItem(STORAGE_KEY);
      if (!raw) {
        return { view: DEFAULT_VIEW, lens: DEFAULT_LENS, granularity: DEFAULT_GRANULARITY };
      }
      const parsed = JSON.parse(raw) as Partial<PersistedViewContext>;
      return {
        view: isView(parsed.view) ? parsed.view : DEFAULT_VIEW,
        lens: isLens(parsed.lens) ? parsed.lens : DEFAULT_LENS,
        granularity: isGranularity(parsed.granularity) ? parsed.granularity : DEFAULT_GRANULARITY,
      };
    } catch {
      return { view: DEFAULT_VIEW, lens: DEFAULT_LENS, granularity: DEFAULT_GRANULARITY };
    }
  }

  private persist(): void {
    try {
      const payload: PersistedViewContext = {
        view: this.view(),
        lens: this._lens(),
        granularity: this._granularity(),
      };
      this.doc.defaultView?.localStorage?.setItem(STORAGE_KEY, JSON.stringify(payload));
    } catch {
      // Ignore storage errors (private mode / quota)
    }
  }
}
