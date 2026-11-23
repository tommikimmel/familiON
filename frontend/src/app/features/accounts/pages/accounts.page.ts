import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ACCOUNT_TYPE_LABEL, ACCOUNT_TYPE_ORDER } from '../../../core/finance/account-labels';
import { AccountsStore } from '../../../core/finance/accounts.store';
import { CategoriesStore } from '../../../core/finance/categories.store';
import {
  Account,
  AccountCreate,
  AccountType,
  Category,
  CategoryKind,
} from '../../../core/finance/finance.models';
import { SessionStore } from '../../../core/state/session.store';
import { View, ViewContextStore } from '../../../core/state/view-context.store';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import {
  BottomSheetComponent,
  ButtonComponent,
  CardComponent,
  EmptyStateComponent,
  IconComponent,
  SegmentedOption,
  SegmentedToggleComponent,
  SkeletonComponent,
  ToastService,
} from '../../../shared/ui';
import { toNumber } from '../../../shared/utils/decimal';
import { AccountCardComponent } from '../components/account-card.component';
import { AccountFormComponent } from '../components/account-form.component';
import { CategoryFormComponent, CategoryFormSubmit } from '../components/category-form.component';
import {
  CategoryTreeComponent,
  CreateCategoryIntent,
} from '../components/category-tree.component';
import { FamilyMemberOption, FamilyMembersApi } from '../data-access/family-members.api';
import { extractProblemMessage } from '../utils/problem-message';

type AccountsTab = 'accounts' | 'categories';

interface CurrencyTotal {
  currencyCode: string;
  total: number;
  count: number;
}

interface AccountGroup {
  type: AccountType;
  label: string;
  accounts: Account[];
}

const TAB_OPTIONS: readonly SegmentedOption<AccountsTab>[] = [
  { value: 'accounts', label: 'Cuentas' },
  { value: 'categories', label: 'Categorías' },
];

