import { A11yModule } from '@angular/cdk/a11y';
import { BreakpointObserver } from '@angular/cdk/layout';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Subscription, catchError, exhaustMap, firstValueFrom, map, of, timer } from 'rxjs';
import { AccountsStore } from '../../core/finance/accounts.store';
import { CategoriesStore } from '../../core/finance/categories.store';
import { FinanceEvents } from '../../core/finance/finance-events';
import { Account } from '../../core/finance/finance.models';
import { Receipt, ReceiptDraft, ReceiptsApi } from '../../core/finance/receipts.api';
import { TransactionsApi } from '../../core/finance/transactions.api';
import {
  InstallmentAmountMode,
  InstallmentPlanCreate,
  TransactionCreate,
  TransactionType,
} from '../../core/finance/transactions.models';
import { QuickAddLauncher } from '../../core/layout/quick-add-launcher.service';
import { SessionStore } from '../../core/state/session.store';
import {
  BottomSheetComponent,
  IconComponent,
  SegmentedOption,
  SkeletonComponent,
  ToastService,
} from '../../shared/ui';
import {
  KeypadKey,
  applyKeypadKey,
  currencySymbol,
  formatAmountDisplay,
  normalizeDecimalInput,
  toApiDecimal,
} from './amount-input';
import { AmountKeypadComponent } from './components/amount-keypad.component';
import { CategoryPickerComponent } from './components/category-picker.component';
import { QuickAddAdvancedComponent } from './components/quick-add-advanced.component';
import { QuickAddCurrencyChipComponent } from './components/quick-add-currency-chip.component';
import {
  QuickAddFamilyMember,
  QuickAddMembersApi,
} from './data-access/quick-add-members.api';
import {
  InstallmentSummaryLines,
  installmentPreview,
  installmentSummaryLines,
} from './installment-preview';
import { extractQuickAddProblemMessage } from './utils/problem-message';
import { prepareReceiptFile } from './utils/receipt-image';

const DESKTOP_BREAKPOINT = '(min-width: 1024px)';
const RECEIPT_POLL_INTERVAL_MS = 1500;
const RECEIPT_POLL_TIMEOUT_MS = 30_000;
const LOW_CONFIDENCE_THRESHOLD = 0.5;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

type PrefilledField = 'amount' | 'currency' | 'date' | 'description' | 'category';
type QuickAddTransactionCreate = TransactionCreate & { receiptAttachmentId?: string };

const TYPE_OPTIONS: readonly SegmentedOption<TransactionType>[] = [
  { value: 'EXPENSE', label: 'Gasto' },
  { value: 'INCOME', label: 'Ingreso' },
];

function todayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function shouldIgnoreEnterTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tag = target.tagName;
  if (tag === 'BUTTON' || tag === 'A' || tag === 'SELECT' || tag === 'TEXTAREA') {
    return true;
  }
  if (tag === 'INPUT' && (target as HTMLInputElement).type === 'search') {
    return true;
  }
  return false;
}

