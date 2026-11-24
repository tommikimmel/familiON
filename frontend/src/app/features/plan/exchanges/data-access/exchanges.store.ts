import { Injectable, Signal, inject, signal } from '@angular/core';
import { catchError, firstValueFrom, of } from 'rxjs';
import { AccountsStore } from '../../../../core/finance/accounts.store';
import { FinanceEvents } from '../../../../core/finance/finance-events';
import { View, ViewContextStore } from '../../../../core/state/view-context.store';
import { extractProblemMessage } from '../utils/problem-message';
import { ExchangesApi } from './exchanges.api';
import {
  CurrencyPosition,
  Exchange,
  ExchangeCreate,
  IndexValue,
  PageMetadata,
} from './exchanges.models';

const DEFAULT_PAGE_SIZE = 20;

const EMPTY_PAGE: PageMetadata = {
  size: DEFAULT_PAGE_SIZE,
  number: 0,
  totalElements: 0,
  totalPages: 0,
};

@Injectable({ providedIn: 'root' })
export class ExchangesStore {
  private readonly api = inject(ExchangesApi);
  private readonly accountsStore = inject(AccountsStore);
  private readonly viewContext = inject(ViewContextStore);
  private readonly financeEvents = inject(FinanceEvents);

  private readonly _positions = signal<CurrencyPosition[]>([]);
  private readonly _exchanges = signal<Exchange[]>([]);
  private readonly _page = signal<PageMetadata>(EMPTY_PAGE);
  private readonly _latestIndices = signal<IndexValue[]>([]);
  private readonly _loading = signal<boolean>(false);
  private readonly _pageLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  private loadSeq = 0;

  readonly positions: Signal<CurrencyPosition[]> = this._positions.asReadonly();
  readonly exchanges: Signal<Exchange[]> = this._exchanges.asReadonly();
  readonly page: Signal<PageMetadata> = this._page.asReadonly();
  readonly latestIndices: Signal<IndexValue[]> = this._latestIndices.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly pageLoading: Signal<boolean> = this._pageLoading.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  async load(view?: View, page = 0): Promise<void> {
    const targetView = view ?? this.viewContext.view();
    const requestId = ++this.loadSeq;
    this._loading.set(true);
    this._error.set(null);

    try {
      const [positions, historyPage, indices] = await Promise.all([
        firstValueFrom(this.api.positions(targetView)),
        firstValueFrom(this.api.list(targetView, page, DEFAULT_PAGE_SIZE)),
        firstValueFrom(this.api.latestIndices().pipe(catchError(() => of(null)))),
      ]);

      if (requestId === this.loadSeq) {
        this._positions.set(positions);
        this._exchanges.set(historyPage.content);
        this._page.set(historyPage.page ?? EMPTY_PAGE);
        if (indices !== null) {
          this._latestIndices.set(indices);
        }
      }
    } catch (err) {
      if (requestId === this.loadSeq) {
        this._error.set(
          extractProblemMessage(err, 'No pudimos cargar tus operaciones de cambio de moneda.'),
        );
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._loading.set(false);
      }
    }
  }

  async loadPage(page: number, view?: View): Promise<void> {
    const targetView = view ?? this.viewContext.view();
    const targetPage = Math.max(0, page);
    const requestId = ++this.loadSeq;
    this._pageLoading.set(true);
    this._error.set(null);

    try {
      const historyPage = await firstValueFrom(
        this.api.list(targetView, targetPage, DEFAULT_PAGE_SIZE),
      );
      if (requestId === this.loadSeq) {
        this._exchanges.set(historyPage.content);
        this._page.set(historyPage.page ?? EMPTY_PAGE);
      }
    } catch (err) {
      if (requestId === this.loadSeq) {
        this._error.set(
          extractProblemMessage(err, 'No pudimos cargar esa página del historial.'),
        );
      }
    } finally {
      if (requestId === this.loadSeq) {
        this._pageLoading.set(false);
      }
    }
  }

  async create(payload: ExchangeCreate): Promise<Exchange> {
    const created = await firstValueFrom(this.api.create(payload));
    const currentView = this.viewContext.view();
    await this.refreshAfterMutation(currentView, 0);
    this.financeEvents.emit('plan-changed');
    return created;
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.api.remove(id));
    const currentView = this.viewContext.view();
    const currentPage = this._page().number;
    const nextPage =
      this._exchanges().length <= 1 && currentPage > 0 ? currentPage - 1 : currentPage;
    await this.refreshAfterMutation(currentView, nextPage);
    this.financeEvents.emit('plan-changed');
  }

  private async refreshAfterMutation(view: View, page: number): Promise<void> {
    const requestId = ++this.loadSeq;
    try {
      const [positions, historyPage] = await Promise.all([
        firstValueFrom(this.api.positions(view)),
        firstValueFrom(this.api.list(view, page, DEFAULT_PAGE_SIZE)),
        this.reloadAccountsSafely(view),
      ]);
      if (requestId === this.loadSeq) {
        this._positions.set(positions);
        this._exchanges.set(historyPage.content);
        this._page.set(historyPage.page ?? EMPTY_PAGE);
        this._error.set(null);
      }
    } catch {
      // Keep mutation success intact; next reload will recover view state.
    }
  }

  private async reloadAccountsSafely(view: View): Promise<void> {
    try {
      await this.accountsStore.load(view);
    } catch {
      // Non-blocking: account balances refresh best-effort after exchange mutation.
    }
  }
}