@Component({
  selector: 'fo-accounts-page',
  standalone: true,
  imports: [
    AccountCardComponent,
    AccountFormComponent,
    BottomSheetComponent,
    ButtonComponent,
    CardComponent,
    CategoryFormComponent,
    CategoryTreeComponent,
    EmptyStateComponent,
    IconComponent,
    MoneyPipe,
    SegmentedToggleComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-5xl space-y-5">
      <header class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="text-xl font-semibold text-text">Cuentas</h1>
          <p class="mt-0.5 text-sm text-muted">
            Administrá tus medios de pago y la estructura de categorías.
          </p>
        </div>

        @if (activeTab() === 'accounts') {
          <fo-button variant="primary" (click)="openCreateAccount()">
            <fo-icon name="plus" [size]="16" />
            <span>Nueva cuenta</span>
          </fo-button>
        } @else if (session.isAdult()) {
          <fo-button
            variant="primary"
            (click)="openCreateCategory({ kind: 'EXPENSE', parentCategoryId: null })"
          >
            <fo-icon name="plus" [size]="16" />
            <span>Nueva categoría</span>
          </fo-button>
        }
      </header>

      <div class="max-w-xs">
        <fo-segmented-toggle
          [options]="tabOptions"
          [(value)]="activeTab"
          ariaLabel="Cambiar entre cuentas y categorías"
        />
      </div>

      @if (activeTab() === 'accounts') {
        @if (accountsStore.loading() && accountsStore.accounts().length === 0) {
          <div class="space-y-4" aria-label="Cargando cuentas">
            <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <fo-skeleton height="5.25rem" rounded="lg" />
              <fo-skeleton height="5.25rem" rounded="lg" />
            </div>
            <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
              <fo-skeleton height="7.5rem" rounded="lg" />
              <fo-skeleton height="7.5rem" rounded="lg" />
            </div>
          </div>
        } @else if (accountsError(); as error) {
          <fo-empty-state icon="alert-circle" title="No pudimos cargar tus cuentas" [description]="error">
            <fo-button variant="primary" (click)="reloadAccounts()">Reintentar</fo-button>
          </fo-empty-state>
        } @else if (activeAccounts().length === 0) {
          <fo-empty-state
            icon="wallet"
            title="Todavía no tenés cuentas"
            description="Creá tu primera cuenta de efectivo, banco, billetera virtual o tarjeta para empezar a registrar movimientos."
          >
            <fo-button variant="primary" (click)="openCreateAccount()">
              <fo-icon name="plus" [size]="16" />
              <span>Nueva cuenta</span>
            </fo-button>
          </fo-empty-state>
        } @else {
          <section aria-label="Totales por divisa" class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            @for (summary of currencyTotals(); track summary.currencyCode) {
              <fo-card padding="md">
                <div class="flex items-center justify-between gap-2">
                  <span
                    class="rounded-full border border-border bg-surface-2 px-2.5 py-0.5 text-xs font-semibold tracking-wide text-text"
                  >
                    {{ summary.currencyCode }}
                  </span>
                  <span class="text-xs text-muted">
                    {{ summary.count }} {{ summary.count === 1 ? 'cuenta' : 'cuentas' }}
                  </span>
                </div>
                <p
                  class="mt-2 text-2xl font-semibold tabular-nums"
                  [class.text-danger]="summary.total < 0"
                  [class.text-text]="summary.total >= 0"
                >
                  {{ summary.total | money: summary.currencyCode }}
                </p>
              </fo-card>
            }
          </section>

          <div class="space-y-6">
            @for (group of groupedAccounts(); track group.type) {
              <section [attr.aria-label]="group.label" class="space-y-2.5">
                <div class="flex items-center gap-2">
                  <h2 class="text-sm font-semibold text-text">{{ group.label }}</h2>
                  <span
                    class="rounded-full bg-surface-2 px-2 py-0.5 text-xs font-medium text-muted tabular-nums"
                  >
                    {{ group.accounts.length }}
                  </span>
                </div>

                <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
                  @for (account of group.accounts; track account.id) {
                    <fo-account-card
                      [account]="account"
                      [isDefault]="isDefaultAccount(account)"
                      [ownerLabel]="ownerLabelFor(account)"
                      [canManage]="canManageAccount(account)"
                      (edit)="openEditAccount($event)"
                      (archive)="openArchiveAccount($event)"
                      (setDefault)="markAsDefault($event)"
                    />
                  }
                </div>
              </section>
            }
          </div>
        }
      } @else {
        @if (categoriesStore.loading() && categoriesStore.categories().length === 0) {
          <div class="grid grid-cols-1 gap-4 lg:grid-cols-2" aria-label="Cargando categorías">
            <div class="space-y-2.5">
              <fo-skeleton height="3.75rem" rounded="lg" />
              <fo-skeleton height="3.75rem" rounded="lg" />
              <fo-skeleton height="3.75rem" rounded="lg" />
            </div>
            <div class="space-y-2.5">
              <fo-skeleton height="3.75rem" rounded="lg" />
              <fo-skeleton height="3.75rem" rounded="lg" />
            </div>
          </div>
        } @else if (categoriesError(); as error) {
          <fo-empty-state
            icon="alert-circle"
            title="No pudimos cargar las categorías"
            [description]="error"
          >
            <fo-button variant="primary" (click)="reloadCategories()">Reintentar</fo-button>
          </fo-empty-state>
        } @else {
          <fo-category-tree
            [expenseTree]="expenseTree()"
            [incomeTree]="incomeTree()"
            [isAdult]="session.isAdult()"
            (createCategory)="openCreateCategory($event)"
            (editCategory)="openEditCategory($event)"
            (deactivateCategory)="openDeactivateCategory($event)"
          />
        }
      }
    </section>

    <fo-bottom-sheet
      [(open)]="accountSheetOpen"
      [title]="editingAccount() ? 'Editar cuenta' : 'Nueva cuenta'"
      (closed)="resetAccountSheet()"
    >
      @if (accountSheetOpen()) {
        <fo-account-form
          [account]="editingAccount()"
          [enabledCurrencies]="enabledCurrencies()"
          [defaultCurrency]="defaultCurrency()"
          [isAdult]="session.isAdult()"
          [currentUserId]="currentUserId()"
          [members]="members()"
          [saving]="savingAccount()"
          [errorMessage]="accountFormError()"
          (submitted)="saveAccount($event)"
          (cancelled)="closeAccountSheet()"
        />
      }
    </fo-bottom-sheet>

    <fo-bottom-sheet
      [(open)]="archiveSheetOpen"
      title="Archivar cuenta"
      (closed)="archivingAccount.set(null)"
    >
      @if (archivingAccount(); as target) {
        <div class="space-y-4">
          <p class="text-sm text-text">
            ¿Querés archivar <strong class="font-semibold">{{ target.name }}</strong>?
          </p>
          <p class="rounded-xl border border-border bg-surface-2 px-3.5 py-2.5 text-sm text-muted">
            La cuenta se archivará; los movimientos históricos se conservan.
          </p>
          <div class="flex items-center justify-end gap-2 pt-1">
            <fo-button
              variant="ghost"
              type="button"
              [disabled]="archivingInProgress()"
              (click)="closeArchiveSheet()"
            >
              Cancelar
            </fo-button>
            <fo-button
              variant="danger"
              type="button"
              [loading]="archivingInProgress()"
              (click)="confirmArchiveAccount()"
            >
              Archivar cuenta
            </fo-button>
          </div>
        </div>
      }
    </fo-bottom-sheet>

    <fo-bottom-sheet
      [(open)]="categorySheetOpen"
      [title]="editingCategory() ? 'Editar categoría' : 'Nueva categoría'"
      (closed)="resetCategorySheet()"
    >
      @if (categorySheetOpen()) {
        <fo-category-form
          [category]="editingCategory()"
          [initialKind]="categoryInitialKind()"
          [initialParentId]="categoryInitialParentId()"
          [expenseRoots]="expenseTree()"
          [incomeRoots]="incomeTree()"
          [saving]="savingCategory()"
          [errorMessage]="categoryFormError()"
          (submitted)="saveCategory($event)"
          (cancelled)="closeCategorySheet()"
        />
      }
    </fo-bottom-sheet>

    <fo-bottom-sheet
      [(open)]="deactivateCategorySheetOpen"
      title="Desactivar categoría"
      (closed)="deactivatingCategory.set(null)"
    >
      @if (deactivatingCategory(); as target) {
        <div class="space-y-4">
          <p class="text-sm text-text">
            ¿Querés desactivar <strong class="font-semibold">{{ target.name }}</strong>?
          </p>
          <p class="rounded-xl border border-border bg-surface-2 px-3.5 py-2.5 text-sm text-muted">
            Dejará de aparecer al cargar nuevos movimientos, pero los movimientos históricos se
            conservan.
          </p>
          <div class="flex items-center justify-end gap-2 pt-1">
            <fo-button
              variant="ghost"
              type="button"
              [disabled]="deactivatingCategoryInProgress()"
              (click)="closeDeactivateCategorySheet()"
            >
              Cancelar
            </fo-button>
            <fo-button
              variant="danger"
              type="button"
              [loading]="deactivatingCategoryInProgress()"
              (click)="confirmDeactivateCategory()"
            >
              Desactivar categoría
            </fo-button>
          </div>
        </div>
      }
    </fo-bottom-sheet>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountsPage {
  protected readonly accountsStore = inject(AccountsStore);
  protected readonly categoriesStore = inject(CategoriesStore);
  protected readonly viewContext = inject(ViewContextStore);
  protected readonly session = inject(SessionStore);
  private readonly membersApi = inject(FamilyMembersApi);
  private readonly toast = inject(ToastService);

  protected readonly tabOptions = TAB_OPTIONS;
  protected readonly activeTab = signal<AccountsTab>('accounts');

  protected readonly accountsError = signal<string | null>(null);
  protected readonly categoriesError = signal<string | null>(null);
  protected readonly members = signal<FamilyMemberOption[]>([]);
  private membersLoaded = false;

  protected readonly accountSheetOpen = signal<boolean>(false);
  protected readonly editingAccount = signal<Account | null>(null);
  protected readonly savingAccount = signal<boolean>(false);
  protected readonly accountFormError = signal<string | null>(null);

  protected readonly archiveSheetOpen = signal<boolean>(false);
  protected readonly archivingAccount = signal<Account | null>(null);
  protected readonly archivingInProgress = signal<boolean>(false);

  protected readonly categorySheetOpen = signal<boolean>(false);
  protected readonly editingCategory = signal<Category | null>(null);
  protected readonly categoryInitialKind = signal<CategoryKind>('EXPENSE');
  protected readonly categoryInitialParentId = signal<string | null>(null);
  protected readonly savingCategory = signal<boolean>(false);
  protected readonly categoryFormError = signal<string | null>(null);

  protected readonly deactivateCategorySheetOpen = signal<boolean>(false);
  protected readonly deactivatingCategory = signal<Category | null>(null);
  protected readonly deactivatingCategoryInProgress = signal<boolean>(false);

  protected readonly expenseTree = this.categoriesStore.tree('EXPENSE');
  protected readonly incomeTree = this.categoriesStore.tree('INCOME');

  protected readonly currentUserId = computed(() => this.session.me()?.id ?? null);

  protected readonly enabledCurrencies = computed(() => {
    const enabled = this.session.me()?.family.enabledCurrencies ?? [];
    return enabled.length > 0 ? enabled : ['ARS', 'USD'];
  });

  protected readonly defaultCurrency = computed(
    () => this.session.me()?.family.baseCurrency ?? 'ARS',
  );

  protected readonly activeAccounts = computed(() =>
    this.accountsStore.accounts().filter((account) => account.isActive),
  );

  protected readonly currencyTotals = computed<CurrencyTotal[]>(() => {
    const totals = new Map<string, { scaled: number; count: number }>();

    for (const account of this.activeAccounts()) {
      const code = account.currencyCode.trim().toUpperCase();
      const entry = totals.get(code) ?? { scaled: 0, count: 0 };
      entry.scaled += Math.round(toNumber(account.currentBalance) * 10000);
      entry.count += 1;
      totals.set(code, entry);
    }

    const baseCurrency = this.session.me()?.family.baseCurrency?.toUpperCase() ?? '';
    const refCurrency = this.session.me()?.family.referenceCurrency?.toUpperCase() ?? '';

    return [...totals.entries()]
      .sort(([a], [b]) => {
        if (a === baseCurrency && b !== baseCurrency) {
          return -1;
        }
        if (b === baseCurrency && a !== baseCurrency) {
          return 1;
        }
        if (a === refCurrency && b !== refCurrency) {
          return -1;
        }
        if (b === refCurrency && a !== refCurrency) {
          return 1;
        }
        return a.localeCompare(b);
      })
      .map(([currencyCode, entry]) => ({
        currencyCode,
        total: entry.scaled / 10000,
        count: entry.count,
      }));
  });

  protected readonly groupedAccounts = computed<AccountGroup[]>(() => {
    const byType = new Map<AccountType, Account[]>();
    for (const account of this.activeAccounts()) {
      const bucket = byType.get(account.accountType) ?? [];
      bucket.push(account);
      byType.set(account.accountType, bucket);
    }

    return ACCOUNT_TYPE_ORDER.filter((type) => (byType.get(type)?.length ?? 0) > 0).map(
      (type) => ({
        type,
        label: ACCOUNT_TYPE_LABEL[type],
        accounts: byType.get(type) ?? [],
      }),
    );
  });

  private readonly memberNamesById = computed(() => {
    const map = new Map<string, string>();
    for (const member of this.members()) {
      map.set(member.id, member.fullName);
    }
    return map;
  });

  constructor() {
    effect(() => {
      const currentView = this.viewContext.view();
      void this.loadAccounts(currentView);
    });

    effect(() => {
      if (this.activeTab() === 'categories') {
        void this.loadCategories(false);
      }
    });

    effect(() => {
      if (this.session.isAdult() && !this.membersLoaded) {
        this.membersLoaded = true;
        void this.loadFamilyMembers();
      }
    });
  }

  protected reloadAccounts(): void {
    void this.loadAccounts(this.viewContext.view());
  }

  protected reloadCategories(): void {
    void this.loadCategories(true);
  }

  protected isDefaultAccount(account: Account): boolean {
    return this.accountsStore.defaultAccountFor(account.currencyCode)?.id === account.id;
  }

  protected ownerLabelFor(account: Account): string | null {
    if (account.ownerUserId === null) {
      return null;
    }
    if (account.ownerUserId === this.currentUserId()) {
      return null;
    }
    return this.memberNamesById().get(account.ownerUserId) ?? null;
  }

  protected canManageAccount(account: Account): boolean {
    if (this.session.isAdult()) {
      return true;
    }
    return account.ownerUserId === this.currentUserId();
  }

  protected openCreateAccount(): void {
    this.editingAccount.set(null);
    this.accountFormError.set(null);
    this.accountSheetOpen.set(true);
  }

  protected openEditAccount(account: Account): void {
    this.editingAccount.set(account);
    this.accountFormError.set(null);
    this.accountSheetOpen.set(true);
  }

  protected closeAccountSheet(): void {
    this.accountSheetOpen.set(false);
    this.resetAccountSheet();
  }

  protected resetAccountSheet(): void {
    this.editingAccount.set(null);
    this.accountFormError.set(null);
  }

  protected async saveAccount(payload: AccountCreate): Promise<void> {
    this.savingAccount.set(true);
    this.accountFormError.set(null);

    const editing = this.editingAccount();
    try {
      if (editing) {
        await this.accountsStore.update(editing.id, payload);
        this.toast.success('Cuenta actualizada.');
      } else {
        await this.accountsStore.create(payload);
        this.toast.success('Cuenta creada.');
      }

      await this.accountsStore.load(this.viewContext.view());
      this.closeAccountSheet();
    } catch (err) {
      this.accountFormError.set(
        extractProblemMessage(err, 'No pudimos guardar la cuenta. Intentá de nuevo.'),
      );
    } finally {
      this.savingAccount.set(false);
    }
  }

  protected openArchiveAccount(account: Account): void {
    this.archivingAccount.set(account);
    this.archiveSheetOpen.set(true);
  }

  protected closeArchiveSheet(): void {
    this.archiveSheetOpen.set(false);
    this.archivingAccount.set(null);
  }

  protected async confirmArchiveAccount(): Promise<void> {
    const target = this.archivingAccount();
    if (!target) {
      return;
    }

    this.archivingInProgress.set(true);
    try {
      await this.accountsStore.remove(target.id);
      await this.accountsStore.load(this.viewContext.view());
      this.closeArchiveSheet();
      this.toast.success('Cuenta archivada.');
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos archivar la cuenta. Intentá de nuevo.'),
      );
    } finally {
      this.archivingInProgress.set(false);
    }
  }

  protected markAsDefault(account: Account): void {
    this.accountsStore.setDefaultAccount(account.currencyCode, account.id);
    this.toast.success(`"${account.name}" quedó como cuenta habitual en ${account.currencyCode}.`);
  }

  protected openCreateCategory(intent: CreateCategoryIntent): void {
    this.editingCategory.set(null);
    this.categoryInitialKind.set(intent.kind);
    this.categoryInitialParentId.set(intent.parentCategoryId);
    this.categoryFormError.set(null);
    this.categorySheetOpen.set(true);
  }

  protected openEditCategory(category: Category): void {
    this.editingCategory.set(category);
    this.categoryInitialKind.set(category.kind);
    this.categoryInitialParentId.set(category.parentCategoryId);
    this.categoryFormError.set(null);
    this.categorySheetOpen.set(true);
  }

  protected closeCategorySheet(): void {
    this.categorySheetOpen.set(false);
    this.resetCategorySheet();
  }

  protected resetCategorySheet(): void {
    this.editingCategory.set(null);
    this.categoryFormError.set(null);
  }

  protected async saveCategory(event: CategoryFormSubmit): Promise<void> {
    this.savingCategory.set(true);
    this.categoryFormError.set(null);

    try {
      if (event.mode === 'edit') {
        await this.categoriesStore.update(event.id, event.payload);
        this.toast.success('Categoría actualizada.');
      } else {
        await this.categoriesStore.create(event.payload);
        this.toast.success('Categoría creada.');
      }
      this.closeCategorySheet();
    } catch (err) {
      this.categoryFormError.set(
        extractProblemMessage(err, 'No pudimos guardar la categoría. Intentá de nuevo.'),
      );
    } finally {
      this.savingCategory.set(false);
    }
  }

  protected openDeactivateCategory(category: Category): void {
    this.deactivatingCategory.set(category);
    this.deactivateCategorySheetOpen.set(true);
  }

  protected closeDeactivateCategorySheet(): void {
    this.deactivateCategorySheetOpen.set(false);
    this.deactivatingCategory.set(null);
  }

  protected async confirmDeactivateCategory(): Promise<void> {
    const target = this.deactivatingCategory();
    if (!target) {
      return;
    }

    this.deactivatingCategoryInProgress.set(true);
    try {
      await this.categoriesStore.update(target.id, { isActive: false });
      this.closeDeactivateCategorySheet();
      this.toast.success('Categoría desactivada.');
    } catch (err) {
      this.toast.error(
        extractProblemMessage(err, 'No pudimos desactivar la categoría. Intentá de nuevo.'),
      );
    } finally {
      this.deactivatingCategoryInProgress.set(false);
    }
  }

  private async loadAccounts(view: View): Promise<void> {
    this.accountsError.set(null);
    try {
      await this.accountsStore.load(view);
    } catch (err) {
      this.accountsError.set(
        extractProblemMessage(err, 'Ocurrió un error al obtener las cuentas.'),
      );
    }
  }

  private async loadCategories(force: boolean): Promise<void> {
    this.categoriesError.set(null);
    try {
      await this.categoriesStore.load(force);
    } catch (err) {
      this.categoriesError.set(
        extractProblemMessage(err, 'Ocurrió un error al obtener las categorías.'),
      );
    }
  }

  private async loadFamilyMembers(): Promise<void> {
    try {
      const list = await firstValueFrom(this.membersApi.list());
      this.members.set(list);
    } catch {
      // Non-blocking: owner picker still offers "Yo" and "Compartida".
      this.members.set([]);
    }
  }
}