@Component({
  selector: 'fo-quick-add-host',
  standalone: true,
  imports: [
    A11yModule,
    AmountKeypadComponent,
    BottomSheetComponent,
    CategoryPickerComponent,
    IconComponent,
    QuickAddAdvancedComponent,
    QuickAddCurrencyChipComponent,
    SkeletonComponent,
  ],
  host: {
    '(document:keydown.enter)': 'onSheetEnter($event)',
  },
  template: `
    <fo-bottom-sheet
      [open]="launcher.isOpen()"
      ariaLabel="Nuevo movimiento"
      (closed)="onSheetClosed()"
    >
      @if (launcher.isOpen()) {
        <div data-testid="quick-add-sheet" class="qa-root">
          <header class="qa-top">
            <div
              class="type-switch"
              [class.type-switch--tabs]="isDesktop()"
              role="tablist"
              aria-label="Tipo de movimiento"
              data-testid="quick-add-type"
            >
              @for (option of typeOptions; track option.value) {
                <button
                  type="button"
                  role="tab"
                  [attr.aria-selected]="transactionType() === option.value"
                  (click)="onTypeChange(option.value)"
                  class="fo-focus-ring"
                  [class.is-active]="transactionType() === option.value"
                >
                  {{ option.label }}
                </button>
              }
            </div>

            <div class="flex items-center gap-1.5">
              <input
                #receiptFileInput
                type="file"
                accept="image/*,application/pdf"
                capture="environment"
                data-testid="quick-add-receipt-input"
                (change)="onReceiptFileSelected($event)"
                class="sr-only"
              />

              <button
                type="button"
                [disabled]="scanning() || saving()"
                title="Escanear ticket"
                aria-label="Escanear ticket"
                data-testid="quick-add-camera-btn"
                (click)="openReceiptPicker()"
                class="subtle-icon-btn fo-focus-ring"
                [class.is-on]="receiptAttachmentId() !== null"
              >
                <fo-icon name="camera" [size]="15" />
              </button>

              <button
                type="button"
                aria-label="Cerrar"
                data-testid="quick-add-close-btn"
                (click)="closeSheet()"
                class="subtle-icon-btn fo-focus-ring"
              >
                <fo-icon name="x" [size]="15" />
              </button>
            </div>
          </header>

          @if (!scanning() && scanWarning(); as warningText) {
            <div role="status" aria-live="polite" data-testid="receipt-scan-warning" class="qa-notice">
              <fo-icon name="alert-circle" [size]="14" class="mt-px shrink-0 text-warning" />
              <span>{{ warningText }}</span>
            </div>
          }

          @if (!scanning() && receiptAttachmentId()) {
            <div data-testid="receipt-attached-badge" class="qa-attached">
              <span class="flex items-center gap-1.5">
                <fo-icon name="file-text" [size]="13" />
                <span>Comprobante adjunto</span>
              </span>
              <button
                type="button"
                data-testid="receipt-detach-btn"
                aria-label="Quitar comprobante adjunto"
                (click)="clearAttachedReceipt()"
                class="fo-focus-ring inline-flex h-5 w-5 cursor-pointer items-center justify-center rounded-full hover:bg-accent/15"
              >
                <fo-icon name="x" [size]="12" />
              </button>
            </div>
          }

          <div class="relative">
            <div
              class="qa-root"
              [class.opacity-35]="scanning()"
              [class.pointer-events-none]="scanning()"
              [attr.aria-hidden]="scanning() ? 'true' : null"
            >
              @if (isDesktop()) {
                <div class="grid grid-cols-2 gap-2.5">
                  <div>
                    <label for="fo-quick-add-amount" class="d-label">Monto ({{ currencyCode() }})</label>
                    <div class="flex items-center gap-1.5">
                      <input
                        #desktopAmountInput
                        id="fo-quick-add-amount"
                        data-testid="quick-add-amount-input"
                        [attr.data-prefilled]="isFieldPrefilled('amount') ? 'true' : null"
                        cdkFocusInitial
                        type="text"
                        inputmode="decimal"
                        autocomplete="off"
                        placeholder="Ej. 20000"
                        [value]="desktopDisplayValue()"
                        (input)="onDesktopAmountInput($event)"
                        class="d-input tabular-nums"
                        [class.is-prefilled]="isFieldPrefilled('amount')"
                      />
                      <fo-quick-add-currency-chip
                    [currencyCode]="currencyCode()"
                    [currencies]="enabledCurrencies()"
                    [open]="currencyMenuOpen()"
                    [prefilled]="isFieldPrefilled('currency')"
                    (toggleMenu)="toggleCurrencyMenu()"
                    (selectCurrency)="selectCurrency($event)"
                  />
                    </div>
                  </div>
                  <div>
                    <label for="fo-quick-add-description" class="d-label">Nombre (opcional)</label>
                    <input
                      id="fo-quick-add-description"
                      data-testid="quick-add-description"
                      [attr.data-prefilled]="isFieldPrefilled('description') ? 'true' : null"
                      type="text"
                      maxlength="140"
                      [value]="description()"
                      (input)="onDesktopDescriptionInput($event)"
                      (keydown.enter)="onDesktopDescriptionEnter($event)"
                      placeholder="Ej. Café, Fiesta Sofi..."
                      class="d-input"
                      [class.is-prefilled]="isFieldPrefilled('description')"
                    />
                  </div>
                </div>
              } @else {
                <section aria-label="Monto" class="qa-amount-area">
                  <fo-quick-add-currency-chip
                    [currencyCode]="currencyCode()"
                    [currencies]="enabledCurrencies()"
                    [open]="currencyMenuOpen()"
                    [prefilled]="isFieldPrefilled('currency')"
                    (toggleMenu)="toggleCurrencyMenu()"
                    (selectCurrency)="selectCurrency($event)"
                  />
                  <div
                    class="qa-amount-display"
                    [class.is-empty]="rawAmount().length === 0"
                    [class.fo-amount-bump-a]="bumpPhase() === 'a'"
                    [class.fo-amount-bump-b]="bumpPhase() === 'b'"
                  >
                    <span aria-hidden="true">{{ activeCurrencySymbol() }}</span><span
                      #mobileAmountDisplay
                      data-testid="quick-add-amount-display"
                      [attr.data-prefilled]="isFieldPrefilled('amount') ? 'true' : null"
                      tabindex="0"
                      cdkFocusInitial
                      role="textbox"
                      aria-readonly="true"
                      aria-label="Monto"
                      class="fo-focus-ring rounded-lg"
                      [class.is-prefilled]="isFieldPrefilled('amount')"
                      >{{ formattedMobileAmount() }}</span
                    >
                  </div>
                  <button
                    type="button"
                    [disabled]="saving() || scanning() || rawAmount().length === 0"
                    data-testid="keypad-backspace"
                    aria-label="Borrar"
                    (click)="onKeypadPress('BACKSPACE')"
                    class="subtle-icon-btn qa-backspace fo-focus-ring"
                  >
                    <span aria-hidden="true">⌫</span>
                  </button>
                </section>
              }

              <fo-category-picker
                [frequentCategories]="frequentCategories()"
                [allCategories]="allCategoriesForKind()"
                [selectedCategoryId]="selectedCategoryId()"
                [description]="description()"
                [showDescription]="!isDesktop()"
                [categoryPrefilled]="isFieldPrefilled('category')"
                [descriptionPrefilled]="isFieldPrefilled('description')"
                (selectedCategoryIdChange)="onCategoryChange($event)"
                (descriptionChange)="onDescriptionChange($event)"
                (enterSubmit)="save()"
              />

              <fo-quick-add-advanced
                [expanded]="advancedExpanded()"
                [transactionType]="transactionType()"
                [currencyCode]="currencyCode()"
                [usableAccounts]="usableAccounts()"
                [creditCards]="creditCards()"
                [selectedAccountId]="resolvedAccountId()"
                [transactionDate]="transactionDate()"
                [isAdult]="session.isAdult()"
                [currentUserId]="currentUserId()"
                [members]="members()"
                [attributedToUserId]="attributedToUserId()"
                [installments]="installments()"
                [amountMode]="amountMode()"
                [hasInterest]="hasInterest()"
                [cashPrice]="cashPrice()"
                [installmentSummary]="installmentSummary()"
                [summaryHint]="advancedSummaryHint()"
                [datePrefilled]="isFieldPrefilled('date')"
                (expandedChange)="advancedExpanded.set($event)"
                (accountIdChange)="selectedAccountIdOverride.set($event)"
                (transactionDateChange)="onTransactionDateChange($event)"
                (attributedToUserIdChange)="attributedToUserId.set($event)"
                (installmentsChange)="onInstallmentsChange($event)"
                (amountModeChange)="amountMode.set($event)"
                (hasInterestChange)="onHasInterestChange($event)"
                (cashPriceChange)="cashPrice.set($event)"
                (navigateToAccounts)="closeSheet()"
              />
            </div>

            @if (scanning()) {
              <div
                role="status"
                aria-live="polite"
                aria-busy="true"
                data-testid="receipt-scanning-state"
                class="absolute inset-0 z-10 flex flex-col justify-between gap-3 rounded-[14px] border border-dashed border-border bg-surface/95 p-4"
              >
                <div class="flex items-center gap-2 text-xs font-semibold text-accent">
                  <svg class="h-4 w-4 shrink-0 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-opacity="0.25" stroke-width="3" />
                    <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
                  </svg>
                  <span>Leyendo ticket…</span>
                </div>

                <div class="space-y-3">
                  <fo-skeleton height="3rem" rounded="lg" />
                  <div class="flex flex-wrap gap-1.5">
                    <fo-skeleton width="5.5rem" height="1.75rem" rounded="full" />
                    <fo-skeleton width="6.5rem" height="1.75rem" rounded="full" />
                    <fo-skeleton width="4.5rem" height="1.75rem" rounded="full" />
                  </div>
                  <fo-skeleton height="2.25rem" rounded="md" />
                  <fo-skeleton height="2.25rem" rounded="md" />
                </div>
              </div>
            }
          </div>

          @if (!isDesktop()) {
            <fo-amount-keypad [disabled]="saving() || scanning()" (keyPress)="onKeypadPress($event)" />
          }

          <div data-testid="quick-add-save-btn">
            <button
              type="button"
              class="save-btn fo-focus-ring"
              [disabled]="!canSave()"
              [attr.aria-busy]="saving() ? 'true' : null"
              (click)="save()"
            >
              {{ saving() ? 'Guardando…' : saveLabel() }}
            </button>
          </div>
        </div>
      }
    </fo-bottom-sheet>
  `,
  styleUrl: './quick-add-host.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuickAddHostComponent {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly session = inject(SessionStore);
  private readonly accountsStore = inject(AccountsStore);
  private readonly categoriesStore = inject(CategoriesStore);
  private readonly transactionsApi = inject(TransactionsApi);
  private readonly receiptsApi = inject(ReceiptsApi);
  private readonly membersApi = inject(QuickAddMembersApi);
  private readonly financeEvents = inject(FinanceEvents);
  private readonly toast = inject(ToastService);

  protected readonly launcher = inject(QuickAddLauncher);
  protected readonly typeOptions = TYPE_OPTIONS;

  private readonly desktopAmountInput =
    viewChild<ElementRef<HTMLInputElement>>('desktopAmountInput');
  private readonly mobileAmountDisplay =
    viewChild<ElementRef<HTMLElement>>('mobileAmountDisplay');
  private readonly receiptFileInput =
    viewChild<ElementRef<HTMLInputElement>>('receiptFileInput');

  protected readonly isDesktop = toSignal(
    this.breakpointObserver
      .observe(DESKTOP_BREAKPOINT)
      .pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_BREAKPOINT) },
  );

  protected readonly transactionType = signal<TransactionType>('EXPENSE');
  protected readonly rawAmount = signal<string>('');
  protected readonly desktopDisplayValue = signal<string>('');
  protected readonly currencyCode = signal<string>('ARS');
  protected readonly currencyMenuOpen = signal<boolean>(false);
  protected readonly selectedCategoryId = signal<string | null>(null);
  protected readonly description = signal<string>('');
  protected readonly transactionDate = signal<string>(todayIsoDate());
  protected readonly advancedExpanded = signal<boolean>(false);
  protected readonly selectedAccountIdOverride = signal<string | null>(null);
  protected readonly attributedToUserId = signal<string | null>(null);
  protected readonly installments = signal<number>(1);
  protected readonly amountMode = signal<InstallmentAmountMode | null>(null);
  protected readonly hasInterest = signal<boolean>(false);
  protected readonly cashPrice = signal<string>('');
  protected readonly members = signal<QuickAddFamilyMember[]>([]);
  protected readonly saving = signal<boolean>(false);
  protected readonly scanning = signal<boolean>(false);
  protected readonly receiptAttachmentId = signal<string | null>(null);
  protected readonly scanWarning = signal<string | null>(null);
  protected readonly prefilledFields = signal<ReadonlySet<PrefilledField>>(new Set());
  protected readonly bumpPhase = signal<'idle' | 'a' | 'b'>('idle');

  private membersLoaded = false;
  private receiptScanSeq = 0;
  private pollSub: Subscription | null = null;
  private pollTimeoutHandle: ReturnType<typeof setTimeout> | null = null;

  private readonly expenseFrequent = this.categoriesStore.frequent('EXPENSE', 8);
  private readonly incomeFrequent = this.categoriesStore.frequent('INCOME', 8);
  private readonly expenseAll = this.categoriesStore.byKind('EXPENSE');
  private readonly incomeAll = this.categoriesStore.byKind('INCOME');

  protected readonly currentUserId = computed(() => this.session.me()?.id ?? null);

  protected readonly frequentCategories = computed(() =>
    this.transactionType() === 'EXPENSE' ? this.expenseFrequent() : this.incomeFrequent(),
  );

  protected readonly allCategoriesForKind = computed(() =>
    this.transactionType() === 'EXPENSE' ? this.expenseAll() : this.incomeAll(),
  );

  protected readonly enabledCurrencies = computed<readonly string[]>(() => {
    const enabled = this.session.me()?.family.enabledCurrencies ?? [];
    if (enabled.length > 0) {
      return enabled;
    }
    const base = this.session.me()?.family.baseCurrency ?? 'ARS';
    return [base];
  });

  protected readonly activeCurrencySymbol = computed(() => currencySymbol(this.currencyCode()));

  protected readonly formattedMobileAmount = computed(() =>
    formatAmountDisplay(this.rawAmount()),
  );

  protected readonly parsedAmount = computed(() => toApiDecimal(this.rawAmount()));

  protected readonly isInstallmentMode = computed(
    () => this.transactionType() === 'EXPENSE' && this.installments() > 1,
  );

  protected readonly usableAccounts = computed(() =>
    this.accountsStore.usableFor(this.currencyCode())(),
  );

  protected readonly creditCards = this.accountsStore.creditCards;

  protected readonly resolvedAccount = computed<Account | null>(() => {
    const overrideId = this.selectedAccountIdOverride();

    if (this.isInstallmentMode()) {
      const cards = this.creditCards();
      if (cards.length === 0) {
        return null;
      }
      if (overrideId) {
        const matched = cards.find((card) => card.id === overrideId);
        if (matched) {
          return matched;
        }
      }
      const normalizedCurrency = this.currencyCode().trim().toUpperCase();
      return (
        cards.find((card) => card.currencyCode.trim().toUpperCase() === normalizedCurrency) ??
        cards[0]
      );
    }

    const usable = this.usableAccounts();
    if (overrideId) {
      const matched = usable.find((account) => account.id === overrideId);
      if (matched) {
        return matched;
      }
    }
    const defaultAccount = this.accountsStore.defaultAccountFor(this.currencyCode());
    if (defaultAccount) {
      return defaultAccount;
    }
    return usable[0] ?? null;
  });

  protected readonly resolvedAccountId = computed<string | null>(
    () => this.resolvedAccount()?.id ?? null,
  );

  protected readonly installmentSummary = computed<InstallmentSummaryLines | null>(() => {
    if (!this.isInstallmentMode()) {
      return null;
    }
    const mode = this.amountMode();
    const amount = this.parsedAmount();
    if (!mode || !amount) {
      return null;
    }
    const result = installmentPreview({
      amount,
      totalInstallments: this.installments(),
      amountMode: mode,
      purchaseDate: this.transactionDate(),
      closingDay: this.resolvedAccount()?.closingDay ?? null,
      currencyCode: this.currencyCode(),
    });
    if (!result) {
      return null;
    }
    return installmentSummaryLines(result, {
      currencyCode: this.currencyCode(),
      hasInterest: this.hasInterest(),
      cashPrice: this.cashPrice(),
    });
  });

  /** Mock CTA: `Guardar gasto`, `Guardar para Sofía`, `Guardar ingreso`. */
  protected readonly saveLabel = computed(() => {
    if (this.transactionType() === 'INCOME') {
      return 'Guardar ingreso';
    }
    const attributed = this.attributedToUserId();
    if (attributed && attributed !== this.currentUserId()) {
      const member = this.members().find((m) => m.id === attributed);
      const firstName = member?.fullName.trim().split(/\s+/)[0];
      if (firstName) {
        return `Guardar para ${firstName}`;
      }
    }
    return 'Guardar gasto';
  });

  protected readonly advancedSummaryHint = computed<string>(() => {
    const parts: string[] = [];
    const accountName = this.resolvedAccount()?.name;
    if (accountName) {
      parts.push(accountName);
    }
    if (this.transactionDate() === todayIsoDate()) {
      parts.push('Hoy');
    } else {
      parts.push(this.transactionDate());
    }
    if (this.isInstallmentMode()) {
      parts.push(`${this.installments()} cuotas`);
    }
    return parts.join(' · ');
  });

  protected readonly canSave = computed(() => {
    if (this.saving() || this.scanning()) {
      return false;
    }
    if (!this.parsedAmount()) {
      return false;
    }
    const hasCategoryOrDescription =
      this.selectedCategoryId() !== null || this.description().trim().length > 0;
    if (!hasCategoryOrDescription) {
      return false;
    }
    if (this.resolvedAccountId() === null) {
      return false;
    }
    if (this.isInstallmentMode() && this.amountMode() === null) {
      return false;
    }
    return true;
  });

  private wasOpen = false;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
    });

    effect(() => {
      const open = this.launcher.isOpen();
      if (open && !this.wasOpen) {
        this.wasOpen = true;
        untracked(() => {
          this.initializeOnOpen();
        });
      } else if (!open && this.wasOpen) {
        this.wasOpen = false;
        untracked(() => {
          this.resetForm();
        });
      }
    });
  }

  protected isFieldPrefilled(field: PrefilledField): boolean {
    return this.prefilledFields().has(field);
  }

  protected openReceiptPicker(): void {
    if (this.scanning() || this.saving()) {
      return;
    }
    this.receiptFileInput()?.nativeElement.click();
  }

  protected async onReceiptFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0] ?? null;
    if (input) {
      input.value = '';
    }
    if (!file) {
      return;
    }
    await this.scanReceiptFile(file);
  }

  protected clearAttachedReceipt(): void {
    this.receiptScanSeq += 1;
    this.stopPolling();
    this.scanning.set(false);
    this.receiptAttachmentId.set(null);
    this.scanWarning.set(null);
    this.prefilledFields.set(new Set());
  }

  protected onCategoryChange(categoryId: string | null): void {
    this.selectedCategoryId.set(categoryId);
    this.clearPrefilled('category');
  }

  protected onDescriptionChange(value: string): void {
    this.description.set(value);
    this.clearPrefilled('description');
  }

  protected onDesktopDescriptionInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.onDescriptionChange(target?.value ?? '');
  }

  protected onDesktopDescriptionEnter(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.save();
  }

  protected onTransactionDateChange(value: string): void {
    this.transactionDate.set(value);
    this.clearPrefilled('date');
  }

  protected onTypeChange(nextType: TransactionType): void {
    if (this.transactionType() === nextType) {
      return;
    }
    this.transactionType.set(nextType);
    this.selectedCategoryId.set(null);
    if (nextType === 'INCOME') {
      this.installments.set(1);
      this.amountMode.set(null);
      this.hasInterest.set(false);
      this.cashPrice.set('');
      this.selectedAccountIdOverride.set(null);
    }
  }

  protected toggleCurrencyMenu(): void {
    if (this.enabledCurrencies().length <= 1) {
      return;
    }
    this.currencyMenuOpen.update((open) => !open);
  }

  protected selectCurrency(code: string): void {
    this.currencyCode.set(code);
    this.currencyMenuOpen.set(false);
    this.selectedAccountIdOverride.set(null);
    this.clearPrefilled('currency');
  }

  protected onKeypadPress(key: KeypadKey): void {
    const next = applyKeypadKey(this.rawAmount(), key);
    this.rawAmount.set(next);
    this.desktopDisplayValue.set(next);
    this.bumpPhase.update((phase) => (phase === 'a' ? 'b' : 'a'));
    this.clearPrefilled('amount');
  }

  protected onDesktopAmountInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    if (!target) {
      return;
    }
    const raw = target.value;
    const normalized = normalizeDecimalInput(raw);
    this.rawAmount.set(normalized);
    if (raw.replace(/,/g, '.') !== normalized) {
      target.value = normalized;
    }
    this.desktopDisplayValue.set(target.value);
    this.clearPrefilled('amount');
  }

  protected onInstallmentsChange(nextCount: number): void {
    const clamped = Math.max(1, Math.min(24, Math.trunc(nextCount)));
    const wasInstallments = this.installments() > 1;
    const willBeInstallments = clamped > 1;
    this.installments.set(clamped);

    if (!wasInstallments && willBeInstallments) {
      this.amountMode.set(null);
      this.hasInterest.set(false);
      this.cashPrice.set('');
      this.selectedAccountIdOverride.set(null);
    } else if (wasInstallments && !willBeInstallments) {
      this.amountMode.set(null);
      this.hasInterest.set(false);
      this.cashPrice.set('');
      this.selectedAccountIdOverride.set(null);
    }
  }

  protected onHasInterestChange(hasInterest: boolean): void {
    this.hasInterest.set(hasInterest);
    if (!hasInterest) {
      this.cashPrice.set('');
    }
  }

  protected onSheetEnter(event: Event): void {
    if (!this.launcher.isOpen() || shouldIgnoreEnterTarget(event.target)) {
      return;
    }
    if (this.canSave()) {
      event.preventDefault();
      void this.save();
    }
  }

  protected closeSheet(): void {
    this.launcher.close();
  }

  protected onSheetClosed(): void {
    this.launcher.close();
  }

  protected async save(): Promise<void> {
    if (!this.canSave()) {
      return;
    }

    const decimalAmount = this.parsedAmount();
    const accountId = this.resolvedAccountId();
    if (!decimalAmount || !accountId) {
      return;
    }

    const type = this.transactionType();
    const selectedCategoryId = this.selectedCategoryId();
    const trimmedDescription = this.description().trim();
    const currentUserId = this.currentUserId();
    const attributedId =
      this.session.isAdult() &&
      this.attributedToUserId() &&
      this.attributedToUserId() !== currentUserId
        ? this.attributedToUserId()
        : null;

    this.saving.set(true);
    try {
      if (this.isInstallmentMode()) {
        const mode = this.amountMode();
        if (!mode) {
          return;
        }
        const selectedCategory = selectedCategoryId
          ? (this.allCategoriesForKind().find((cat) => cat.id === selectedCategoryId) ?? null)
          : null;
        const fallbackCategoryId =
          this.categoriesStore.bySystemKey('OTHER_EXPENSE')?.id ??
          this.allCategoriesForKind()[0]?.id ??
          null;
        const resolvedCategoryId = selectedCategoryId ?? fallbackCategoryId;
        if (!resolvedCategoryId) {
          this.toast.error('Elegí una categoría para guardar la compra en cuotas.');
          return;
        }

        const planBody: InstallmentPlanCreate = {
          description: trimmedDescription || selectedCategory?.name || 'Compra en cuotas',
          categoryId: resolvedCategoryId,
          creditCardAccountId: accountId,
          currencyCode: this.currencyCode(),
          totalInstallments: this.installments(),
          amountMode: mode,
          amount: decimalAmount,
          hasInterest: this.hasInterest(),
          purchaseDate: this.transactionDate(),
        };

        const parsedCashPrice = this.hasInterest() ? toApiDecimal(this.cashPrice()) : null;
        if (parsedCashPrice) {
          planBody.cashPrice = parsedCashPrice;
        }
        if (attributedId) {
          planBody.attributedToUserId = attributedId;
        }

        await firstValueFrom(this.transactionsApi.createInstallmentPlan(planBody));
        this.toast.success('Gasto guardado');
        this.categoriesStore.recordUsage(resolvedCategoryId);
        this.financeEvents.emit('transaction-created');
        this.resetForm();
        this.launcher.close();
        return;
      }

      const body: QuickAddTransactionCreate = {
        type,
        amount: decimalAmount,
        currencyCode: this.currencyCode(),
        transactionDate: this.transactionDate(),
        accountId,
      };

      if (selectedCategoryId) {
        body.categoryId = selectedCategoryId;
      }
      if (trimmedDescription) {
        body.description = trimmedDescription;
      }
      if (attributedId) {
        body.attributedToUserId = attributedId;
      }
      const attachmentId = this.receiptAttachmentId();
      if (attachmentId) {
        body.receiptAttachmentId = attachmentId;
      }

      await firstValueFrom(this.transactionsApi.create(body));
      this.toast.success(type === 'INCOME' ? 'Ingreso guardado' : 'Gasto guardado');
      if (selectedCategoryId) {
        this.categoriesStore.recordUsage(selectedCategoryId);
      }
      this.financeEvents.emit('transaction-created');
      this.resetForm();
      this.launcher.close();
    } catch (err) {
      const fallback =
        this.isInstallmentMode() && err instanceof HttpErrorResponse && err.status === 404
          ? 'La carga en cuotas todavía no está disponible.'
          : 'No pudimos guardar el movimiento. Intentá de nuevo.';
      this.toast.error(extractQuickAddProblemMessage(err, fallback));
    } finally {
      this.saving.set(false);
    }
  }

  private initializeOnOpen(): void {
    this.resetForm();
    void this.ensureDataLoaded();
    queueMicrotask(() => this.focusAmount());
  }

  private resetForm(): void {
    this.receiptScanSeq += 1;
    this.stopPolling();
    const baseCurrency = this.session.me()?.family.baseCurrency ?? 'ARS';
    this.transactionType.set('EXPENSE');
    this.rawAmount.set('');
    this.desktopDisplayValue.set('');
    this.currencyCode.set(baseCurrency);
    this.currencyMenuOpen.set(false);
    this.selectedCategoryId.set(null);
    this.description.set('');
    this.transactionDate.set(todayIsoDate());
    this.advancedExpanded.set(false);
    this.selectedAccountIdOverride.set(null);
    this.attributedToUserId.set(null);
    this.installments.set(1);
    this.amountMode.set(null);
    this.hasInterest.set(false);
    this.cashPrice.set('');
    this.saving.set(false);
    this.scanning.set(false);
    this.receiptAttachmentId.set(null);
    this.scanWarning.set(null);
    this.prefilledFields.set(new Set());
    this.bumpPhase.set('idle');
  }

  private async scanReceiptFile(file: File): Promise<void> {
    const seq = ++this.receiptScanSeq;
    this.stopPolling();
    this.scanning.set(true);
    this.scanWarning.set(null);
    this.prefilledFields.set(new Set());

    try {
      const prepared = await prepareReceiptFile(file);
      if (seq !== this.receiptScanSeq || !this.launcher.isOpen()) {
        return;
      }

      const initial = await firstValueFrom(this.receiptsApi.scan(prepared));
      if (seq !== this.receiptScanSeq || !this.launcher.isOpen()) {
        return;
      }

      this.receiptAttachmentId.set(initial.id);

      if (initial.status === 'PARSED') {
        this.handleParsedReceipt(initial);
        return;
      }
      if (initial.status === 'FAILED') {
        this.handleFailedReceipt();
        return;
      }

      this.startReceiptPolling(initial.id, seq);
    } catch (err) {
      if (seq !== this.receiptScanSeq) {
        return;
      }
      this.scanning.set(false);
      this.toast.error(this.extractReceiptUploadError(err));
    }
  }

  private startReceiptPolling(receiptId: string, seq: number): void {
    this.stopPolling();

    this.pollTimeoutHandle = setTimeout(() => {
      if (seq !== this.receiptScanSeq) {
        return;
      }
      this.stopPolling();
      this.scanning.set(false);
      this.scanWarning.set(
        'La lectura del ticket tardó demasiado. Completa los datos manualmente; el comprobante quedó adjunto.',
      );
    }, RECEIPT_POLL_TIMEOUT_MS);

    this.pollSub = timer(RECEIPT_POLL_INTERVAL_MS, RECEIPT_POLL_INTERVAL_MS)
      .pipe(
        exhaustMap(() =>
          this.receiptsApi.get(receiptId).pipe(catchError(() => of<Receipt | null>(null))),
        ),
      )
      .subscribe((receipt) => {
        if (seq !== this.receiptScanSeq) {
          this.stopPolling();
          return;
        }
        if (!receipt) {
          return;
        }
        if (receipt.status === 'PARSED') {
          this.stopPolling();
          this.handleParsedReceipt(receipt);
        } else if (receipt.status === 'FAILED') {
          this.stopPolling();
          this.handleFailedReceipt();
        }
      });
  }

  private stopPolling(): void {
    if (this.pollSub) {
      this.pollSub.unsubscribe();
      this.pollSub = null;
    }
    if (this.pollTimeoutHandle !== null) {
      clearTimeout(this.pollTimeoutHandle);
      this.pollTimeoutHandle = null;
    }
  }

  private handleParsedReceipt(receipt: Receipt): void {
    this.scanning.set(false);
    const draft = receipt.draft;
    if (!draft) {
      this.scanWarning.set('Revisa los datos: la lectura no fue precisa');
      return;
    }

    if (this.transactionType() !== 'EXPENSE') {
      this.onTypeChange('EXPENSE');
    }

    const marked = new Set<PrefilledField>();

    if (draft.totalAmount !== null && draft.totalAmount !== undefined) {
      const normalized = normalizeDecimalInput(String(draft.totalAmount));
      if (toApiDecimal(normalized)) {
        this.rawAmount.set(normalized);
        this.desktopDisplayValue.set(normalized);
        marked.add('amount');
      }
    }

    if (draft.currencyCode && draft.currencyCode.trim().length > 0) {
      const code = draft.currencyCode.trim().toUpperCase();
      if (this.enabledCurrencies().includes(code)) {
        this.currencyCode.set(code);
        this.currencyMenuOpen.set(false);
        this.selectedAccountIdOverride.set(null);
        marked.add('currency');
      }
    }

    if (draft.date && ISO_DATE_PATTERN.test(draft.date.trim())) {
      this.transactionDate.set(draft.date.trim());
      marked.add('date');
    }

    if (draft.merchant && draft.merchant.trim().length > 0) {
      this.description.set(draft.merchant.trim().slice(0, 140));
      marked.add('description');
    }

    const categoryId = this.resolveSuggestedCategoryId(draft);
    if (categoryId) {
      this.selectedCategoryId.set(categoryId);
      marked.add('category');
    }

    this.prefilledFields.set(marked);

    const confidence = this.parseConfidence(draft.confidence);
    if (confidence !== null && confidence < LOW_CONFIDENCE_THRESHOLD) {
      this.scanWarning.set('Revisa los datos: la lectura no fue precisa');
    } else {
      this.scanWarning.set(null);
    }
  }

  private handleFailedReceipt(): void {
    this.scanning.set(false);
    this.scanWarning.set(null);
    this.toast.error('No se pudo leer el ticket; el comprobante quedó adjunto');
  }

  private resolveSuggestedCategoryId(draft: ReceiptDraft): string | null {
    const expenseCategories = this.expenseAll();
    if (draft.suggestedCategoryId) {
      const direct = expenseCategories.find((cat) => cat.id === draft.suggestedCategoryId);
      if (direct) {
        return direct.id;
      }
    }
    if (draft.suggestedCategoryKey) {
      const byKey = this.categoriesStore.bySystemKey(draft.suggestedCategoryKey);
      if (byKey && byKey.kind === 'EXPENSE' && byKey.isActive !== false) {
        return byKey.id;
      }
    }
    return null;
  }

  private parseConfidence(raw: string | number | null | undefined): number | null {
    if (raw === null || raw === undefined || raw === '') {
      return null;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private clearPrefilled(field: PrefilledField): void {
    if (!this.prefilledFields().has(field)) {
      return;
    }
    this.prefilledFields.update((current) => {
      const next = new Set(current);
      next.delete(field);
      return next;
    });
  }

  private extractReceiptUploadError(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 413) {
        return extractQuickAddProblemMessage(
          err,
          'El archivo supera el tamaño máximo permitido (5 MB).',
        );
      }
      if (err.status === 415) {
        return extractQuickAddProblemMessage(
          err,
          'Formato no compatible. Usa JPG, PNG, WebP o PDF.',
        );
      }
    }
    return extractQuickAddProblemMessage(
      err,
      'No pudimos subir el comprobante. Intenta de nuevo.',
    );
  }

  private focusAmount(): void {
    const desktopInput = this.desktopAmountInput()?.nativeElement;
    if (desktopInput) {
      desktopInput.focus();
      return;
    }
    const mobileDisplay = this.mobileAmountDisplay()?.nativeElement;
    mobileDisplay?.focus();
  }

  private async ensureDataLoaded(): Promise<void> {
    const tasks: Promise<unknown>[] = [];
    tasks.push(this.categoriesStore.load(false).catch(() => undefined));
    if (this.accountsStore.accounts().length === 0 && !this.accountsStore.loading()) {
      tasks.push(this.accountsStore.load().catch(() => undefined));
    }
    if (this.session.isAdult() && !this.membersLoaded) {
      this.membersLoaded = true;
      tasks.push(
        firstValueFrom(this.membersApi.list())
          .then((list) => this.members.set(list))
          .catch(() => {
            this.membersLoaded = false;
          }),
      );
    }
    await Promise.all(tasks);
  }
}
