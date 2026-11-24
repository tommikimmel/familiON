import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FinanceEvents } from '../../../core/finance/finance-events';
import { SessionStore } from '../../../core/state/session.store';
import { View, ViewContextStore } from '../../../core/state/view-context.store';
import {
  BottomSheetComponent,
  ButtonComponent,
  EmptyStateComponent,
  SegmentedOption,
  SegmentedToggleComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { CommitmentChartComponent } from './components/commitment-chart.component';
import { InstallmentDetailSheetComponent } from './components/installment-detail-sheet.component';
import { InstallmentPlanCardComponent } from './components/installment-plan-card.component';
import {
  CancelRemainingPayload,
  InstallmentPlan,
  InstallmentPlanStatus,
} from './data-access/installments.models';
import { InstallmentsStore } from './data-access/installments.store';
import { extractProblemMessage } from './utils/installments-format';

const STATUS_OPTIONS: readonly SegmentedOption<InstallmentPlanStatus>[] = [
  { value: 'ACTIVE', label: 'Activas' },
  { value: 'COMPLETED', label: 'Terminadas' },
  { value: 'CANCELLED', label: 'Canceladas' },
];

@Component({
  selector: 'fo-installments-page',
  standalone: true,
  imports: [
    BottomSheetComponent,
    ButtonComponent,
    CommitmentChartComponent,
    EmptyStateComponent,
    InstallmentDetailSheetComponent,
    InstallmentPlanCardComponent,
    SegmentedToggleComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-5xl space-y-6">
      <header class="space-y-1">
        <h1 class="text-xl font-semibold text-text">Cuotas</h1>
        <p class="text-sm text-muted">
          Seguí tus compras en cuotas, el recargo real y lo que ya tenés comprometido mes a mes.
        </p>
      </header>

      @if (store.loading() && store.plans().length === 0 && store.commitment().length === 0) {
        <div class="space-y-5" aria-busy="true" aria-label="Cargando compras en cuotas">
          <fo-skeleton height="14rem" rounded="lg" />
          <fo-skeleton height="2.75rem" rounded="full" />
          <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
            <fo-skeleton height="11rem" rounded="lg" />
            <fo-skeleton height="11rem" rounded="lg" />
          </div>
        </div>
      } @else if (store.error(); as errorMsg) {
        <fo-empty-state
          icon="alert-circle"
          title="No pudimos cargar tus cuotas"
          [description]="errorMsg"
        >
          <fo-button variant="primary" (click)="reload()">Reintentar</fo-button>
        </fo-empty-state>
      } @else {
        <fo-commitment-chart
          [commitment]="store.commitment()"
          [pendingTotals]="store.pendingTotals()"
          [baseCurrency]="baseCurrency()"
        />

        <div class="max-w-md">
          <fo-segmented-toggle
            [options]="statusOptions"
            [value]="store.status()"
            (valueChange)="onStatusChange($event)"
            ariaLabel="Estado de compras en cuotas"
          />
        </div>

        @if (store.listLoading()) {
          <div class="grid grid-cols-1 gap-3 lg:grid-cols-2" aria-busy="true">
            <fo-skeleton height="11rem" rounded="lg" />
            <fo-skeleton height="11rem" rounded="lg" />
          </div>
        } @else if (store.plans().length === 0) {
          <fo-empty-state
            data-testid="installments-empty-state"
            icon="credit-card"
            [title]="emptyTitle()"
            [description]="emptyDescription()"
          />
        } @else {
          <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
            @for (plan of store.plans(); track plan.id) {
              <fo-installment-plan-card
                [plan]="plan"
                [showAttributedTo]="isFamilyView()"
                (selected)="openDetail($event)"
              />
            }
          </div>
        }
      }
    </section>

    <fo-bottom-sheet
      [(open)]="detailSheetOpen"
      [title]="sheetTitle()"
      (closed)="onSheetClosed()"
    >
      @if (detailSheetOpen()) {
        <fo-installment-detail-sheet
          [detail]="store.selectedDetail()"
          [loading]="store.detailLoading()"
          [error]="store.detailError()"
          [mutating]="store.mutating()"
          [mutationError]="mutationError()"
          (cancelRemaining)="onCancelRemaining($event)"
          (retry)="retryDetail()"
        />
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstallmentsPage {
  protected readonly store = inject(InstallmentsStore);
  private readonly viewContext = inject(ViewContextStore);
  private readonly session = inject(SessionStore);
  private readonly financeEvents = inject(FinanceEvents);
  private readonly toast = inject(ToastService);

  protected readonly statusOptions = STATUS_OPTIONS;
  protected readonly detailSheetOpen = signal<boolean>(false);
  protected readonly selectedPlanId = signal<string | null>(null);
  protected readonly selectedPlanTitle = signal<string>('');
  protected readonly mutationError = signal<string | null>(null);

  private lastHandledEventAt = 0;

  protected readonly isFamilyView = computed<boolean>(() => this.viewContext.view() === 'FAMILY');

  protected readonly baseCurrency = computed<string>(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

  protected readonly sheetTitle = computed<string>(
    () => this.store.selectedDetail()?.description ?? (this.selectedPlanTitle() || 'Detalle del plan'),
  );

  protected readonly emptyTitle = computed<string>(() => {
    switch (this.store.status()) {
      case 'ACTIVE':
        return 'No tenés cuotas activas';
      case 'COMPLETED':
        return 'Todavía no tenés planes terminados';
      case 'CANCELLED':
        return 'No hay planes cancelados';
    }
  });

  protected readonly emptyDescription = computed<string>(() => {
    switch (this.store.status()) {
      case 'ACTIVE':
        return 'Cuando registres una compra en cuotas con tarjeta de crédito va a aparecer acá con su cronograma.';
      case 'COMPLETED':
        return 'Los planes que completes se van a archivar automáticamente en esta pestaña.';
      case 'CANCELLED':
        return 'Los planes que canceles o precanceles van a quedar registrados acá.';
    }
  });

  constructor() {
    effect(() => {
      const currentView: View = this.viewContext.view();
      void this.store.load(currentView);
    });

    effect(() => {
      const event = this.financeEvents.last();
      if (!event || event.at <= this.lastHandledEventAt) {
        return;
      }
      if (event.type === 'transaction-created') {
        this.lastHandledEventAt = event.at;
        void this.store.load(this.viewContext.view());
      }
    });
  }

  protected reload(): void {
    void this.store.load(this.viewContext.view());
  }

  protected onStatusChange(nextStatus: InstallmentPlanStatus): void {
    void this.store.setStatus(nextStatus, this.viewContext.view());
  }

  protected openDetail(plan: InstallmentPlan): void {
    this.selectedPlanId.set(plan.id);
    this.selectedPlanTitle.set(plan.description);
    this.mutationError.set(null);
    this.detailSheetOpen.set(true);
    void this.store.loadDetail(plan.id);
  }

  protected retryDetail(): void {
    const planId = this.selectedPlanId();
    if (planId) {
      void this.store.loadDetail(planId);
    }
  }

  protected onSheetClosed(): void {
    this.selectedPlanId.set(null);
    this.selectedPlanTitle.set('');
    this.mutationError.set(null);
    this.store.clearDetail();
  }

  protected async onCancelRemaining(payload: CancelRemainingPayload): Promise<void> {
    const planId = this.selectedPlanId();
    if (!planId) {
      return;
    }

    this.mutationError.set(null);
    try {
      await this.store.cancelRemaining(planId, payload);
      this.detailSheetOpen.set(false);
      this.onSheetClosed();
      this.toast.success(
        payload.mode === 'PREPAY' ? 'Plan precancelado.' : 'Cuotas restantes canceladas.',
      );
    } catch (err) {
      this.mutationError.set(
        extractProblemMessage(
          err,
          payload.mode === 'PREPAY'
            ? 'No pudimos precancelar el plan. Intentá de nuevo.'
            : 'No pudimos cancelar las cuotas restantes. Intentá de nuevo.',
        ),
      );
    }
  }
}
