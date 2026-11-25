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
import { IconComponent } from '../../../shared/ui';
import { normalizeDecimalInput } from '../amount-input';
import { QuickAddFamilyMember } from '../data-access/quick-add-members.api';
import { InstallmentSummaryLines } from '../installment-preview';

type InterestChoice = 'NO_INTEREST' | 'WITH_INTEREST';

const INTEREST_OPTIONS: readonly { value: InterestChoice; label: string }[] = [
  { value: 'NO_INTEREST', label: 'Sin interés' },
  { value: 'WITH_INTEREST', label: 'Con interés' },
];

const AMOUNT_MODE_OPTIONS: readonly { value: InstallmentAmountMode; label: string }[] = [
  { value: 'TOTAL', label: 'Es el total' },
  { value: 'PER_INSTALLMENT', label: 'Es cada cuota' },
];

@Component({
  selector: 'fo-quick-add-advanced',
  standalone: true,
  imports: [IconComponent, RouterLink],
  template: `
    <div class="flex flex-col gap-3">
      <button
        type="button"
        data-testid="quick-add-more-options-btn"
        [attr.aria-expanded]="expanded()"
        (click)="toggleExpanded()"
        class="more-toggle fo-focus-ring"
        [class.is-prefilled]="datePrefilled() && !expanded()"
      >
        <span>{{ toggleLabel() }}</span>
        <fo-icon
          name="chevron-down"
          [size]="12"
          class="transition-transform duration-150"
          [class.rotate-180]="expanded()"
        />
      </button>

      @if (expanded()) {
        <div data-testid="quick-add-advanced-panel" class="more-panel">
          <div class="more-row">
            <label for="fo-quick-add-account" class="more-label">
              {{ isInstallmentMode() ? 'Tarjeta' : 'Cuenta' }}
            </label>
            @if (isInstallmentMode() && creditCards().length === 0) {
              <div role="status" data-testid="quick-add-no-cards-notice" class="more-notice">
                <span>Necesitás una tarjeta de crédito para cargar cuotas.</span>
                <a
                  routerLink="/accounts"
                  data-testid="quick-add-no-cards-link"
                  (click)="navigateToAccounts.emit()"
                  class="fo-focus-ring font-semibold text-accent underline underline-offset-2"
                >
                  Ir a Cuentas
                </a>
              </div>
            } @else if (!isInstallmentMode() && usableAccounts().length === 0) {
              <div role="status" class="more-notice">
                <span>No tenés cuentas activas en {{ currencyCode() }}.</span>
                <a
                  routerLink="/accounts"
                  (click)="navigateToAccounts.emit()"
                  class="fo-focus-ring font-semibold text-accent underline underline-offset-2"
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
                class="fo-mini-chip is-active fo-focus-ring mini-field"
              >
                @for (account of activeAccountOptions(); track account.id) {
                  <option [value]="account.id">
                    {{ account.name }} ({{ account.currencyCode }})
                  </option>
                }
              </select>
            }
          </div>

          <div class="more-row">
            <label for="fo-quick-add-date" class="more-label">Fecha</label>
            <input
              id="fo-quick-add-date"
              data-testid="quick-add-date-input"
              [attr.data-prefilled]="datePrefilled() ? 'true' : null"
              type="date"
              [value]="transactionDate()"
              (change)="onDateChange($event)"
              class="fo-mini-chip fo-focus-ring mini-field tabular-nums"
              [class.is-active]="datePrefilled()"
            />
          </div>

          @if (isAdult() && activeMembers().length > 0) {
            <div data-testid="quick-add-attribution-section" class="more-row">
              <span class="more-label">Para:</span>
              <div class="more-chips" role="group" aria-label="Para integrante">
                @for (member of activeMembers(); track member.id) {
                  <button
                    type="button"
                    [attr.data-testid]="'attribution-member-' + member.id"
                    [attr.aria-pressed]="isMemberSelected(member.id)"
                    [attr.aria-label]="member.fullName"
                    (click)="selectMember(member.id)"
                    class="fo-mini-chip fo-focus-ring"
                    [class.is-active]="isMemberSelected(member.id)"
                  >
                    {{ memberLabel(member) }}
                  </button>
                }
              </div>
            </div>
          }

          @if (transactionType() === 'EXPENSE') {
            <div data-testid="quick-add-installments-section" class="flex flex-col gap-2.5">
              <div class="more-row">
                <span class="more-label">Cuotas</span>
                <div class="more-chips items-center">
                  <button
                    type="button"
                    data-testid="installments-decrement"
                    aria-label="Restar cuota"
                    [disabled]="installments() <= 1"
                    (click)="stepInstallments(-1)"
                    class="fo-mini-chip fo-focus-ring disabled:opacity-40"
                  >
                    −
                  </button>
                  <span data-testid="installments-count" class="fo-mini-chip is-active tabular-nums">
                    {{ installments() === 1 ? '1 pago' : installments() + ' cuotas' }}
                  </span>
                  <button
                    type="button"
                    data-testid="installments-increment"
                    aria-label="Sumar cuota"
                    [disabled]="installments() >= 24"
                    (click)="stepInstallments(1)"
                    class="fo-mini-chip fo-focus-ring disabled:opacity-40"
                  >
                    +
                  </button>
                </div>
              </div>

              @if (isInstallmentMode()) {
                <div class="flex flex-col gap-1">
                  <span class="field-lbl">¿Qué es ese monto?</span>
                  <div role="radiogroup" aria-label="Modo del monto en cuotas" class="seg">
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
                        class="fo-focus-ring"
                        [class.is-active]="selected"
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

                <div class="flex flex-col gap-1">
                  <span class="field-lbl">Financiación</span>
                  <div role="radiogroup" aria-label="Interés en cuotas" class="seg">
                    @for (option of interestOptions; track option.value) {
                      @let selected = interestChoice() === option.value;
                      <button
                        type="button"
                        role="radio"
                        [attr.data-testid]="'interest-' + option.value"
                        [attr.aria-checked]="selected"
                        (click)="onInterestChange(option.value)"
                        class="fo-focus-ring"
                        [class.is-active]="selected"
                      >
                        {{ option.label }}
                      </button>
                    }
                  </div>
                </div>

                @if (hasInterest()) {
                  <div class="opt-box">
                    <label for="fo-quick-add-cash-price" class="field-lbl !mb-0">
                      <span>Precio de contado (opcional)</span>
                      <span class="font-medium">Para calcular recargo</span>
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
                      class="small-input tabular-nums"
                    />
                  </div>
                }

                <div
                  data-testid="installment-preview"
                  aria-live="polite"
                  aria-atomic="true"
                  class="live-summary tabular-nums"
                >
                  @if (installmentSummary(); as summary) {
                    <strong class="sum-main">{{ summary.main }}</strong>
                    <span class="sum-sub">
                      <span>Total a pagar: <strong>{{ summary.total }}</strong></span>
                      <span>{{ summary.range }}</span>
                    </span>
                    <span class="sum-tag">{{ summary.tag }}</span>
                  } @else {
                    <span class="text-muted">
                      Ingresá el monto y elegí el modo para ver el detalle de las cuotas.
                    </span>
                  }
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .more-toggle {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      align-self: center;
      gap: 4px;
      max-width: 100%;
      padding: 2px 0;
      border: none;
      border-radius: 6px;
      background: transparent;
      color: var(--fo-text-muted);
      font-family: inherit;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
    }
    .more-toggle:hover,
    .more-toggle.is-prefilled {
      color: var(--fo-text);
    }
    .more-panel {
      display: flex;
      flex-direction: column;
      gap: 9px;
      padding: 10px;
      border: 1px solid var(--fo-border);
      border-radius: 13px;
      background: var(--fo-bg);
      animation: fo-fade-slide-up 0.22s var(--fo-ease-smooth);
    }
    .more-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
      font-size: 11px;
    }
    .more-label {
      flex-shrink: 0;
      color: var(--fo-text-muted);
    }
    .more-chips {
      display: inline-flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 4px;
    }
    .more-notice {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 2px;
      color: var(--fo-text-2);
      font-size: 10.5px;
      text-align: right;
    }
    .mini-field {
      max-width: 62%;
      cursor: pointer;
    }
    .field-lbl {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1px;
      color: var(--fo-text-muted);
      font-size: 11px;
      font-weight: 600;
    }
    .seg {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 3px;
      padding: 3px;
      border: 1px solid var(--fo-border);
      border-radius: 11px;
      background: var(--fo-surface-2);
    }
    .seg button {
      padding: 7px 6px;
      border: none;
      border-radius: 8px;
      background: transparent;
      color: var(--fo-text-muted);
      font-family: inherit;
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.14s ease;
    }
    .seg button.is-active {
      background: var(--fo-surface);
      color: var(--fo-text);
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
    }
    .opt-box {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 10px 12px;
      border: 1px solid var(--fo-border);
      border-radius: 12px;
      background: var(--fo-surface);
    }
    .small-input {
      width: 100%;
      padding: 8px 10px;
      border: 1px solid var(--fo-border);
      border-radius: 9px;
      outline: none;
      background: var(--fo-bg);
      color: var(--fo-text);
      font-family: inherit;
      font-size: 14px;
      font-weight: 600;
    }
    .small-input:focus {
      border-color: var(--fo-accent);
    }
    .live-summary {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px 10px;
      border: 1px solid var(--fo-accent);
      border-radius: 10px;
      background: var(--fo-accent-soft);
      font-size: 11px;
    }
    .sum-main {
      color: var(--fo-text);
      font-size: 12px;
      font-weight: 700;
    }
    .sum-sub {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 6px;
      color: var(--fo-text-2);
    }
    .sum-tag {
      color: var(--fo-accent);
      font-weight: 700;
    }
    @media (prefers-reduced-motion: reduce) {
      .more-panel {
        animation: none;
      }
    }
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
  readonly installmentSummary = input<InstallmentSummaryLines | null>(null);
  readonly summaryHint = input<string>('');
  readonly datePrefilled = input<boolean>(false);

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

  /** Mock `more-toggle`: `Más opciones (Mercado Pago · Hoy)` / `Ocultar opciones`. */
  protected readonly toggleLabel = computed(() => {
    if (this.expanded()) {
      return 'Ocultar opciones';
    }
    const hint = this.summaryHint();
    return hint ? `Más opciones (${hint})` : 'Más opciones';
  });

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

  /** Mock chips: `Yo` for the current user, first name for the rest. */
  protected memberLabel(member: QuickAddFamilyMember): string {
    if (member.id === this.currentUserId()) {
      return 'Yo';
    }
    return member.fullName.trim().split(/\s+/)[0] || member.fullName;
  }
}
