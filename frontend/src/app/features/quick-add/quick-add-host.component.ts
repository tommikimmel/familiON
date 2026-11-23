import { A11yModule } from '@angular/cdk/a11y';
import { BreakpointObserver } from '@angular/cdk/layout';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { firstValueFrom, map } from 'rxjs';
import { AccountsStore } from '../../core/finance/accounts.store';
import { CategoriesStore } from '../../core/finance/categories.store';
import { FinanceEvents } from '../../core/finance/finance-events';
import { Account } from '../../core/finance/finance.models';
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
  ButtonComponent,
  IconComponent,
  SegmentedOption,
  SegmentedToggleComponent,
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
import {
  QuickAddFamilyMember,
  QuickAddMembersApi,
} from './data-access/quick-add-members.api';
import { installmentPreview } from './installment-preview';
import { extractQuickAddProblemMessage } from './utils/problem-message';

const DESKTOP_BREAKPOINT = '(min-width: 1024px)';

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
    ButtonComponent,
    CategoryPickerComponent,
    IconComponent,
    QuickAddAdvancedComponent,
    SegmentedToggleComponent,
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
        <div data-testid="quick-add-sheet" class="space-y-4">
          <header class="flex items-center justify-between gap-2">
            <div class="max-w-[220px] flex-1">
              <fo-segmented-toggle
                [options]="typeOptions"
                [value]="transactionType()"
                (valueChange)="onTypeChange($event)"
                ariaLabel="Tipo de movimiento"
                size="sm"
              />
            </div>

            <div class="flex items-center gap-1">
              <button
                type="button"
                disabled
                title="Próximamente"
                aria-label="Escanear comprobante (próximamente)"
                data-testid="quick-add-camera-btn"
                class="inline-flex h-8 w-8 items-center justify-center rounded-full text-muted/60 disabled:cursor-not-allowed"
              >
                <fo-icon name="camera" [size]="18" />
              </button>

              <button
                type="button"
                aria-label="Cerrar"
                data-testid="quick-add-close-btn"
                (click)="closeSheet()"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-2 hover:text-text"
              >
                <fo-icon name="x" [size]="18" />
              </button>
            </div>
          </header>

          <section aria-label="Monto" class="space-y-2">
            <div class="flex items-center justify-between gap-3 rounded-2xl border border-border bg-surface-2/50 px-4 py-3">
              <div class="flex min-w-0 flex-1 items-baseline gap-2">
                <span class="shrink-0 text-xl font-semibold text-muted" aria-hidden="true">
                  {{ activeCurrencySymbol() }}
                </span>

                @if (isDesktop()) {
                  <label for="fo-quick-add-amount" class="sr-only">Monto</label>
                  <input
                    #desktopAmountInput
                    id="fo-quick-add-amount"
                    data-testid="quick-add-amount-input"
                    cdkFocusInitial
                    type="text"
                    inputmode="decimal"
                    autocomplete="off"
                    placeholder="0"
                    [value]="desktopDisplayValue()"
                    (input)="onDesktopAmountInput($event)"
                    class="fo-focus-ring w-full min-w-0 bg-transparent text-[40px] leading-none font-bold tracking-tight text-text tabular-nums placeholder:text-muted/45 focus:outline-none"
                  />
                } @else {
                  <div
                    #mobileAmountDisplay
                    data-testid="quick-add-amount-display"
                    tabindex="0"
                    cdkFocusInitial
                    role="textbox"
                    aria-readonly="true"
                    aria-label="Monto"
                    class="fo-focus-ring min-w-0 flex-1 truncate rounded-lg text-[40px] leading-none font-bold tracking-tight tabular-nums"
                    [class.text-text]="rawAmount().length > 0"
                    [class.text-muted/45]="rawAmount().length === 0"
                    [class.fo-amount-bump-a]="bumpPhase() === 'a'"
                    [class.fo-amount-bump-b]="bumpPhase() === 'b'"
                  >
                    {{ formattedMobileAmount() }}
                  </div>
                }
              </div>

              <div class="relative shrink-0">
                <button
                  type="button"
                  data-testid="quick-add-currency-chip"
                  [disabled]="enabledCurrencies().length <= 1"
                  [attr.aria-expanded]="enabledCurrencies().length > 1 ? currencyMenuOpen() : null"
                  aria-label="Divisa del movimiento"
                  (click)="toggleCurrencyMenu()"
                  class="fo-focus-ring inline-flex items-center gap-1 rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-semibold text-text transition-colors hover:border-accent disabled:cursor-default disabled:hover:border-border"
                >
                  <span>{{ currencyCode() }}</span>
                  @if (enabledCurrencies().length > 1) {
                    <fo-icon name="chevron-down" [size]="12" />
                  }
                </button>

                @if (currencyMenuOpen() && enabledCurrencies().length > 1) {
                  <div
                    role="listbox"
                    aria-label="Elegir divisa"
                    data-testid="quick-add-currency-menu"
                    class="absolute right-0 z-20 mt-1.5 min-w-24 rounded-xl border border-border bg-surface p-1 shadow-lg"
                  >
                    @for (code of enabledCurrencies(); track code) {
                      <button
                        type="button"
                        role="option"
                        [attr.aria-selected]="code === currencyCode()"
                        [attr.data-testid]="'quick-add-currency-option-' + code"
                        (click)="selectCurrency(code)"
                        class="fo-focus-ring flex w-full cursor-pointer items-center justify-between gap-2 rounded-lg px-2.5 py-1.5 text-xs font-semibold transition-colors hover:bg-surface-2"
                        [class.text-accent]="code === currencyCode()"
                        [class.text-text]="code !== currencyCode()"
                      >
                        <span>{{ code }}</span>
                        @if (code === currencyCode()) {
                          <fo-icon name="check" [size]="12" />
                        }
                      </button>
                    }
                  </div>
                }
              </div>
            </div>
          </section>

          <fo-category-picker
            [frequentCategories]="frequentCategories()"
            [allCategories]="allCategoriesForKind()"
            [selectedCategoryId]="selectedCategoryId()"
            [description]="description()"
            (selectedCategoryIdChange)="selectedCategoryId.set($event)"
            (descriptionChange)="description.set($event)"
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
            [installmentPreviewText]="installmentPreviewText()"
            [summaryHint]="advancedSummaryHint()"
            (expandedChange)="advancedExpanded.set($event)"
            (accountIdChange)="selectedAccountIdOverride.set($event)"
            (transactionDateChange)="transactionDate.set($event)"
            (attributedToUserIdChange)="attributedToUserId.set($event)"
            (installmentsChange)="onInstallmentsChange($event)"
            (amountModeChange)="amountMode.set($event)"
            (hasInterestChange)="onHasInterestChange($event)"
            (cashPriceChange)="cashPrice.set($event)"
            (navigateToAccounts)="closeSheet()"
          />

          @if (!isDesktop()) {
            <fo-amount-keypad
              [disabled]="saving()"
              [canDelete]="rawAmount().length > 0"
              (keyPress)="onKeypadPress($event)"
            />
          }

          <div class="pt-1">
            <fo-button
              type="button"
              variant="primary"
              size="lg"
              [fullWidth]="true"
              [disabled]="!canSave()"
              [loading]="saving()"
              data-testid="quick-add-save-btn"
              (click)="save()"
            >
              Guardar
            </fo-button>
          </div>
        </div>
      }
    </fo-bottom-sheet>
  `,
  styles: `
    @keyframes fo-amount-bump-a {
      0% {
        transform: scale(1);
      }
      45% {
        transform: scale(1.04);
      }
      100% {
        transform: scale(1);
      }
    }

    @keyframes fo-amount-bump-b {
      0% {
        transform: scale(1);
      }
      45% {
        transform: scale(1.04);
      }
      100% {
        transform: scale(1);
      }
    }

    .fo-amount-bump-a {
      transform-origin: left center;
      animation: fo-amount-bump-a 140ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1));
    }

    .fo-amount-bump-b {
      transform-origin: left center;
      animation: fo-amount-bump-b 140ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1));
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuickAddHostComponent {
  private readonly breakpointObserver = inject(BreakpointObserver);
  protected readonly session = inject(SessionStore);
  private readonly accountsStore = inject(AccountsStore);
  private readonly categoriesStore = inject(CategoriesStore);
  private readonly transactionsApi = inject(TransactionsApi);
  private readonly membersApi = inject(QuickAddMembersApi);
  private readonly financeEvents = inject(FinanceEvents);
  private readonly toast = inject(ToastService);

  protected readonly launcher = inject(QuickAddLauncher);
  protected readonly typeOptions = TYPE_OPTIONS;

  private readonly desktopAmountInput =
    viewChild<ElementRef<HTMLInputElement>>('desktopAmountInput');
  private readonly mobileAmountDisplay =
    viewChild<ElementRef<HTMLElement>>('mobileAmountDisplay');

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
  protected readonly bumpPhase = signal<'idle' | 'a' | 'b'>('idle');

  private membersLoaded = false;

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

  protected readonly installmentPreviewText = computed<string | null>(() => {
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
    return result?.summary ?? null;
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
    if (this.saving()) {
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
  }

  protected onKeypadPress(key: KeypadKey): void {
    const next = applyKeypadKey(this.rawAmount(), key);
    this.rawAmount.set(next);
    this.desktopDisplayValue.set(next);
    this.bumpPhase.update((phase) => (phase === 'a' ? 'b' : 'a'));
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

      const body: TransactionCreate = {
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
    this.bumpPhase.set('idle');
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
