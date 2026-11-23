import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Account } from '../../../core/finance/finance.models';
import {
  InstallmentAmountMode,
  TransactionType,
} from '../../../core/finance/transactions.models';
import {
  IconComponent,
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../shared/ui';
import { readableTextOn } from '../../../shared/utils/color';
import { normalizeDecimalInput } from '../amount-input';
import { QuickAddFamilyMember } from '../data-access/quick-add-members.api';

type InterestChoice = 'NO_INTEREST' | 'WITH_INTEREST';

const INTEREST_OPTIONS: readonly SegmentedOption<InterestChoice>[] = [
  { value: 'NO_INTEREST', label: 'Sin interés' },
  { value: 'WITH_INTEREST', label: 'Con interés' },
];

const AMOUNT_MODE_OPTIONS: readonly { value: InstallmentAmountMode; label: string }[] = [
  { value: 'TOTAL', label: 'Es el total' },
  { value: 'PER_INSTALLMENT', label: 'Es cada cuota' },
];

function initialsFor(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

@Component({
  selector: 'fo-quick-add-advanced',
  standalone: true,
  imports: [IconComponent, RouterLink, SegmentedToggleComponent],
  template: `
    <div class="rounded-xl border border-border bg-surface-2/40">
      <button
        type="button"
        data-testid="quick-add-more-options-btn"
        [attr.aria-expanded]="expanded()"
        (click)="toggleExpanded()"
        class="fo-focus-ring flex w-full cursor-pointer items-center justify-between gap-2 rounded-xl px-3.5 py-2.5 text-left text-xs font-medium text-muted transition-colors hover:text-text"
      >
        <span class="flex items-center gap-1.5">
          <span>Más opciones</span>
          @if (!expanded() && summaryHint()) {
            <span class="truncate font-normal text-muted/80">· {{ summaryHint() }}</span>
          }
        </span>
        <fo-icon
          name="chevron-down"
          [size]="16"
          class="transition-transform duration-150"
          [class.rotate-180]="expanded()"
        />
      </button>

      @if (expanded()) {
        <div data-testid="quick-add-advanced-panel" class="space-y-3.5 border-t border-border px-3.5 py-3">
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div class="space-y-1">
              <label for="fo-quick-add-account" class="block text-xs font-medium text-muted">
                {{ isInstallmentMode() ? 'Tarjeta de crédito' : 'Cuenta' }}
              </label>

              @if (isInstallmentMode() && creditCards().length === 0) {
                <div
                  role="status"
                  data-testid="quick-add-no-cards-notice"
                  class="rounded-xl border border-warning/30 bg-warning/10 px-3 py-2 text-xs text-text"
                >
                  <p>Necesitás una tarjeta de crédito para cargar cuotas.</p>
                  <a
                    routerLink="/accounts"
                    data-testid="quick-add-no-cards-link"
                    (click)="navigateToAccounts.emit()"
                    class="fo-focus-ring mt-1 inline-flex items-center gap-1 font-semibold text-accent underline underline-offset-2"
                  >
                    Ir a Cuentas
                  </a>
                </div>
              } @else if (!isInstallmentMode() && usableAccounts().length === 0) {
                <div
                  role="status"
                  class="rounded-xl border border-warning/30 bg-warning/10 px-3 py-2 text-xs text-text"
                >
                  <p>No tenés cuentas activas en {{ currencyCode() }}.</p>
                  <a
                    routerLink="/accounts"
                    (click)="navigateToAccounts.emit()"
                    class="fo-focus-ring mt-1 inline-flex items-center gap-1 font-semibold text-accent underline underline-offset-2"
                  >
                    Ir a Cuentas
                  </a>
                </div>
              } @else {
                <select
                  id="fo-quick-add-account"
                  data-testid="quick-add-account-select"
                  [value]="selectedAccountId() ?? ''"
                  (change)="onAccountChange($event)"
                  class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text"
                >
                  @for (account of activeAccountOptions(); track account.id) {
                    <option [value]="account.id">
                      {{ account.name }} ({{ account.currencyCode }})
                    </option>
                  }
                </select>
              }
            </div>

            <div class="space-y-1">
              <label for="fo-quick-add-date" class="block text-xs font-medium text-muted">
                Fecha
              </label>
              <input
                id="fo-quick-add-date"
                data-testid="quick-add-date-input"
                type="date"
                [value]="transactionDate()"
                (change)="onDateChange($event)"
                class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text tabular-nums"
              />
            </div>
          </div>

          @if (isAdult() && activeMembers().length > 0) {
            <div data-testid="quick-add-attribution-section" class="space-y-1.5">
              <span class="block text-xs font-medium text-muted">Para:</span>
              <div class="flex flex-wrap gap-1.5" role="group" aria-label="Para integrante">
                @for (member of activeMembers(); track member.id) {
                  @let selected = isMemberSelected(member.id);
                  <button
                    type="button"
                    [attr.data-testid]="'attribution-member-' + member.id"
                    [attr.aria-pressed]="selected"
                    (click)="selectMember(member.id)"
                    class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors"
                    [class.border-accent]="selected"
                    [class.bg-accent-soft]="selected"
                    [class.text-accent]="selected"
                    [class.border-border]="!selected"
                    [class.bg-surface]="!selected"
                    [class.text-text]="!selected"
                    [class.hover:border-accent]="!selected"
                  >
                    <span
                      aria-hidden="true"
                      class="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-bold"
                      [style.background-color]="member.accentColor || 'var(--fo-accent)'"
                      [style.color]="avatarTextColor(member.accentColor)"
                    >
                      {{ memberInitials(member.fullName) }}
                    </span>
                    <span class="truncate">
                      {{ member.fullName }}
                      @if (member.id === currentUserId()) {
                        <span class="text-muted">(vos)</span>
                      }
                    </span>
                  </button>
                }
              </div>
            </div>
          }

          @if (transactionType() === 'EXPENSE') {
            <div data-testid="quick-add-installments-section" class="space-y-3 border-t border-border/70 pt-3">
              <div class="flex items-center justify-between gap-3">
                <span class="text-xs font-medium text-muted">Cuotas</span>

                <div class="inline-flex items-center gap-2">
                  <button
                    type="button"
                    data-testid="installments-decrement"
                    aria-label="Restar cuota"
                    [disabled]="installments() <= 1"
                    (click)="stepInstallments(-1)"
                    class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg border border-border bg-surface text-base font-semibold text-text transition-colors hover:bg-surface-2 disabled:pointer-events-none disabled:opacity-40"
                  >
                    −
                  </button>

                  <span
                    data-testid="installments-count"
                    class="min-w-20 text-center text-sm font-semibold text-text tabular-nums"
                  >
                    {{ installments() === 1 ? '1 pago' : installments() + ' cuotas' }}
                  </span>

                  <button
                    type="button"
                    data-testid="installments-increment"
                    aria-label="Sumar cuota"
                    [disabled]="installments() >= 24"
                    (click)="stepInstallments(1)"
                    class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg border border-border bg-surface text-base font-semibold text-text transition-colors hover:bg-surface-2 disabled:pointer-events-none disabled:opacity-40"
                  >
                    +
                  </button>
                </div>
              </div>

              @if (isInstallmentMode()) {
                <div class="space-y-3">
                  <div class="space-y-1.5">
                    <span class="block text-xs font-medium text-muted">
                      El monto ingresado…
                    </span>
                    <div
                      role="radiogroup"
                      aria-label="Modo del monto en cuotas"
                      class="grid grid-cols-2 gap-1.5 rounded-full border border-border bg-surface p-1"
                    >
                      @for (option of amountModeOptions; track option.value; let i = $index) {
                        @let selected = amountMode() === option.value;
                        <button
                          type="button"
                          role="radio"
                          [attr.data-testid]="'amount-mode-' + option.value"
                          [attr.aria-checked]="selected"
                          [attr.tabindex]="selected || (amountMode() === null && i === 0) ? 0 : -1"
                          (click)="amountModeChange.emit(option.value)"
                          (keydown)="onAmountModeKeydown($event, i)"
                          class="fo-focus-ring cursor-pointer rounded-full px-3 py-1.5 text-xs font-medium transition-colors"
                          [class.bg-accent]="selected"
                          [class.text-accent-contrast]="selected"
                          [class.text-muted]="!selected"
                          [class.hover:text-text]="!selected"
                        >
                          {{ option.label }}
                        </button>
                      }
                    </div>
                    @if (amountMode() === null) {
                      <p class="text-[11px] text-warning">
                        Elegí si el monto es el total o cada cuota para poder guardar.
                      </p>
                    }
                  </div>

                  <div>
                    <fo-segmented-toggle
                      [options]="interestOptions"
                      [value]="interestChoice()"
                      (valueChange)="onInterestChange($event)"
                      ariaLabel="Interés en cuotas"
                      size="sm"
                    />
                  </div>

                  @if (hasInterest()) {
                    <div class="space-y-1">
                      <label for="fo-quick-add-cash-price" class="block text-xs font-medium text-muted">
                        Precio contado (opcional)
                      </label>
                      <input
                        id="fo-quick-add-cash-price"
                        data-testid="quick-add-cash-price-input"
                        type="text"
                        inputmode="decimal"
                        autocomplete="off"
                        placeholder="Ej. 180000"
                        [value]="cashPrice()"
                        (input)="onCashPriceInput($event)"
                        class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm text-text tabular-nums placeholder:text-muted"
                      />
                    </div>
                  }

                  <div
                    data-testid="installment-preview"
                    aria-live="polite"
                    aria-atomic="true"
                    class="rounded-xl border border-border bg-surface px-3 py-2 text-xs font-medium text-text tabular-nums"
                  >
                    @if (installmentPreviewText(); as preview) {
                      {{ preview }}
                    } @else {
                      <span class="font-normal text-muted">
                        Ingresá el monto y elegí el modo para ver el detalle de las cuotas.
                      </span>
                    }
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuickAddAdvancedComponent {
  readonly expanded = input<boolean>(false);
  readonly transactionType = input.required<TransactionType>();
  readonly currencyCode = input.required<string>();
  readonly usableAccounts = input.required<readonly Account[]>();
  readonly creditCards = input.required<readonly Account[]>();
  readonly selectedAccountId = input<string | null>(null);
  readonly transactionDate = input.required<string>();
  readonly isAdult = input<boolean>(false);
  readonly currentUserId = input<string | null>(null);
  readonly members = input<readonly QuickAddFamilyMember[]>([]);
  readonly attributedToUserId = input<string | null>(null);
  readonly installments = input<number>(1);
  readonly amountMode = input<InstallmentAmountMode | null>(null);
  readonly hasInterest = input<boolean>(false);
  readonly cashPrice = input<string>('');
  readonly installmentPreviewText = input<string | null>(null);
  readonly summaryHint = input<string>('');

  readonly expandedChange = output<boolean>();
  readonly accountIdChange = output<string>();
  readonly transactionDateChange = output<string>();
  readonly attributedToUserIdChange = output<string | null>();
  readonly installmentsChange = output<number>();
  readonly amountModeChange = output<InstallmentAmountMode>();
  readonly hasInterestChange = output<boolean>();
  readonly cashPriceChange = output<string>();
  readonly navigateToAccounts = output<void>();

  protected readonly interestOptions = INTEREST_OPTIONS;
  protected readonly amountModeOptions = AMOUNT_MODE_OPTIONS;

  protected readonly isInstallmentMode = computed(
    () => this.transactionType() === 'EXPENSE' && this.installments() > 1,
  );

  protected readonly activeAccountOptions = computed(() =>
    this.isInstallmentMode() ? this.creditCards() : this.usableAccounts(),
  );

  protected readonly activeMembers = computed(() =>
    this.members().filter((member) => member.isActive),
  );

  protected readonly interestChoice = computed<InterestChoice>(() =>
    this.hasInterest() ? 'WITH_INTEREST' : 'NO_INTEREST',
  );

  protected toggleExpanded(): void {
    this.expandedChange.emit(!this.expanded());
  }

  protected onAccountChange(event: Event): void {
    const target = event.target as HTMLSelectElement | null;
    if (target?.value) {
      this.accountIdChange.emit(target.value);
    }
  }

  protected onDateChange(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    if (target?.value) {
      this.transactionDateChange.emit(target.value);
    }
  }

  protected isMemberSelected(memberId: string): boolean {
    const selected = this.attributedToUserId();
    if (selected) {
      return selected === memberId;
    }
    return this.currentUserId() === memberId;
  }

  protected selectMember(memberId: string): void {
    if (memberId === this.currentUserId()) {
      this.attributedToUserIdChange.emit(null);
      return;
    }
    this.attributedToUserIdChange.emit(memberId);
  }

  protected stepInstallments(delta: -1 | 1): void {
    const next = Math.max(1, Math.min(24, this.installments() + delta));
    if (next !== this.installments()) {
      this.installmentsChange.emit(next);
    }
  }

  protected onAmountModeKeydown(event: KeyboardEvent, currentIndex: number): void {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') {
      return;
    }
    event.preventDefault();
    const nextIndex = (currentIndex + 1) % this.amountModeOptions.length;
    this.amountModeChange.emit(this.amountModeOptions[nextIndex].value);
    const parent = (event.currentTarget as HTMLElement | null)?.parentElement;
    const buttons = parent?.querySelectorAll<HTMLButtonElement>('button[role="radio"]');
    buttons?.[nextIndex]?.focus();
  }

  protected onInterestChange(choice: InterestChoice): void {
    this.hasInterestChange.emit(choice === 'WITH_INTEREST');
  }

  protected onCashPriceInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    if (!target) {
      return;
    }
    const normalized = normalizeDecimalInput(target.value);
    if (target.value.replace(/,/g, '.') !== normalized) {
      target.value = normalized;
    }
    this.cashPriceChange.emit(normalized);
  }

  protected memberInitials(fullName: string): string {
    return initialsFor(fullName);
  }

  protected avatarTextColor(accentColor: string | null | undefined): string {
    return readableTextOn(accentColor || '#0F766E');
  }
}
