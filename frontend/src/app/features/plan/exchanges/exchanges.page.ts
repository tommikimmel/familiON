import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { AccountsStore } from '../../../core/finance/accounts.store';
import { SessionStore } from '../../../core/state/session.store';
import { View, ViewContextStore } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  BottomSheetComponent,
  ButtonComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { ExchangeFormComponent } from './components/exchange-form.component';
import { ExchangeHistoryListComponent } from './components/exchange-history-list.component';
import { PositionCardComponent } from './components/position-card.component';
import { Exchange, ExchangeCreate } from './data-access/exchanges.models';
import { ExchangesStore } from './data-access/exchanges.store';
import { extractProblemMessage } from './utils/problem-message';

@Component({
  selector: 'fo-exchanges-page',
  standalone: true,
  imports: [
    BottomSheetComponent,
    ButtonComponent,
    EmptyStateComponent,
    ExchangeFormComponent,
    ExchangeHistoryListComponent,
    IconComponent,
    MoneyPipe,
    PositionCardComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="w-full space-y-3 lg:space-y-4">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="fo-page-title text-text lg:sr-only">Cambio de moneda</h1>
          <p class="mt-0.5 text-[11px] text-muted">
            Registrá compras y ventas de divisas y seguí tu precio promedio ponderado.
          </p>
        </div>

        <fo-button
          variant="primary"
          data-testid="new-exchange-btn"
          (click)="openCreateSheet()"
        >
          <fo-icon name="plus" [size]="16" />
          <span>Nueva operación</span>
        </fo-button>
      </header>

      @if (store.loading() && store.positions().length === 0 && store.exchanges().length === 0) {
        <div class="space-y-5" aria-busy="true" aria-label="Cargando operaciones de cambio">
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <fo-skeleton height="9.5rem" rounded="lg" />
            <fo-skeleton height="9.5rem" rounded="lg" />
          </div>
          <div class="space-y-2.5">
            <fo-skeleton height="4.25rem" rounded="lg" />
            <fo-skeleton height="4.25rem" rounded="lg" />
            <fo-skeleton height="4.25rem" rounded="lg" />
          </div>
        </div>
      } @else if (store.error(); as errorMsg) {
        <fo-empty-state
          icon="alert-circle"
          title="No pudimos cargar tus divisas"
          [description]="errorMsg"
        >
          <fo-button variant="primary" (click)="reload()">Reintentar</fo-button>
        </fo-empty-state>
      } @else if (store.positions().length === 0 && store.exchanges().length === 0) {
        <fo-empty-state
          data-testid="exchanges-empty-state"
          icon="arrow-left-right"
          title="Todavía no registraste cambios de moneda"
          description="Registrá tu primera compra o venta de divisas para ver tu precio promedio ponderado y el resultado frente a la cotización actual."
        >
          <fo-button variant="primary" (click)="openCreateSheet()">
            <fo-icon name="plus" [size]="16" />
            <span>Nueva operación</span>
          </fo-button>
        </fo-empty-state>
      } @else {
        @if (store.positions().length > 0) {
          <section aria-label="Posiciones por divisa" class="space-y-3">
            <div class="flex items-center justify-between gap-2">
              <h2 class="text-sm font-semibold text-text">Posiciones</h2>
              <span class="text-xs text-muted">
                Precio promedio ponderado en {{ baseCurrency() }}
              </span>
            </div>

            <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              @for (position of store.positions(); track position.currencyCode) {
                <fo-position-card
                  [position]="position"
                  [baseCurrency]="baseCurrency()"
                />
              }
            </div>
          </section>
        }

        <section aria-label="Historial de operaciones" class="space-y-3">
          <div class="flex items-center justify-between gap-2">
            <h2 class="text-sm font-semibold text-text">Historial</h2>
            <span class="text-xs text-muted tabular-nums">
              {{ store.page().totalElements }}
              {{ store.page().totalElements === 1 ? 'operación' : 'operaciones' }}
            </span>
          </div>

          @if (store.exchanges().length > 0) {
            <fo-exchange-history-list
              [exchanges]="store.exchanges()"
              [page]="store.page()"
              [baseCurrency]="baseCurrency()"
              [currentUserId]="currentUserId()"
              [isAdult]="session.isAdult()"
              [loading]="store.pageLoading()"
              (deleteRequested)="openDeleteSheet($event)"
              (pageChange)="goToPage($event)"
            />
          } @else {
            <fo-empty-state
              icon="arrow-left-right"
              title="Sin operaciones registradas"
              description="Cuando registres una compra o venta aparecerá en este historial."
            />
          }
        </section>
      }
    </section>

    <fo-bottom-sheet
      [(open)]="createSheetOpen"
      title="Nueva operación"
      (closed)="resetCreateSheet()"
    >
      @if (createSheetOpen()) {
        <fo-exchange-form
          [accounts]="usableAccounts()"
          [baseCurrency]="baseCurrency()"
          [referenceCurrency]="referenceCurrency()"
          [preferredRateSource]="preferredRateSource()"
          [latestIndices]="store.latestIndices()"
          [defaultAccountIds]="defaultAccountIds()"
          [saving]="creating()"
          [errorMessage]="createError()"
          (submitted)="submitExchange($event)"
          (cancelled)="closeCreateSheet()"
        />
      }
    </fo-bottom-sheet>

    <fo-bottom-sheet
      [(open)]="deleteSheetOpen"
      title="Eliminar operación"
      (closed)="deletingExchange.set(null)"
    >
      @if (deletingExchange(); as target) {
        <div class="space-y-4">
          <p class="text-sm text-text">
            ¿Querés eliminar el cambio de
            <strong class="font-semibold tabular-nums">
              {{ target.sourceAmount | money: target.sourceCurrency }}
            </strong>
            por
            <strong class="font-semibold tabular-nums">
              {{ target.targetAmount | money: target.targetCurrency }}
            </strong>?
          </p>
          <p class="rounded-xl border border-border bg-surface-2 px-3.5 py-2.5 text-sm text-muted">
            Se revertirán los movimientos asociados en las cuentas y se recalcularán tus posiciones.
          </p>
          <div class="flex items-center justify-end gap-2 pt-1">
            <fo-button
              variant="ghost"
              type="button"
              [disabled]="deleting()"
              (click)="closeDeleteSheet()"
            >
              Cancelar
            </fo-button>
            <fo-button
              variant="danger"
              type="button"
              data-testid="confirm-delete-exchange-btn"
              [loading]="deleting()"
              (click)="confirmDelete()"
            >
              Eliminar operación
            </fo-button>
          </div>
        </div>
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExchangesPage {
  protected readonly store = inject(ExchangesStore);
  private readonly accountsStore = inject(AccountsStore);
  private readonly viewContext = inject(ViewContextStore);
  protected readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  protected readonly usableAccounts = this.accountsStore.usableFor();

  protected readonly createSheetOpen = signal<boolean>(false);
  protected readonly creating = signal<boolean>(false);
  protected readonly createError = signal<string | null>(null);

  protected readonly deleteSheetOpen = signal<boolean>(false);
  protected readonly deletingExchange = signal<Exchange | null>(null);
  protected readonly deleting = signal<boolean>(false);

  protected readonly currentUserId = computed(() => this.session.me()?.id ?? null);

  protected readonly baseCurrency = computed(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

  protected readonly referenceCurrency = computed(
    () => this.session.me()?.family.referenceCurrency ?? 'USD',
  );

  protected readonly preferredRateSource = computed(
    () => this.session.me()?.family.referenceRateSource ?? 'USD_MEP',
  );

  protected readonly defaultAccountIds = computed<Record<string, string>>(() => {
    const accounts = this.usableAccounts();
    const map: Record<string, string> = {};
    const currencies = new Set(accounts.map((acc) => acc.currencyCode.trim().toUpperCase()));
    for (const code of currencies) {
      const preferred = this.accountsStore.defaultAccountFor(code);
      if (preferred) {
        map[code] = preferred.id;
      }
    }
    return map;
  });

  constructor() {
    effect(() => {
      const currentView = this.viewContext.view();
      void this.loadAll(currentView);
    });
  }

  protected reload(): void {
    void this.loadAll(this.viewContext.view());
  }

  protected goToPage(nextPage: number): void {
    void this.store.loadPage(nextPage, this.viewContext.view());
  }

  protected openCreateSheet(): void {
    this.createError.set(null);
    this.createSheetOpen.set(true);
  }

  protected closeCreateSheet(): void {
    this.createSheetOpen.set(false);
    this.resetCreateSheet();
  }

  protected resetCreateSheet(): void {
    this.createError.set(null);
  }

  protected async submitExchange(payload: ExchangeCreate): Promise<void> {
    this.creating.set(true);
    this.createError.set(null);

    try {
      await this.store.create(payload);
      this.closeCreateSheet();
      this.toast.success('Operación registrada.');
    } catch (err) {
      this.createError.set(
        extractProblemMessage(err, 'No pudimos registrar la operación. Intentá de nuevo.'),
      );
    } finally {
      this.creating.set(false);
    }
  }

  protected openDeleteSheet(exchange: Exchange): void {
    this.deletingExchange.set(exchange);
    this.deleteSheetOpen.set(true);
  }

  protected closeDeleteSheet(): void {
    this.deleteSheetOpen.set(false);
    this.deletingExchange.set(null);
  }

  protected async confirmDelete(): Promise<void> {
    const target = this.deletingExchange();
    if (!target) {
      return;
    }

    this.deleting.set(true);
    try {
      await this.store.remove(target.id);
      this.closeDeleteSheet();
      this.toast.success('Operación eliminada.');
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos eliminar la operación. Intentá de nuevo.'),
      );
    } finally {
      this.deleting.set(false);
    }
  }

  private async loadAll(view: View): Promise<void> {
    await Promise.all([
      this.store.load(view),
      this.accountsStore.load(view).catch(() => undefined),
    ]);
  }
}
