import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ViewContextStore } from '../../../core/state/view-context.store';
import { SessionStore } from '../../../core/state/session.store';
import { BottomSheetComponent } from '../../../shared/ui/bottom-sheet/bottom-sheet.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SegmentedOption, SegmentedToggleComponent } from '../../../shared/ui/segmented-toggle/segmented-toggle.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { GoalCardComponent } from './components/goal-card.component';
import {
  GoalFormSheetComponent,
  GoalFormSubmitPayload,
} from './components/goal-form-sheet.component';
import { GoalContributionSheetComponent } from './components/goal-contribution-sheet.component';
import { GoalHistorySheetComponent } from './components/goal-history-sheet.component';
import {
  Contribution,
  ContributionCreate,
  GoalStatus,
  SavingsGoal,
} from './data-access/goals.models';
import { GoalsStore } from './data-access/goals.store';
import { extractProblemMessage } from './utils/problem-message';

const STATUS_FILTER_OPTIONS: readonly SegmentedOption<GoalStatus | 'ALL'>[] = [
  { value: 'ACTIVE', label: 'Activas' },
  { value: 'ALL', label: 'Todas' },
  { value: 'COMPLETED', label: 'Completadas' },
] as const;

@Component({
  selector: 'fo-goals-page',
  standalone: true,
  imports: [
    GoalCardComponent,
    GoalFormSheetComponent,
    GoalContributionSheetComponent,
    GoalHistorySheetComponent,
    BottomSheetComponent,
    ButtonComponent,
    EmptyStateComponent,
    IconComponent,
    SegmentedToggleComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="w-full space-y-3 lg:space-y-4">
      <!-- Header -->
      <header class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="fo-page-title text-text lg:sr-only">Metas</h1>
          <p class="mt-0.5 text-[11px] text-muted">
            Ahorrá en unidades estables con seguimiento de progreso real y aportes rápidos.
          </p>
        </div>

        <div class="flex items-center gap-2">
          <fo-button
            variant="primary"
            type="button"
            (click)="openCreateSheet()"
            ariaLabel="Crear nueva meta de ahorro"
          >
            <fo-icon name="plus" [size]="16" />
            <span>Nueva meta</span>
          </fo-button>
        </div>
      </header>

      <!-- Filter Controls -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div class="w-full sm:w-72">
          <fo-segmented-toggle
            [options]="filterOptions"
            [value]="store.statusFilter()"
            (valueChange)="onStatusFilterChange($event)"
            size="sm"
            ariaLabel="Filtrar por estado"
          />
        </div>

        <span class="text-xs text-muted tabular-nums">
          {{ store.filteredGoals().length }}
          {{ store.filteredGoals().length === 1 ? 'meta encontrada' : 'metas encontradas' }}
        </span>
      </div>

      <!-- Main Content -->
      @if (store.loading() && store.goals().length === 0) {
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <fo-skeleton height="14rem" />
          <fo-skeleton height="14rem" />
          <fo-skeleton height="14rem" />
        </div>
      } @else if (store.error(); as errorMessage) {
        <div class="rounded-xl border border-danger/30 bg-danger-soft p-4 text-center">
          <p class="text-sm text-danger font-medium">{{ errorMessage }}</p>
          <div class="mt-3">
            <fo-button variant="ghost" size="sm" type="button" (click)="reload()">
              Reintentar
            </fo-button>
          </div>
        </div>
      } @else if (store.filteredGoals().length === 0) {
        <fo-empty-state
          icon="piggy-bank"
          title="Sin metas en este filtro"
          description="Establecé tus objetivos de ahorro en dólares, UVAs o pesos ajustados para proteger tu dinero."
        >
          <fo-button variant="primary" size="sm" type="button" (click)="openCreateSheet()">
            <fo-icon name="plus" [size]="14" />
            <span>Crear meta</span>
          </fo-button>
        </fo-empty-state>
      } @else {
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          @for (goal of store.filteredGoals(); track goal.id) {
            <fo-goal-card
              [goal]="goal"
              [baseCurrency]="baseCurrency()"
              (contribute)="openContributeSheet($event)"
              (viewHistory)="openHistorySheet($event)"
              (edit)="openEditSheet($event)"
            />
          }
        </div>
      }
    </section>

    <!-- Create / Edit Sheet -->
    <fo-bottom-sheet
      [(open)]="formSheetOpen"
      [title]="editingGoal() ? 'Editar meta' : 'Nueva meta de ahorro'"
      (closed)="closeFormSheet()"
    >
      @if (formSheetOpen()) {
        <fo-goal-form-sheet
          [goal]="editingGoal()"
          [saving]="formSaving()"
          (submitted)="submitGoalForm($event)"
          (cancelled)="closeFormSheet()"
        />
      }
    </fo-bottom-sheet>

    <!-- Fast Contribute Sheet -->
    <fo-bottom-sheet
      [(open)]="contributeSheetOpen"
      title="Aportar a la meta"
      (closed)="closeContributeSheet()"
    >
      @if (contributeSheetOpen()) {
        <fo-goal-contribution-sheet
          [goal]="activeGoal()"
          [currencies]="familyCurrencies()"
          [saving]="contributeSaving()"
          (submitted)="submitContribution($event)"
          (cancelled)="closeContributeSheet()"
        />
      }
    </fo-bottom-sheet>

    <!-- History Sheet -->
    <fo-bottom-sheet
      [(open)]="historySheetOpen"
      title="Historial de aportes"
      (closed)="closeHistorySheet()"
    >
      @if (historySheetOpen()) {
        <fo-goal-history-sheet
          [goal]="activeGoal()"
          [contributions]="contributionsList()"
          [loading]="contributionsLoading()"
          [deletingId]="deletingContributionId()"
          (deleteContribution)="deleteContribution($event)"
          (closed)="closeHistorySheet()"
        />
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GoalsPage {
  protected readonly store = inject(GoalsStore);
  private readonly viewContext = inject(ViewContextStore);
  private readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  protected readonly filterOptions = STATUS_FILTER_OPTIONS;

  // Form sheet state
  protected readonly formSheetOpen = signal<boolean>(false);
  protected readonly editingGoal = signal<SavingsGoal | null>(null);
  protected readonly formSaving = signal<boolean>(false);

  // Contribute sheet state
  protected readonly contributeSheetOpen = signal<boolean>(false);
  protected readonly activeGoal = signal<SavingsGoal | null>(null);
  protected readonly contributeSaving = signal<boolean>(false);

  // History sheet state
  protected readonly historySheetOpen = signal<boolean>(false);
  protected readonly contributionsList = signal<Contribution[]>([]);
  protected readonly contributionsLoading = signal<boolean>(false);
  protected readonly deletingContributionId = signal<string | null>(null);

  protected readonly baseCurrency = computed(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

  protected readonly familyCurrencies = computed(() => {
    const fam = this.session.me()?.family;
    if (!fam) {
      return ['ARS', 'USD'];
    }
    const set = new Set<string>([fam.baseCurrency, fam.referenceCurrency, ...fam.enabledCurrencies]);
    return Array.from(set);
  });

  constructor() {
    effect(() => {
      const currentView = this.viewContext.view();
      void this.store.load(currentView, this.store.statusFilter());
    });
  }

  protected reload(): void {
    void this.store.load(this.viewContext.view(), this.store.statusFilter());
  }

  protected onStatusFilterChange(filter: GoalStatus | 'ALL'): void {
    this.store.setStatusFilter(filter);
    void this.store.load(this.viewContext.view(), filter);
  }

  // Create / Edit handlers
  protected openCreateSheet(): void {
    this.editingGoal.set(null);
    this.formSheetOpen.set(true);
  }

  protected openEditSheet(goal: SavingsGoal): void {
    this.editingGoal.set(goal);
    this.formSheetOpen.set(true);
  }

  protected closeFormSheet(): void {
    this.formSheetOpen.set(false);
    this.editingGoal.set(null);
  }

  protected async submitGoalForm(payload: GoalFormSubmitPayload): Promise<void> {
    this.formSaving.set(true);
    try {
      if (payload.mode === 'create') {
        await this.store.create(payload.data);
        this.toast.success('Meta de ahorro creada.');
      } else {
        await this.store.update(payload.id, payload.data);
        this.toast.success('Meta actualizada.');
      }
      this.closeFormSheet();
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos guardar la meta. Verificá los datos e intentá de nuevo.'),
      );
    } finally {
      this.formSaving.set(false);
    }
  }

  // Contribute handlers
  protected openContributeSheet(goal: SavingsGoal): void {
    this.activeGoal.set(goal);
    this.contributeSheetOpen.set(true);
  }

  protected closeContributeSheet(): void {
    this.contributeSheetOpen.set(false);
    this.activeGoal.set(null);
  }

  protected async submitContribution(payload: ContributionCreate): Promise<void> {
    const goal = this.activeGoal();
    if (!goal) {
      return;
    }

    this.contributeSaving.set(true);
    try {
      await this.store.addContribution(goal.id, payload);
      this.toast.success('¡Aporte registrado con éxito!');
      this.closeContributeSheet();
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos registrar el aporte. Intentá de nuevo.'),
      );
    } finally {
      this.contributeSaving.set(false);
    }
  }

  // History handlers
  protected async openHistorySheet(goal: SavingsGoal): Promise<void> {
    this.activeGoal.set(goal);
    this.historySheetOpen.set(true);
    this.contributionsLoading.set(true);

    try {
      const list = await this.store.listContributions(goal.id);
      this.contributionsList.set(list);
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos cargar el historial de aportes.'),
      );
    } finally {
      this.contributionsLoading.set(false);
    }
  }

  protected closeHistorySheet(): void {
    this.historySheetOpen.set(false);
    this.activeGoal.set(null);
    this.contributionsList.set([]);
  }

  protected async deleteContribution(contribution: Contribution): Promise<void> {
    const goal = this.activeGoal();
    if (!goal) {
      return;
    }

    this.deletingContributionId.set(contribution.id);
    try {
      await this.store.deleteContribution(goal.id, contribution.id);
      this.contributionsList.update((curr) => curr.filter((c) => c.id !== contribution.id));
      this.toast.success('Aporte eliminado.');
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos eliminar el aporte.'),
      );
    } finally {
      this.deletingContributionId.set(null);
    }
  }
}
