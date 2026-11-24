import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Account, Category, CategoryKind } from '../../../core/finance/finance.models';
import {
  TransactionItem,
  TransactionPatch,
  TransactionScope,
} from '../../../core/finance/transactions.models';
import { Lens } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  BottomSheetComponent,
  ButtonComponent,
  IconComponent,
  SkeletonComponent,
} from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import {
  TransactionEditCapabilities,
  TransactionEditDraft,
  buildTransactionPatch,
  fallbackTransactionIcon,
  formatLongDate,
  formatReferenceSnapshot,
  formatSignedAmount,
  getTransactionEditCapabilities,
  normalizeEditableAmount,
  resolveLensDisplayUnit,
  transactionAmountTone,
  transactionStatusLabel,
  transactionTypeLabel,
} from '../data-access/transactions.grouping';
import { TransactionsFamilyMember } from '../data-access/transactions-members.api';

type SheetMode = 'detail' | 'edit' | 'confirm-delete';

function formatDraftAmount(amount: string): string {
  const num = toNumber(amount);
  if (!Number.isFinite(num) || num <= 0) {
    return '';
  }
  return Number.isInteger(num) ? String(num) : String(Number(num.toFixed(4)));
}

@Component({
  selector: 'fo-transaction-detail-sheet',
  standalone: true,
  imports: [
    BottomSheetComponent,
    ButtonComponent,
    IconComponent,
    MoneyPipe,
    RouterLink,
    SkeletonComponent,
  ],
  template: `
    <fo-bottom-sheet
      [open]="transaction() !== null"
      [title]="sheetTitle()"
      (closed)="onClose()"
    >
      @if (transaction(); as tx) {
        @if (mode() === 'detail') {
          <div data-testid="transaction-detail-view" class="space-y-4">
            <!-- Hero header -->
            <div class="flex items-start gap-3">
              <span
                class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl"
                [style.background-color]="iconBg(tx)"
                [style.color]="iconColor(tx)"
              >
                <fo-icon [name]="tx.category?.icon ?? fallbackIcon(tx)" [size]="20" />
              </span>

              <div class="min-w-0 flex-1">
                <h3 class="truncate text-base font-semibold text-text">
                  {{ primaryTitle(tx) }}
                </h3>
                <div class="mt-1 flex flex-wrap items-center gap-1.5">
                  <span
                    class="rounded-md bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-muted"
                  >
                    {{ typeLabel(tx) }}
                  </span>
                  <span
                    class="rounded-md px-2 py-0.5 text-[11px] font-semibold"
                    [class.bg-warning/15]="tx.status === 'PROJECTED'"
                    [class.text-warning]="tx.status === 'PROJECTED'"
                    [class.bg-surface-2]="tx.status !== 'PROJECTED'"
                    [class.text-muted]="tx.status !== 'PROJECTED'"
                  >
                    {{ statusLabel(tx) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Amount + active lens + reference snapshot -->
            <div class="rounded-2xl border border-border bg-surface-2/50 p-4">
              <p
                data-testid="detail-amount"
                class="text-2xl font-bold tracking-tight tabular-nums"
                [class.text-success]="amountTone(tx) === 'positive'"
                [class.text-danger]="amountTone(tx) === 'negative'"
                [class.text-text]="amountTone(tx) === 'neutral'"
              >
                {{ signedAmount(tx) }}
              </p>

              @if (showLensSubtitle(tx)) {
                <p class="mt-1 text-sm text-muted tabular-nums">
                  En lente activa:
                  <strong class="font-medium text-text">
                    {{ tx.lensApproximated ? '~' : ''
                    }}{{ tx.lensAmount | money: lensUnit(tx) }}
                  </strong>
                </p>
              }

              @if (snapshotLoading()) {
                <div class="mt-2.5">
                  <fo-skeleton width="65%" height="1rem" />
                </div>
              } @else if (referenceSnapshotText(); as snapshotLabel) {
                <p
                  data-testid="reference-snapshot"
                  class="mt-2.5 inline-flex items-center gap-1.5 rounded-lg bg-accent-soft px-2.5 py-1 text-xs font-medium text-accent tabular-nums"
                >
                  <fo-icon name="sparkles" [size]="13" />
                  <span>{{ snapshotLabel }}</span>
                </p>
              }
            </div>

            <!-- Metadata details -->
            <dl class="divide-y divide-border/70 text-sm">
              <div class="flex items-center justify-between gap-3 py-2.5">
                <dt class="text-muted">Fecha</dt>
                <dd class="font-medium text-text tabular-nums">
                  {{ formatLongDateLabel(tx.transactionDate) }}
                </dd>
              </div>

              <div class="flex items-center justify-between gap-3 py-2.5">
                <dt class="text-muted">Categoría</dt>
                <dd class="font-medium text-text">
                  @if (tx.category; as cat) {
                    <span class="inline-flex items-center gap-1.5">
                      <span
                        class="h-2.5 w-2.5 rounded-full"
                        [style.background-color]="cat.colorHex || 'var(--fo-accent)'"
                        aria-hidden="true"
                      ></span>
                      <span>{{ cat.name }}</span>
                    </span>
                  } @else {
                    <span class="text-muted">Sin categoría</span>
                  }
                </dd>
              </div>

              <div class="flex items-center justify-between gap-3 py-2.5">
                <dt class="text-muted">Cuenta</dt>
                <dd class="font-medium text-text">{{ tx.account.name }}</dd>
              </div>

              <div class="flex items-center justify-between gap-3 py-2.5">
                <dt class="text-muted">Atribuido a</dt>
                <dd class="font-medium text-text">{{ tx.attributedTo.fullName }}</dd>
              </div>

              <div class="flex items-center justify-between gap-3 py-2.5">
                <dt class="text-muted">Pagado por</dt>
                <dd class="font-medium text-text">{{ tx.paidBy.fullName }}</dd>
              </div>

              <div class="flex items-center justify-between gap-3 py-2.5">
                <dt class="text-muted">Alcance</dt>
                <dd class="font-medium text-text">
                  {{ tx.scope === 'FAMILY' ? 'Familia' : 'Personal' }}
                </dd>
              </div>

              @if (tx.installment; as inst) {
                <div class="flex items-center justify-between gap-3 py-2.5">
                  <dt class="text-muted">Cuota</dt>
                  <dd class="font-semibold text-accent tabular-nums">
                    Cuota {{ inst.number }} de {{ inst.total }}
                  </dd>
                </div>
              }

              @if (tx.description) {
                <div class="py-2.5">
                  <dt class="text-muted">Descripción</dt>
                  <dd class="mt-1 text-text">{{ tx.description }}</dd>
                </div>
              }

              @if (tx.notes) {
                <div class="py-2.5">
                  <dt class="text-muted">Notas</dt>
                  <dd class="mt-1 whitespace-pre-wrap text-text">{{ tx.notes }}</dd>
                </div>
              }
            </dl>

            @if (tx.installment) {
              <div
                data-testid="installment-delete-notice"
                class="flex items-start gap-2.5 rounded-xl border border-accent/30 bg-accent-soft/70 p-3 text-xs text-text"
              >
                <fo-icon name="credit-card" [size]="16" class="mt-0.5 shrink-0 text-accent" />
                <div class="space-y-1">
                  <p class="font-medium">Las cuotas se gestionan desde Plan → Cuotas</p>
                  <a
                    routerLink="/plan/installments"
                    data-testid="installment-plan-link"
                    (click)="onClose()"
                    class="fo-focus-ring inline-flex items-center gap-1 font-semibold text-accent underline-offset-2 hover:underline"
                  >
                    <span>Ir a Plan → Cuotas</span>
                    <fo-icon name="chevron-right" [size]="13" />
                  </a>
                </div>
              </div>
            }

            <!-- Actions -->
            @if (canModify()) {
              <div class="flex items-center justify-end gap-2 border-t border-border pt-4">
                @if (!tx.installment) {
                  <fo-button
                    data-testid="detail-delete-btn"
                    variant="ghost"
                    size="md"
                    (click)="enterDeleteConfirm()"
                  >
                    <fo-icon name="trash-2" [size]="16" class="text-danger" />
                    <span class="text-danger">Eliminar</span>
                  </fo-button>
                }
                <fo-button
                  data-testid="detail-edit-btn"
                  variant="primary"
                  size="md"
                  (click)="enterEdit()"
                >
                  <fo-icon name="pencil" [size]="16" />
                  <span>Editar</span>
                </fo-button>
              </div>
            } @else {
              <p class="border-t border-border pt-3 text-xs text-muted">
                Solo el autor del movimiento o un adulto puede editarlo o eliminarlo.
              </p>
            }
          </div>
        } @else if (mode() === 'edit') {
          <form
            data-testid="transaction-edit-form"
            class="space-y-4"
            (submit)="onSubmitEdit($event)"
          >
            @if (capabilities().restrictionNotice; as notice) {
              <div
                data-testid="edit-restriction-notice"
                class="flex items-start gap-2 rounded-xl border border-border bg-surface-2 p-3 text-xs text-muted"
              >
                <fo-icon name="alert-circle" [size]="16" class="mt-0.5 shrink-0 text-warning" />
                <span>{{ notice }}</span>
              </div>
            }

            @if (errorMessage(); as apiError) {
              <div
                role="alert"
                data-testid="detail-error-banner"
                class="flex items-start gap-2 rounded-xl border border-danger/30 bg-danger/10 p-3 text-xs font-medium text-danger"
              >
                <fo-icon name="alert-circle" [size]="16" class="mt-0.5 shrink-0" />
                <span>{{ apiError }}</span>
              </div>
            }

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label for="edit-tx-amount" class="mb-1 block text-xs font-medium text-muted">
                  Monto ({{ tx.currencyCode }})
                </label>
                <input
                  id="edit-tx-amount"
                  data-testid="edit-amount-input"
                  type="text"
                  inputmode="decimal"
                  [value]="draftAmount()"
                  [disabled]="!capabilities().canEditAmount"
                  (input)="onAmountInput($event)"
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text tabular-nums disabled:cursor-not-allowed disabled:opacity-55"
                />
              </div>

              <div>
                <label for="edit-tx-date" class="mb-1 block text-xs font-medium text-muted">
                  Fecha
                </label>
                <input
                  id="edit-tx-date"
                  data-testid="edit-date-input"
                  type="date"
                  [value]="draftDate()"
                  [disabled]="!capabilities().canEditDate"
                  (change)="onDateInput($event)"
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text tabular-nums disabled:cursor-not-allowed disabled:opacity-55"
                />
              </div>
            </div>

            @if (capabilities().canEditCategory) {
              <div>
                <label for="edit-tx-category" class="mb-1 block text-xs font-medium text-muted">
                  Categoría
                </label>
                <select
                  id="edit-tx-category"
                  data-testid="edit-category-select"
                  [value]="draftCategoryId() ?? ''"
                  (change)="onCategoryChange($event)"
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
                >
                  <option value="">Sin categoría</option>
                  @for (cat of editableCategories(); track cat.id) {
                    <option [value]="cat.id">{{ cat.name }}</option>
                  }
                </select>
              </div>
            }

            @if (capabilities().canEditAccount) {
              <div>
                <label for="edit-tx-account" class="mb-1 block text-xs font-medium text-muted">
                  Cuenta
                </label>
                <select
                  id="edit-tx-account"
                  data-testid="edit-account-select"
                  [value]="draftAccountId()"
                  (change)="onAccountChange($event)"
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
                >
                  @for (acc of editableAccounts(); track acc.id) {
                    <option [value]="acc.id">{{ acc.name }} ({{ acc.currencyCode }})</option>
                  }
                </select>
              </div>
            }

            <div>
              <label for="edit-tx-description" class="mb-1 block text-xs font-medium text-muted">
                Descripción
              </label>
              <input
                id="edit-tx-description"
                data-testid="edit-description-input"
                type="text"
                maxlength="255"
                [value]="draftDescription()"
                (input)="onDescriptionInput($event)"
                placeholder="Ej. Compra supermercado"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text placeholder:text-muted"
              />
            </div>

            <div>
              <label for="edit-tx-notes" class="mb-1 block text-xs font-medium text-muted">
                Notas
              </label>
              <textarea
                id="edit-tx-notes"
                data-testid="edit-notes-input"
                rows="2"
                [value]="draftNotes()"
                (input)="onNotesInput($event)"
                placeholder="Notas opcionales..."
                class="fo-focus-ring w-full resize-none rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text placeholder:text-muted"
              ></textarea>
            </div>

            @if (isAdult() && capabilities().canEditAttribution) {
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label for="edit-tx-person" class="mb-1 block text-xs font-medium text-muted">
                    Para:
                  </label>
                  <select
                    id="edit-tx-person"
                    data-testid="edit-attribution-select"
                    [value]="draftAttributedTo()"
                    (change)="onAttributedToChange($event)"
                    class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
                  >
                    @for (member of editableMembers(); track member.id) {
                      <option [value]="member.id">{{ member.fullName }}</option>
                    }
                  </select>
                </div>

                <div>
                  <label for="edit-tx-scope" class="mb-1 block text-xs font-medium text-muted">
                    Alcance
                  </label>
                  <select
                    id="edit-tx-scope"
                    data-testid="edit-scope-select"
                    [value]="draftScope()"
                    (change)="onScopeChange($event)"
                    class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2 px-3 py-2 text-sm text-text"
                  >
                    <option value="PERSONAL">Personal</option>
                    <option value="FAMILY">Familia</option>
                  </select>
                </div>
              </div>
            }

            @if (validationError(); as valErr) {
              <p class="text-xs font-medium text-danger" role="alert">{{ valErr }}</p>
            }

            <div class="flex items-center justify-end gap-2 border-t border-border pt-4">
              <fo-button
                variant="ghost"
                size="md"
                [disabled]="saving()"
                (click)="cancelSubMode()"
              >
                Cancelar
              </fo-button>
              <fo-button
                data-testid="edit-save-btn"
                type="submit"
                variant="primary"
                size="md"
                [loading]="saving()"
                [disabled]="!canSubmitEdit()"
              >
                Guardar
              </fo-button>
            </div>
          </form>
        } @else {
          <div data-testid="transaction-delete-confirm" class="space-y-4">
            <div class="rounded-2xl border border-danger/30 bg-danger/8 p-4">
              <p class="text-sm font-semibold text-text">¿Seguro que querés eliminar este movimiento?</p>
              <p class="mt-1 text-xs text-muted">
                {{ primaryTitle(tx) }} · {{ signedAmount(tx) }}
              </p>
              @if (capabilities().isTransfer) {
                <p class="mt-2 text-xs text-danger">
                  Se eliminarán ambos movimientos vinculados a esta transferencia.
                </p>
              }
            </div>

            @if (errorMessage(); as apiError) {
              <div
                role="alert"
                data-testid="detail-error-banner"
                class="flex items-start gap-2 rounded-xl border border-danger/30 bg-danger/10 p-3 text-xs font-medium text-danger"
              >
                <fo-icon name="alert-circle" [size]="16" class="mt-0.5 shrink-0" />
                <span>{{ apiError }}</span>
              </div>
            }

            <div class="flex items-center justify-end gap-2 border-t border-border pt-4">
              <fo-button
                variant="ghost"
                size="md"
                [disabled]="deleting()"
                (click)="cancelSubMode()"
              >
                Cancelar
              </fo-button>
              <fo-button
                data-testid="confirm-delete-btn"
                variant="danger"
                size="md"
                [loading]="deleting()"
                (click)="confirmDelete.emit(tx.id)"
              >
                Sí, eliminar
              </fo-button>
            </div>
          </div>
        }
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransactionDetailSheetComponent {
  readonly transaction = input<TransactionItem | null>(null);
  readonly lens = input<Lens>('NOMINAL');
  readonly referenceSnapshot = input<TransactionItem | null>(null);
  readonly snapshotLoading = input<boolean>(false);
  readonly isAdult = input<boolean>(false);
  readonly currentUserId = input<string | null>(null);
  readonly categories = input<readonly Category[]>([]);
  readonly accounts = input<readonly Account[]>([]);
  readonly members = input<readonly TransactionsFamilyMember[]>([]);
  readonly saving = input<boolean>(false);
  readonly deleting = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly closed = output<void>();
  readonly savePatch = output<{ id: string; patch: TransactionPatch }>();
  readonly confirmDelete = output<string>();
  readonly clearError = output<void>();

  protected readonly mode = signal<SheetMode>('detail');
  protected readonly draftAmount = signal<string>('');
  protected readonly draftDate = signal<string>('');
  protected readonly draftCategoryId = signal<string | null>(null);
  protected readonly draftDescription = signal<string>('');
  protected readonly draftNotes = signal<string>('');
  protected readonly draftAccountId = signal<string>('');
  protected readonly draftAttributedTo = signal<string>('');
  protected readonly draftScope = signal<TransactionScope>('PERSONAL');

  private lastTxId: string | null = null;

  protected readonly sheetTitle = computed(() => {
    switch (this.mode()) {
      case 'edit':
        return 'Editar movimiento';
      case 'confirm-delete':
        return 'Eliminar movimiento';
      default:
        return 'Detalle del movimiento';
    }
  });

  protected readonly capabilities = computed<TransactionEditCapabilities>(() => {
    const tx = this.transaction();
    if (!tx) {
      return {
        canEditAmount: false,
        canEditDate: false,
        canEditCategory: false,
        canEditAccount: false,
        canEditAttribution: false,
        canEditScope: false,
        isInstallment: false,
        isExchange: false,
        isAllowance: false,
        isTransfer: false,
        restrictionNotice: null,
      };
    }
    return getTransactionEditCapabilities(tx, this.isAdult());
  });

  protected readonly canModify = computed(() => {
    const tx = this.transaction();
    if (!tx) {
      return false;
    }
    if (this.isAdult()) {
      return true;
    }
    const uid = this.currentUserId();
    return uid !== null && uid === tx.paidBy.id;
  });

  protected readonly referenceSnapshotText = computed(() =>
    formatReferenceSnapshot(this.referenceSnapshot()),
  );

  protected readonly editableCategories = computed<readonly Category[]>(() => {
    const tx = this.transaction();
    if (!tx || (tx.type !== 'EXPENSE' && tx.type !== 'INCOME')) {
      return [];
    }
    const kind: CategoryKind = tx.type;
    const filtered = this.categories().filter(
      (cat) => cat.kind === kind && cat.isActive !== false,
    );
    if (tx.category && !filtered.some((cat) => cat.id === tx.category!.id)) {
      return [
        {
          id: tx.category.id,
          parentCategoryId: null,
          name: tx.category.name,
          kind,
          icon: tx.category.icon,
          colorHex: tx.category.colorHex,
          isSystem: false,
          systemKey: null,
          isActive: true,
        },
        ...filtered,
      ];
    }
    return filtered;
  });

  protected readonly editableAccounts = computed<readonly { id: string; name: string; currencyCode: string }[]>(() => {
    const tx = this.transaction();
    if (!tx) {
      return [];
    }
    const currency = tx.currencyCode.trim().toUpperCase();
    const usable = this.accounts()
      .filter((acc) => acc.isActive && acc.currencyCode.trim().toUpperCase() === currency)
      .map((acc) => ({ id: acc.id, name: acc.name, currencyCode: acc.currencyCode }));

    if (!usable.some((acc) => acc.id === tx.account.id)) {
      return [{ id: tx.account.id, name: tx.account.name, currencyCode: currency }, ...usable];
    }
    return usable;
  });

  protected readonly editableMembers = computed<readonly { id: string; fullName: string }[]>(() => {
    const tx = this.transaction();
    const list = this.members().map((member) => ({ id: member.id, fullName: member.fullName }));
    if (tx && !list.some((m) => m.id === tx.attributedTo.id)) {
      return [{ id: tx.attributedTo.id, fullName: tx.attributedTo.fullName }, ...list];
    }
    return list;
  });

  protected readonly pendingPatch = computed<TransactionPatch>(() => {
    const tx = this.transaction();
    if (!tx) {
      return {};
    }
    const draft: TransactionEditDraft = {
      amount: this.draftAmount(),
      transactionDate: this.draftDate(),
      categoryId: this.draftCategoryId(),
      description: this.draftDescription(),
      notes: this.draftNotes(),
      accountId: this.draftAccountId(),
      attributedToUserId: this.draftAttributedTo(),
      scope: this.draftScope(),
    };
    return buildTransactionPatch(tx, draft, this.capabilities());
  });

  protected readonly validationError = computed<string | null>(() => {
    const tx = this.transaction();
    if (!tx || this.mode() !== 'edit') {
      return null;
    }
    const caps = this.capabilities();
    if (caps.canEditAmount && normalizeEditableAmount(this.draftAmount()) === null) {
      return 'Ingresá un monto válido mayor a cero.';
    }
    if (caps.canEditDate && !/^\d{4}-\d{2}-\d{2}$/.test(this.draftDate())) {
      return 'Seleccioná una fecha válida.';
    }
    if (
      (tx.type === 'EXPENSE' || tx.type === 'INCOME') &&
      !this.draftCategoryId() &&
      this.draftDescription().trim().length === 0
    ) {
      return 'Indicá una categoría o una descripción.';
    }
    return null;
  });

  protected readonly canSubmitEdit = computed(
    () =>
      !this.saving() &&
      this.validationError() === null &&
      Object.keys(this.pendingPatch()).length > 0,
  );

  constructor() {
    effect(() => {
      const tx = this.transaction();
      const nextId = tx?.id ?? null;
      if (nextId !== this.lastTxId) {
        this.lastTxId = nextId;
        untracked(() => {
          this.mode.set('detail');
          if (tx) {
            this.resetDraftFrom(tx);
          }
        });
      }
    });
  }

  protected enterEdit(): void {
    const tx = this.transaction();
    if (!tx) {
      return;
    }
    this.clearError.emit();
    this.resetDraftFrom(tx);
    this.mode.set('edit');
  }

  protected enterDeleteConfirm(): void {
    this.clearError.emit();
    this.mode.set('confirm-delete');
  }

  protected cancelSubMode(): void {
    this.clearError.emit();
    this.mode.set('detail');
  }

  protected onClose(): void {
    this.mode.set('detail');
    this.closed.emit();
  }

  protected onSubmitEdit(event: Event): void {
    event.preventDefault();
    const tx = this.transaction();
    if (!tx || !this.canSubmitEdit()) {
      return;
    }
    this.savePatch.emit({ id: tx.id, patch: this.pendingPatch() });
  }

  protected onAmountInput(event: Event): void {
    this.draftAmount.set((event.target as HTMLInputElement | null)?.value ?? '');
  }

  protected onDateInput(event: Event): void {
    this.draftDate.set((event.target as HTMLInputElement | null)?.value ?? '');
  }

  protected onCategoryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement | null)?.value ?? '';
    this.draftCategoryId.set(value || null);
  }

  protected onAccountChange(event: Event): void {
    this.draftAccountId.set((event.target as HTMLSelectElement | null)?.value ?? '');
  }

  protected onDescriptionInput(event: Event): void {
    this.draftDescription.set((event.target as HTMLInputElement | null)?.value ?? '');
  }

  protected onNotesInput(event: Event): void {
    this.draftNotes.set((event.target as HTMLTextAreaElement | null)?.value ?? '');
  }

  protected onAttributedToChange(event: Event): void {
    this.draftAttributedTo.set((event.target as HTMLSelectElement | null)?.value ?? '');
  }

  protected onScopeChange(event: Event): void {
    const val = (event.target as HTMLSelectElement | null)?.value;
    this.draftScope.set(val === 'FAMILY' ? 'FAMILY' : 'PERSONAL');
  }

  protected primaryTitle(tx: TransactionItem): string {
    return tx.description?.trim() || tx.category?.name || transactionTypeLabel(tx.type);
  }

  protected typeLabel(tx: TransactionItem): string {
    return transactionTypeLabel(tx.type);
  }

  protected statusLabel(tx: TransactionItem): string {
    return transactionStatusLabel(tx.status);
  }

  protected fallbackIcon(tx: TransactionItem): string {
    return fallbackTransactionIcon(tx.type);
  }

  protected amountTone(tx: TransactionItem): 'positive' | 'negative' | 'neutral' {
    return transactionAmountTone(tx.type);
  }

  protected signedAmount(tx: TransactionItem): string {
    return formatSignedAmount(tx.amount, tx.currencyCode, tx.type);
  }

  protected showLensSubtitle(tx: TransactionItem): boolean {
    return this.lens() !== 'NOMINAL' && tx.lensAmount !== null;
  }

  protected lensUnit(tx: TransactionItem): string {
    return resolveLensDisplayUnit(tx.lensUnit, this.lens(), tx.currencyCode);
  }

  protected formatLongDateLabel(isoDate: string): string {
    return formatLongDate(isoDate);
  }

  protected iconBg(tx: TransactionItem): string {
    const color = tx.category?.colorHex;
    return color ? `color-mix(in srgb, ${color} 14%, transparent)` : 'var(--fo-surface-2)';
  }

  protected iconColor(tx: TransactionItem): string {
    return tx.category?.colorHex || 'var(--fo-text-muted)';
  }

  private resetDraftFrom(tx: TransactionItem): void {
    this.draftAmount.set(formatDraftAmount(tx.amount));
    this.draftDate.set(tx.transactionDate);
    this.draftCategoryId.set(tx.category?.id ?? null);
    this.draftDescription.set(tx.description ?? '');
    this.draftNotes.set(tx.notes ?? '');
    this.draftAccountId.set(tx.account.id);
    this.draftAttributedTo.set(tx.attributedTo.id);
    this.draftScope.set(tx.scope === 'FAMILY' ? 'FAMILY' : 'PERSONAL');
  }
}
