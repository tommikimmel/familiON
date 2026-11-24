import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { AccountsStore } from '../../../core/finance/accounts.store';
import { CategoriesStore } from '../../../core/finance/categories.store';
import { SessionStore } from '../../../core/state/session.store';
import { View, ViewContextStore } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  BottomSheetComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  IconComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { ConfirmPaymentSheetComponent } from './components/confirm-payment-sheet.component';
import { RecurringExpenseFormComponent } from './components/recurring-expense-form.component';
import { RecurringExpenseItemComponent } from './components/recurring-expense-item.component';
import {
  ConfirmPaymentPayload,
  RecurringExpense,
  RecurringExpenseCreate,
  RecurringExpensePatch,
} from './data-access/recurring.models';
import { RecurringStore } from './data-access/recurring.store';
import {
  currentYearMonth,
  extractProblemMessage,
  formatMonthLabel,
} from './utils/recurring-format';

@Component({
  selector: 'fo-recurring-page',
  standalone: true,
  imports: [
    BottomSheetComponent,
    ButtonComponent,
    CardComponent,
    ConfirmPaymentSheetComponent,
    EmptyStateComponent,
    IconComponent,
    MoneyPipe,
    RecurringExpenseFormComponent,
    RecurringExpenseItemComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-5xl space-y-6">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="text-xl font-semibold text-text">Recurrentes</h1>
          <p class="mt-0.5 text-sm text-muted">
            Gastos fijos del mes: revisá tus vencimientos y confirmá cada pago en dos toques.
          </p>
        </div>

        @if (session.isAdult()) {
          <fo-button
            variant="primary"
            data-testid="new-recurring-btn"
            (click)="openCreateSheet()"
          >
            <fo-icon name="plus" [size]="16" />
            <span>Nuevo gasto fijo</span>
          </fo-button>
        }
      </header>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div
          class="inline-flex items-center gap-1 rounded-full border border-border bg-surface p-1 shadow-2xs"
          role="group"
          aria-label="Selector de mes"
        >
          <button
            type="button"
            data-testid="prev-month-btn"
            aria-label="Mes anterior"
            (click)="shiftMonth(-1)"
            class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-2 hover:text-text"
          >
            <fo-icon name="chevron-left" [size]="18" />
          </button>

          <span
            data-testid="current-month-label"
            class="min-w-36 px-2 text-center text-sm font-semibold text-text"
          >
            {{ monthLabel() }}
          </span>

          <button
            type="button"
            data-testid="next-month-btn"
            aria-label="Mes siguiente"
            (click)="shiftMonth(1)"
            class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-2 hover:text-text"
          >
            <fo-icon name="chevron-right" [size]="18" />
          </button>
        </div>

        @if (store.month() !== todayMonth) {
          <fo-button variant="ghost" size="sm" (click)="goToCurrentMonth()">
            Mes actual
          </fo-button>
        }
      </div>

      @if (store.loading() && store.items().length === 0) {
        <div class="space-y-4" aria-busy="true" aria-label="Cargando gastos fijos">
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <fo-skeleton height="5.5rem" rounded="lg" />
            <fo-skeleton height="5.5rem" rounded="lg" />
            <fo-skeleton height="5.5rem" rounded="lg" />
          </div>
          <div class="space-y-2.5">
            <fo-skeleton height="5rem" rounded="lg" />
            <fo-skeleton height="5rem" rounded="lg" />
            <fo-skeleton height="5rem" rounded="lg" />
          </div>
        </div>
      } @else if (store.error(); as errorMsg) {
        <fo-empty-state
          icon="alert-circle"
          title="No pudimos cargar tus gastos fijos"
          [description]="errorMsg"
        >
          <fo-button variant="primary" (click)="reload()">Reintentar</fo-button>
        </fo-empty-state>
      } @else {
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <fo-card padding="md">
            <p class="text-xs font-medium text-muted">Total estimado</p>
            <div class="mt-1 space-y-0.5" data-testid="summary-estimated">
              @for (row of summaryRows(); track row.currencyCode; let isFirst = $first) {
                <p
                  class="tabular-nums"
                  [class.text-xl]="isFirst"
                  [class.font-semibold]="isFirst"
                  [class.text-text]="isFirst"
                  [class.text-sm]="!isFirst"
                  [class.font-medium]="!isFirst"
                  [class.text-muted]="!isFirst"
                >
                  {{ row.estimatedTotal | money: row.currencyCode }}
                </p>
              }
            </div>
          </fo-card>

          <fo-card padding="md">
            <p class="text-xs font-medium text-muted">Pagado</p>
            <div class="mt-1 space-y-0.5" data-testid="summary-paid">
              @for (row of summaryRows(); track row.currencyCode; let isFirst = $first) {
                <p
                  class="tabular-nums"
                  [class.text-xl]="isFirst"
                  [class.font-semibold]="isFirst"
                  [class.text-success]="isFirst"
                  [class.text-sm]="!isFirst"
                  [class.font-medium]="!isFirst"
                  [class.text-muted]="!isFirst"
                >
                  {{ row.paidTotal | money: row.currencyCode }}
                </p>
              }
            </div>
            @if (store.visibleItems().length > 0) {
              <p class="mt-1 text-xs text-muted tabular-nums">
                {{ store.paidCount() }} de {{ store.visibleItems().length }} pagados
              </p>
            }
          </fo-card>

          <fo-card padding="md">
            <p class="text-xs font-medium text-muted">Pendiente</p>
            <div class="mt-1 space-y-0.5" data-testid="summary-pending">
              @for (row of summaryRows(); track row.currencyCode; let isFirst = $first) {
                <p
                  class="tabular-nums"
                  [class.text-xl]="isFirst"
                  [class.font-semibold]="isFirst"
                  [class.text-text]="isFirst && store.overdueCount() === 0"
                  [class.text-danger]="isFirst && store.overdueCount() > 0"
                  [class.text-sm]="!isFirst"
                  [class.font-medium]="!isFirst"
                  [class.text-muted]="!isFirst"
                >
                  {{ row.pendingTotal | money: row.currencyCode }}
                </p>
              }
            </div>
            @if (store.overdueCount() > 0) {
              <p class="mt-1 text-xs font-medium text-danger tabular-nums">
                {{ store.overdueCount() }}
                {{ store.overdueCount() === 1 ? 'vencido' : 'vencidos' }}
              </p>
            }
          </fo-card>
        </div>

        @if (store.visibleItems().length === 0) {
          <fo-empty-state
            data-testid="recurring-empty-state"
            icon="repeat"
            title="No hay gastos fijos para este mes"
            description="Cargá alquileres, servicios o suscripciones para ver cuánto tenés por pagar cada mes."
          >
            @if (session.isAdult()) {
              <fo-button variant="primary" (click)="openCreateSheet()">
                <fo-icon name="plus" [size]="16" />
                <span>Nuevo gasto fijo</span>
              </fo-button>
            }
          </fo-empty-state>
        } @else {
          <section aria-label="Listado de gastos fijos del mes" class="space-y-2.5">
            @for (expense of store.visibleItems(); track expense.id) {
              <fo-recurring-expense-item
                [expense]="expense"
                [isAdult]="session.isAdult()"
                (payRequested)="openPaySheet($event)"
                (editRequested)="openEditSheet($event)"
                (archiveRequested)="openArchiveSheet($event)"
              />
            }
          </section>
        }
      }
    </section>

    <fo-bottom-sheet
      [(open)]="paySheetOpen"
      title="Confirmar pago"
      (closed)="resetPaySheet()"
    >
      @if (paySheetOpen() && payingExpense(); as target) {
        <fo-confirm-payment-sheet
          [expense]="target"
          [accounts]="usableAccounts()"
          [defaultAccountId]="payDefaultAccountId()"
          [saving]="paying()"
          [errorMessage]="payError()"
          (confirmed)="submitConfirmPayment($event)"
          (cancelled)="closePaySheet()"
        />
      }
    </fo-bottom-sheet>

    <fo-bottom-sheet
      [(open)]="formSheetOpen"
      [title]="editingExpense() ? 'Editar gasto fijo' : 'Nuevo gasto fijo'"
      (closed)="resetFormSheet()"
    >
      @if (formSheetOpen()) {
        <fo-recurring-expense-form
          [expense]="editingExpense()"
          [categories]="expenseCategories()"
          [accounts]="usableAccounts()"
          [enabledCurrencies]="enabledCurrencies()"
          [baseCurrency]="baseCurrency()"
          [saving]="savingForm()"
          [errorMessage]="formError()"
          (createSubmitted)="submitCreate($event)"
          (updateSubmitted)="submitUpdate($event)"
          (cancelled)="closeFormSheet()"
        />
      }
    </fo-bottom-sheet>

    <fo-bottom-sheet
      [(open)]="archiveSheetOpen"
      title="Archivar gasto fijo"
      (closed)="archivingExpense.set(null)"
    >
      @if (archivingExpense(); as target) {
        <div class="space-y-4">
          <p class="text-sm text-text">
            ¿Querés archivar <strong class="font-semibold">{{ target.name }}</strong>?
          </p>
          <p class="rounded-xl border border-border bg-surface-2 px-3.5 py-2.5 text-sm text-muted">
            Dejará de aparecer en los próximos meses, pero los pagos ya registrados se conservarán en
            tu historial.
          </p>
          <div class="flex items-center justify-end gap-2 pt-1">
            <fo-button
              variant="ghost"
              [disabled]="archiving()"
              (click)="closeArchiveSheet()"
            >
              Cancelar
            </fo-button>
            <fo-button
              variant="danger"
              data-testid="confirm-archive-recurring-btn"
              [loading]="archiving()"
              (click)="confirmArchive()"
            >
              Archivar
            </fo-button>
          </div>
        </div>
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecurringPage {
  protected readonly store = inject(RecurringStore);
  private readonly accountsStore = inject(AccountsStore);
  private readonly categoriesStore = inject(CategoriesStore);
  private readonly viewContext = inject(ViewContextStore);
  protected readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  protected readonly todayMonth = currentYearMonth();

  protected readonly usableAccounts = this.accountsStore.usableFor();
  protected readonly expenseCategories = this.categoriesStore.byKind('EXPENSE');

  protected readonly paySheetOpen = signal<boolean>(false);
  protected readonly payingExpense = signal<RecurringExpense | null>(null);
  protected readonly paying = signal<boolean>(false);
  protected readonly payError = signal<string | null>(null);

  protected readonly formSheetOpen = signal<boolean>(false);
  protected readonly editingExpense = signal<RecurringExpense | null>(null);
  protected readonly savingForm = signal<boolean>(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly archiveSheetOpen = signal<boolean>(false);
  protected readonly archivingExpense = signal<RecurringExpense | null>(null);
  protected readonly archiving = signal<boolean>(false);

  protected readonly baseCurrency = computed<string>(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

  protected readonly enabledCurrencies = computed<readonly string[]>(
    () => this.session.me()?.family.enabledCurrencies ?? ['ARS', 'USD'],
  );

  protected readonly monthLabel = computed<string>(() => formatMonthLabel(this.store.month()));

  protected readonly summaryRows = computed(() => {
    const rows = this.store.summaryByCurrency();
    if (rows.length > 0) {
      return rows;
    }
    return [
      {
        currencyCode: this.baseCurrency(),
        estimatedTotal: 0,
        paidTotal: 0,
        pendingTotal: 0,
      },
    ];
  });

  protected readonly payDefaultAccountId = computed<string | null>(() => {
    const target = this.payingExpense();
    if (!target) {
      return null;
    }
    const currency = target.currencyCode.trim().toUpperCase();
    const candidates = this.usableAccounts().filter(
      (acc) => acc.isActive && acc.currencyCode.trim().toUpperCase() === currency,
    );
    if (target.account && candidates.some((acc) => acc.id === target.account?.id)) {
      return target.account.id;
    }
    const fallback = this.accountsStore.defaultAccountFor(currency);
    if (fallback && candidates.some((acc) => acc.id === fallback.id)) {
      return fallback.id;
    }
    return candidates[0]?.id ?? null;
  });

  constructor() {
    effect(() => {
      const currentView: View = this.viewContext.view();
      void this.loadAll(currentView);
    });
  }

  protected reload(): void {
    void this.store.load();
  }

  protected shiftMonth(delta: -1 | 1): void {
    void this.store.shiftMonth(delta);
  }

  protected goToCurrentMonth(): void {
    void this.store.setMonth(this.todayMonth);
  }

  protected openPaySheet(expense: RecurringExpense): void {
    this.payingExpense.set(expense);
    this.payError.set(null);
    this.paySheetOpen.set(true);
  }

  protected closePaySheet(): void {
    this.paySheetOpen.set(false);
    this.resetPaySheet();
  }

  protected resetPaySheet(): void {
    this.payingExpense.set(null);
    this.payError.set(null);
  }

  protected async submitConfirmPayment(payload: ConfirmPaymentPayload): Promise<void> {
    const target = this.payingExpense();
    if (!target) {
      return;
    }

    this.paying.set(true);
    this.payError.set(null);
    try {
      await this.store.confirmPayment(target.id, payload);
      this.closePaySheet();
      this.toast.success(`Pago de ${target.name} registrado.`);
    } catch (err) {
      this.payError.set(
        extractProblemMessage(err, 'No pudimos confirmar el pago. Intentá de nuevo.'),
      );
    } finally {
      this.paying.set(false);
    }
  }

  protected openCreateSheet(): void {
    this.editingExpense.set(null);
    this.formError.set(null);
    this.formSheetOpen.set(true);
  }

  protected openEditSheet(expense: RecurringExpense): void {
    this.editingExpense.set(expense);
    this.formError.set(null);
    this.formSheetOpen.set(true);
  }

  protected closeFormSheet(): void {
    this.formSheetOpen.set(false);
    this.resetFormSheet();
  }

  protected resetFormSheet(): void {
    this.editingExpense.set(null);
    this.formError.set(null);
  }

  protected async submitCreate(payload: RecurringExpenseCreate): Promise<void> {
    this.savingForm.set(true);
    this.formError.set(null);
    try {
      await this.store.create(payload);
      this.closeFormSheet();
      this.toast.success('Gasto fijo creado.');
    } catch (err) {
      this.formError.set(
        extractProblemMessage(err, 'No pudimos crear el gasto fijo. Intentá de nuevo.'),
      );
    } finally {
      this.savingForm.set(false);
    }
  }

  protected async submitUpdate(patch: RecurringExpensePatch): Promise<void> {
    const target = this.editingExpense();
    if (!target) {
      return;
    }

    this.savingForm.set(true);
    this.formError.set(null);
    try {
      await this.store.update(target.id, patch);
      this.closeFormSheet();
      this.toast.success('Gasto fijo actualizado.');
    } catch (err) {
      this.formError.set(
        extractProblemMessage(err, 'No pudimos actualizar el gasto fijo. Intentá de nuevo.'),
      );
    } finally {
      this.savingForm.set(false);
    }
  }

  protected openArchiveSheet(expense: RecurringExpense): void {
    this.archivingExpense.set(expense);
    this.archiveSheetOpen.set(true);
  }

  protected closeArchiveSheet(): void {
    this.archiveSheetOpen.set(false);
    this.archivingExpense.set(null);
  }

  protected async confirmArchive(): Promise<void> {
    const target = this.archivingExpense();
    if (!target) {
      return;
    }

    this.archiving.set(true);
    try {
      await this.store.archive(target.id);
      this.closeArchiveSheet();
      this.toast.success('Gasto fijo archivado.');
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos archivar el gasto fijo. Intentá de nuevo.'),
      );
    } finally {
      this.archiving.set(false);
    }
  }

  private async loadAll(view: View): Promise<void> {
    await Promise.all([
      this.store.load(),
      this.accountsStore.load(view).catch(() => undefined),
      this.categoriesStore.load().catch(() => undefined),
    ]);
  }
}
