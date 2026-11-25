import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { SessionStore } from '../../../core/state/session.store';
import { BottomSheetComponent } from '../../../shared/ui/bottom-sheet/bottom-sheet.component';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { AllowanceCardComponent } from './components/allowance-card.component';
import { AllowanceChildViewComponent } from './components/allowance-child-view.component';
import { AllowanceDisburseSheetComponent } from './components/allowance-disburse-sheet.component';
import {
  AllowanceFormSheetComponent,
  AllowanceFormSubmitPayload,
} from './components/allowance-form-sheet.component';
import { Allowance, DisbursePayload } from './data-access/allowances.models';
import { AllowancesStore } from './data-access/allowances.store';
import { extractProblemMessage } from './utils/problem-message';

@Component({
  selector: 'fo-allowances-page',
  standalone: true,
  imports: [
    AllowanceCardComponent,
    AllowanceChildViewComponent,
    AllowanceDisburseSheetComponent,
    AllowanceFormSheetComponent,
    BottomSheetComponent,
    ButtonComponent,
    EmptyStateComponent,
    IconComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="w-full space-y-3 lg:space-y-4">
      <!-- Header -->
      <header class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="fo-page-title text-text lg:sr-only">Mensualidades</h1>
          <p class="mt-0.5 text-[11px] text-muted">
            {{
              isAdult()
                ? 'Gestioná y acreditá las mensualidades de tus hijos de forma periódica.'
                : 'Consultá el estado y saldo disponible de tu mensualidad.'
            }}
          </p>
        </div>

        @if (isAdult()) {
          <div class="flex items-center gap-2">
            <fo-button
              variant="primary"
              type="button"
              (click)="openCreateSheet()"
              ariaLabel="Configurar nueva mensualidad"
            >
              <fo-icon name="plus" [size]="16" />
              <span>Nueva mensualidad</span>
            </fo-button>
          </div>
        }
      </header>

      <!-- Loading / Error States -->
      @if (store.loading() && store.allowances().length === 0) {
        <div class="space-y-3">
          <fo-skeleton height="7rem" />
          <fo-skeleton height="7rem" />
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
      } @else {
        <!-- VIEW: HIJO -->
        @if (!isAdult()) {
          <fo-allowance-child-view [allowance]="childAllowance()" />
        } @else {
          <!-- VIEW: ADULTO -->
          @if (store.allowances().length === 0) {
            <fo-empty-state
              icon="banknote"
              title="Sin mensualidades configuradas"
              description="Podés definir un monto periódico para cada hijo, con reglas de ajuste por inflación y acreditación automática o manual."
            >
              <fo-button variant="primary" size="sm" type="button" (click)="openCreateSheet()">
                <fo-icon name="plus" [size]="14" />
                <span>Configurar mensualidad</span>
              </fo-button>
            </fo-empty-state>
          } @else {
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
              @for (item of store.allowances(); track item.id) {
                <fo-allowance-card
                  [allowance]="item"
                  (disburse)="openDisburseSheet($event)"
                  (edit)="openEditSheet($event)"
                />
              }
            </div>
          }
        }
      }
    </section>

    <!-- Create / Edit Sheet -->
    <fo-bottom-sheet
      [(open)]="formSheetOpen"
      [title]="editingAllowance() ? 'Editar mensualidad' : 'Nueva mensualidad'"
      (closed)="closeFormSheet()"
    >
      @if (formSheetOpen()) {
        <fo-allowance-form-sheet
          [allowance]="editingAllowance()"
          [children]="store.children()"
          [accounts]="store.accounts()"
          [saving]="formSaving()"
          (submitted)="submitAllowanceForm($event)"
          (delete)="openDeleteSheet($event)"
          (cancelled)="closeFormSheet()"
        />
      }
    </fo-bottom-sheet>

    <!-- Disburse Sheet -->
    <fo-bottom-sheet
      [(open)]="disburseSheetOpen"
      title="Acreditar mensualidad"
      (closed)="closeDisburseSheet()"
    >
      @if (disburseSheetOpen()) {
        <fo-allowance-disburse-sheet
          [allowance]="disbursingAllowance()"
          [saving]="disburseSaving()"
          (confirmed)="confirmDisburse($event)"
          (cancelled)="closeDisburseSheet()"
        />
      }
    </fo-bottom-sheet>

    <!-- Delete Confirmation Sheet -->
    <fo-bottom-sheet
      [(open)]="deleteSheetOpen"
      title="Eliminar mensualidad"
      (closed)="closeDeleteSheet()"
    >
      @if (deletingAllowance(); as target) {
        <div class="space-y-4">
          <p class="text-sm text-text">
            ¿Querés eliminar la mensualidad asignada a
            <strong class="font-semibold">{{ target.child.fullName }}</strong>?
          </p>
          <p class="rounded-xl border border-border bg-surface-2 px-3.5 py-2.5 text-xs text-muted">
            Esta acción detendrá las futuras acreditaciones automáticas y periódicas de esta mensualidad.
          </p>
          <div class="flex items-center justify-end gap-2 pt-1">
            <fo-button
              variant="ghost"
              type="button"
              [disabled]="deletingSaving()"
              (click)="closeDeleteSheet()"
            >
              Cancelar
            </fo-button>
            <fo-button
              variant="danger"
              type="button"
              [loading]="deletingSaving()"
              (click)="confirmDelete()"
            >
              Eliminar mensualidad
            </fo-button>
          </div>
        </div>
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AllowancesPage {
  protected readonly store = inject(AllowancesStore);
  protected readonly session = inject(SessionStore);
  private readonly toast = inject(ToastService);

  protected readonly isAdult = computed(() => this.session.isAdult());
  protected readonly currentUserId = computed(() => this.session.me()?.id ?? null);

  protected readonly childAllowance = computed(() => {
    const userId = this.currentUserId();
    const list = this.store.allowances();
    if (!userId) {
      return list[0] ?? null;
    }
    return list.find((a) => a.child.id === userId) ?? list[0] ?? null;
  });

  // Sheet states
  protected readonly formSheetOpen = signal<boolean>(false);
  protected readonly editingAllowance = signal<Allowance | null>(null);
  protected readonly formSaving = signal<boolean>(false);

  protected readonly disburseSheetOpen = signal<boolean>(false);
  protected readonly disbursingAllowance = signal<Allowance | null>(null);
  protected readonly disburseSaving = signal<boolean>(false);

  protected readonly deleteSheetOpen = signal<boolean>(false);
  protected readonly deletingAllowance = signal<Allowance | null>(null);
  protected readonly deletingSaving = signal<boolean>(false);

  constructor() {
    effect(() => {
      // Trigger loading allowances
      void this.store.loadAll();
    });
  }

  protected reload(): void {
    void this.store.loadAll();
  }

  // Create / Edit
  protected openCreateSheet(): void {
    this.editingAllowance.set(null);
    this.formSheetOpen.set(true);
  }

  protected openEditSheet(allowance: Allowance): void {
    this.editingAllowance.set(allowance);
    this.formSheetOpen.set(true);
  }

  protected closeFormSheet(): void {
    this.formSheetOpen.set(false);
    this.editingAllowance.set(null);
  }

  protected async submitAllowanceForm(payload: AllowanceFormSubmitPayload): Promise<void> {
    this.formSaving.set(true);
    try {
      if (payload.mode === 'create') {
        await this.store.create(payload.data);
        this.toast.success('Mensualidad configurada con éxito.');
      } else {
        await this.store.update(payload.id, payload.data);
        this.toast.success('Mensualidad actualizada.');
      }
      this.closeFormSheet();
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos guardar la mensualidad. Intentá de nuevo.'),
      );
    } finally {
      this.formSaving.set(false);
    }
  }

  // Disburse
  protected openDisburseSheet(allowance: Allowance): void {
    this.disbursingAllowance.set(allowance);
    this.disburseSheetOpen.set(true);
  }

  protected closeDisburseSheet(): void {
    this.disburseSheetOpen.set(false);
    this.disbursingAllowance.set(null);
  }

  protected async confirmDisburse(payload: DisbursePayload): Promise<void> {
    const allowance = this.disbursingAllowance();
    if (!allowance) {
      return;
    }

    this.disburseSaving.set(true);
    try {
      await this.store.disburse(allowance.id, payload);
      this.toast.success('Mensualidad acreditada correctamente.');
      this.closeDisburseSheet();
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos acreditar la mensualidad. Intentá de nuevo.'),
      );
    } finally {
      this.disburseSaving.set(false);
    }
  }

  // Delete
  protected openDeleteSheet(allowance: Allowance): void {
    this.deletingAllowance.set(allowance);
    this.closeFormSheet();
    this.deleteSheetOpen.set(true);
  }

  protected closeDeleteSheet(): void {
    this.deleteSheetOpen.set(false);
    this.deletingAllowance.set(null);
  }

  protected async confirmDelete(): Promise<void> {
    const target = this.deletingAllowance();
    if (!target) {
      return;
    }

    this.deletingSaving.set(true);
    try {
      await this.store.remove(target.id);
      this.toast.success('Mensualidad eliminada.');
      this.closeDeleteSheet();
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos eliminar la mensualidad.'),
      );
    } finally {
      this.deletingSaving.set(false);
    }
  }
}
