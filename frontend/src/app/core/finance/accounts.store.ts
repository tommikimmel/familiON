import { DOCUMENT } from '@angular/common';
import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SessionStore } from '../state/session.store';
import { View, ViewContextStore } from '../state/view-context.store';
import { AccountsApi } from './accounts.api';
import { Account, AccountCreate } from './finance.models';

const DEFAULT_ACCOUNT_STORAGE_PREFIX = 'fo.defaultAccount.';

function normalizeCurrency(currency: string): string {
  return currency.trim().toUpperCase();
}

@Injectable({ providedIn: 'root' })
export class AccountsStore {
  private readonly api = inject(AccountsApi);
  private readonly session = inject(SessionStore);
  private readonly viewContext = inject(ViewContextStore);
  private readonly doc = inject(DOCUMENT);

  private readonly _accounts = signal<Account[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _defaultVersion = signal<number>(0);

  private loadSeq = 0;

  readonly accounts: Signal<Account[]> = this._accounts.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();

  readonly creditCards: Signal<Account[]> = computed(() =>
    this._accounts().filter((account) => account.isActive && account.accountType === 'CREDIT_CARD'),
  );

  async load(view?: View): Promise<void> {
    const targetView = view ?? this.viewContext.view();
    const requestId = ++this.loadSeq;
    this._loading.set(true);
    try {
      const list = await firstValueFrom(this.api.list(targetView));
      if (requestId === this.loadSeq) {
        this._accounts.set(list);
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  async create(account: AccountCreate): Promise<Account> {
    const created = await firstValueFrom(this.api.create(account));
    this._accounts.update((current) => [...current.filter((item) => item.id !== created.id), created]);
    return created;
  }

  async update(id: string, patch: Partial<AccountCreate>): Promise<Account> {
    const updated = await firstValueFrom(this.api.update(id, patch));
    this._accounts.update((current) => {
      const exists = current.some((item) => item.id === updated.id);
      if (!exists) {
        return [...current, updated];
      }
      return current.map((item) => (item.id === updated.id ? updated : item));
    });
    return updated;
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.api.remove(id));
    this._accounts.update((current) => current.filter((item) => item.id !== id));
  }

  usableFor(currency?: string): Signal<Account[]> {
    const targetCurrency = currency ? normalizeCurrency(currency) : null;
    return computed(() => this.filterUsable(targetCurrency));
  }

  defaultAccountFor(currency: string): Account | null {
    // Establish reactive dependency so templates/computeds re-evaluate after setDefaultAccount().
    this._defaultVersion();

    const normalized = normalizeCurrency(currency);
    if (!normalized) {
      return null;
    }

    const candidates = this.filterUsable(normalized);
    if (candidates.length === 0) {
      return null;
    }

    const overrideId = this.readDefaultAccountId(normalized);
    if (overrideId) {
      const override = candidates.find((account) => account.id === overrideId);
      if (override) {
        return override;
      }
    }

    return candidates.find((account) => account.accountType !== 'CREDIT_CARD') ?? null;
  }

  setDefaultAccount(currency: string, accountId: string): void {
    const normalized = normalizeCurrency(currency);
    if (!normalized) {
      return;
    }
    try {
      this.doc.defaultView?.localStorage?.setItem(
        `${DEFAULT_ACCOUNT_STORAGE_PREFIX}${normalized}`,
        accountId,
      );
    } catch {
      // Ignore storage errors (private mode / quota).
    }
    this._defaultVersion.update((version) => version + 1);
  }

  private filterUsable(targetCurrency: string | null): Account[] {
    const me = this.session.me();
    return this._accounts().filter((account) => {
      if (!account.isActive) {
        return false;
      }
      if (me && account.ownerUserId !== null && account.ownerUserId !== me.id) {
        return false;
      }
      if (targetCurrency && normalizeCurrency(account.currencyCode) !== targetCurrency) {
        return false;
      }
      return true;
    });
  }

  private readDefaultAccountId(normalizedCurrency: string): string | null {
    try {
      const raw = this.doc.defaultView?.localStorage?.getItem(
        `${DEFAULT_ACCOUNT_STORAGE_PREFIX}${normalizedCurrency}`,
      );
      return raw && raw.trim().length > 0 ? raw : null;
    } catch {
      return null;
    }
  }
}
