import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  output,
  signal,
} from '@angular/core';
import { Account } from '../../../../core/finance/finance.models';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import {
  ButtonComponent,
  IconComponent,
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../../shared/ui';
import { ExchangeCreate, ExchangeMode, IndexValue } from '../data-access/exchanges.models';
import {
  ExchangeCalculationState,
  ExchangeField,
  buildReferenceLine,
  createInitialCalculationState,
  formatDecimalForApi,
  formatEditableNumber,
  formatRateExpression,
  isRateWithinTolerance,
  parseDecimalInput,
  pickReferenceIndex,
  recalculateExchange,
} from '../utils/exchange-math';

const MODE_OPTIONS: readonly SegmentedOption<ExchangeMode>[] = [
  { value: 'BUY', label: 'Comprar' },
  { value: 'SELL', label: 'Vender' },
];

function todayIsoDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function normalizeCurrency(code: string): string {
  return code.trim().toUpperCase();
}

@Component({
  selector: 'fo-exchange-form',
  standalone: true,
  imports: [ButtonComponent, IconComponent, MoneyPipe, SegmentedToggleComponent],
  template: `
    <form (submit)="onSubmit($event)" class="space-y-4" novalidate>
      @if (errorMessage(); as error) {
        <div
          role="alert"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger"
        >
          {{ error }}
        </div>
      }

      @if (validationError(); as validationMsg) {
        <div
          role="alert"
          data-testid="exchange-form-validation-error"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger"
        >
          {{ validationMsg }}
        </div>
      }

      <fo-segmented-toggle
        [options]="modeOptions"
        [value]="mode()"
        (valueChange)="onModeChange($event)"
        ariaLabel="Tipo de operación"
      />

      @if (missingRequiredAccounts()) {
        <div
          role="status"
          data-testid="exchange-missing-accounts"
          class="rounded-xl border border-warning/30 bg-warning/10 px-3.5 py-3 text-sm text-text"
        >
          <p class="font-medium">Faltan cuentas para operar</p>
          <p class="mt-0.5 text-xs text-muted">
            Necesitás al menos una cuenta activa en {{ normalizedBaseCurrency() }} y otra en una
            divisa extranjera para registrar una compra o venta.
          </p>
        </div>
      } @else {
        @if (foreignCurrencies().length > 1) {
          <fieldset class="space-y-1.5">
            <legend class="text-xs font-medium text-muted">Divisa extranjera</legend>
            <div class="flex flex-wrap gap-2" role="group" aria-label="Divisa extranjera">
              @for (code of foreignCurrencies(); track code) {
                @let selected = foreignCurrency() === code;
                <button
                  type="button"
                  [attr.aria-pressed]="selected"
                  (click)="onForeignCurrencyChange(code)"
                  class="fo-focus-ring cursor-pointer rounded-full border px-3 py-1 text-xs font-semibold transition-colors"
                  [class.border-accent]="selected"
                  [class.bg-accent-soft]="selected"
                  [class.text-accent]="selected"
                  [class.border-border]="!selected"
                  [class.bg-surface]="!selected"
                  [class.text-text]="!selected"
                >
                  {{ code }}
                </button>
              }
            </div>
          </fieldset>
        }

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <label for="fo-exchange-source-account" class="block text-sm font-medium text-text">
              Cuenta origen ({{ sourceCurrency() }})
            </label>
            <select
              id="fo-exchange-source-account"
              data-testid="exchange-source-account"
              [value]="sourceAccountId()"
              (change)="onSourceAccountChange($event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
            >
              @for (acc of sourceAccounts(); track acc.id) {
                <option [value]="acc.id">
                  {{ acc.name }} ({{ acc.currentBalance | money: acc.currencyCode }})
                </option>
              }
            </select>
          </div>

          <div class="space-y-1.5">
            <label for="fo-exchange-target-account" class="block text-sm font-medium text-text">
              Cuenta destino ({{ targetCurrency() }})
            </label>
            <select
              id="fo-exchange-target-account"
              data-testid="exchange-target-account"
              [value]="targetAccountId()"
              (change)="onTargetAccountChange($event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
            >
              @for (acc of targetAccounts(); track acc.id) {
                <option [value]="acc.id">
                  {{ acc.name }} ({{ acc.currentBalance | money: acc.currencyCode }})
                </option>
              }
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <div class="flex items-center justify-between gap-2">
              <label for="fo-exchange-source-amount" class="block text-sm font-medium text-text">
                Pago ({{ sourceCurrency() }})
              </label>
              @if (calcState().derivedField === 'source') {
                <span class="text-[11px] font-medium text-accent">Calculado</span>
              }
            </div>
            <input
              id="fo-exchange-source-amount"
              data-testid="exchange-source-amount"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="0,00"
              [value]="sourceInput()"
              (input)="onAmountFieldInput('source', $event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums placeholder:text-muted"
            />
          </div>

          <div class="space-y-1.5">
            <div class="flex items-center justify-between gap-2">
              <label for="fo-exchange-target-amount" class="block text-sm font-medium text-text">
                Recibo ({{ targetCurrency() }})
              </label>
              @if (calcState().derivedField === 'target') {
                <span class="text-[11px] font-medium text-accent">Calculado</span>
              }
            </div>
            <input
              id="fo-exchange-target-amount"
              data-testid="exchange-target-amount"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="0,00"
              [value]="targetInput()"
              (input)="onAmountFieldInput('target', $event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums placeholder:text-muted"
            />
          </div>
        </div>

        <div class="space-y-2 rounded-xl border border-border bg-surface-2/70 p-3.5">
          <div class="space-y-1.5">
            <div class="flex items-center justify-between gap-2">
              <label for="fo-exchange-rate" class="block text-xs font-medium text-muted">
                Tipo de cambio ({{ normalizedBaseCurrency() }} por 1 {{ foreignCurrency() }})
              </label>
              @if (calcState().derivedField === 'rate') {
                <span class="text-[11px] font-medium text-accent">Calculado</span>
              }
            </div>
            <input
              id="fo-exchange-rate"
              data-testid="exchange-rate-input"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="Ej. 1185,00"
              [value]="rateInput()"
              (input)="onAmountFieldInput('rate', $event)"
              class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2 text-sm font-medium text-text tabular-nums placeholder:text-muted"
            />
          </div>

          @if (rateExpression(); as expression) {
            <p
              data-testid="exchange-live-rate"
              class="text-sm font-semibold text-text tabular-nums"
            >
              {{ expression }}
            </p>
          }

          @if (referenceLine(); as refLine) {
            <p
              data-testid="exchange-reference-comparison"
              class="flex items-center gap-1.5 text-xs font-medium tabular-nums"
              [class.text-success]="refLine.tone === 'positive'"
              [class.text-danger]="refLine.tone === 'negative'"
              [class.text-muted]="refLine.tone === 'neutral'"
            >
              <fo-icon name="trending-up" [size]="14" />
              <span>{{ refLine.text }}</span>
            </p>
          }
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div class="space-y-1.5 sm:col-span-2">
            <label for="fo-exchange-fee" class="block text-sm font-medium text-text">
              Comisión <span class="font-normal text-muted">(opcional)</span>
            </label>
            <input
              id="fo-exchange-fee"
              data-testid="exchange-fee-amount"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              placeholder="0,00"
              [value]="feeInput()"
              (input)="onFeeInput($event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums placeholder:text-muted"
            />
          </div>

          <div class="space-y-1.5">
            <label for="fo-exchange-fee-currency" class="block text-sm font-medium text-text">
              Divisa
            </label>
            <select
              id="fo-exchange-fee-currency"
              data-testid="exchange-fee-currency"
              [value]="feeCurrency()"
              (change)="onFeeCurrencyChange($event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent"
            >
              @for (code of feeCurrencyOptions(); track code) {
                <option [value]="code">{{ code }}</option>
              }
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="space-y-1.5">
            <label for="fo-exchange-date" class="block text-sm font-medium text-text">
              Fecha
            </label>
            <input
              id="fo-exchange-date"
              data-testid="exchange-date"
              type="date"
              [value]="exchangeDate()"
              (input)="onDateInput($event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent tabular-nums"
            />
          </div>

          <div class="space-y-1.5">
            <label for="fo-exchange-notes" class="block text-sm font-medium text-text">
              Nota <span class="font-normal text-muted">(opcional)</span>
            </label>
            <input
              id="fo-exchange-notes"
              data-testid="exchange-notes"
              type="text"
              maxlength="255"
              placeholder="Ej. Compra MEP en broker"
              [value]="notes()"
              (input)="onNotesInput($event)"
              class="fo-focus-ring w-full rounded-[10px] border border-border bg-bg px-3 py-[9px] text-[13px] text-text focus:border-accent placeholder:text-muted"
            />
          </div>
        </div>
      }

      <div class="flex items-center justify-end gap-2 pt-2">
        <fo-button
          variant="ghost"
          type="button"
          [disabled]="saving()"
          (click)="cancelled.emit()"
        >
          Cancelar
        </fo-button>
        <fo-button
          variant="primary"
          type="submit"
          data-testid="exchange-submit-btn"
          [loading]="saving()"
          [disabled]="missingRequiredAccounts()"
        >
          Guardar operación
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExchangeFormComponent {
  readonly accounts = input.required<readonly Account[]>();
  readonly baseCurrency = input<string>('ARS');
  readonly referenceCurrency = input<string>('USD');
  readonly preferredRateSource = input<string>('USD_MEP');
  readonly latestIndices = input<readonly IndexValue[]>([]);
  readonly defaultAccountIds = input<Readonly<Record<string, string>>>({});
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly submitted = output<ExchangeCreate>();
  readonly cancelled = output<void>();

  protected readonly modeOptions = MODE_OPTIONS;
  protected readonly mode = signal<ExchangeMode>('BUY');
  protected readonly selectedForeignCurrency = signal<string | null>(null);
  protected readonly sourceAccountId = signal<string>('');
  protected readonly targetAccountId = signal<string>('');

  protected readonly calcState = signal<ExchangeCalculationState>(
    createInitialCalculationState('BUY'),
  );
  protected readonly sourceInput = signal<string>('');
  protected readonly targetInput = signal<string>('');
  protected readonly rateInput = signal<string>('');

  protected readonly feeInput = signal<string>('');
  protected readonly feeCurrency = signal<string>('ARS');
  protected readonly exchangeDate = signal<string>(todayIsoDate());
  protected readonly notes = signal<string>('');
  protected readonly validationError = signal<string | null>(null);

  protected readonly normalizedBaseCurrency = computed(() =>
    normalizeCurrency(this.baseCurrency() || 'ARS'),
  );

  protected readonly baseAccounts = computed(() => {
    const base = this.normalizedBaseCurrency();
    return this.accounts().filter(
      (account) => account.isActive && normalizeCurrency(account.currencyCode) === base,
    );
  });

  protected readonly foreignCurrencies = computed<string[]>(() => {
    const base = this.normalizedBaseCurrency();
    const ref = normalizeCurrency(this.referenceCurrency() || 'USD');
    const seen = new Set<string>();
    for (const account of this.accounts()) {
      if (!account.isActive) {
        continue;
      }
      const code = normalizeCurrency(account.currencyCode);
      if (code !== base) {
        seen.add(code);
      }
    }
    return [...seen].sort((a, b) => {
      if (a === ref && b !== ref) {
        return -1;
      }
      if (b === ref && a !== ref) {
        return 1;
      }
      return a.localeCompare(b);
    });
  });

  protected readonly foreignCurrency = computed<string>(() => {
    const available = this.foreignCurrencies();
    const selected = this.selectedForeignCurrency();
    if (selected && available.includes(selected)) {
      return selected;
    }
    return available[0] ?? normalizeCurrency(this.referenceCurrency() || 'USD');
  });

  protected readonly foreignAccounts = computed(() => {
    const targetCode = this.foreignCurrency();
    return this.accounts().filter(
      (account) => account.isActive && normalizeCurrency(account.currencyCode) === targetCode,
    );
  });

  protected readonly missingRequiredAccounts = computed(
    () => this.baseAccounts().length === 0 || this.foreignCurrencies().length === 0,
  );

  protected readonly sourceCurrency = computed(() =>
    this.mode() === 'BUY' ? this.normalizedBaseCurrency() : this.foreignCurrency(),
  );

  protected readonly targetCurrency = computed(() =>
    this.mode() === 'BUY' ? this.foreignCurrency() : this.normalizedBaseCurrency(),
  );

  protected readonly sourceAccounts = computed(() =>
    this.mode() === 'BUY' ? this.baseAccounts() : this.foreignAccounts(),
  );

  protected readonly targetAccounts = computed(() =>
    this.mode() === 'BUY' ? this.foreignAccounts() : this.baseAccounts(),
  );

  protected readonly feeCurrencyOptions = computed(() => {
    const src = this.sourceCurrency();
    const tgt = this.targetCurrency();
    return src === tgt ? [src] : [src, tgt];
  });

  protected readonly rateExpression = computed<string | null>(() => {
    const rate = this.calcState().rate;
    if (rate === null || rate <= 0) {
      return null;
    }
    return formatRateExpression(this.foreignCurrency(), this.normalizedBaseCurrency(), rate);
  });

  protected readonly referenceIndex = computed<IndexValue | null>(() =>
    pickReferenceIndex(
      this.latestIndices(),
      this.foreignCurrency(),
      this.preferredRateSource(),
    ),
  );

  protected readonly referenceLine = computed(() =>
    buildReferenceLine(
      this.referenceIndex(),
      this.normalizedBaseCurrency(),
      this.calcState().rate,
      this.mode(),
    ),
  );

  constructor() {
    effect(() => {
      const sources = this.sourceAccounts();
      const current = this.sourceAccountId();
      if (!sources.some((acc) => acc.id === current)) {
        this.sourceAccountId.set(this.pickDefaultAccountId(sources, this.sourceCurrency()));
      }
    });

    effect(() => {
      const targets = this.targetAccounts();
      const current = this.targetAccountId();
      if (!targets.some((acc) => acc.id === current)) {
        this.targetAccountId.set(this.pickDefaultAccountId(targets, this.targetCurrency()));
      }
    });

    effect(() => {
      const allowed = this.feeCurrencyOptions();
      if (!allowed.includes(this.feeCurrency())) {
        this.feeCurrency.set(allowed[0] ?? this.sourceCurrency());
      }
    });
  }

  protected onModeChange(nextMode: ExchangeMode): void {
    if (this.mode() === nextMode) {
      return;
    }
    this.mode.set(nextMode);
    this.validationError.set(null);
    this.resetCalculation(nextMode);
    this.feeCurrency.set(
      nextMode === 'BUY' ? this.normalizedBaseCurrency() : this.foreignCurrency(),
    );
  }

  protected onForeignCurrencyChange(currencyCode: string): void {
    if (this.foreignCurrency() === currencyCode) {
      return;
    }
    this.selectedForeignCurrency.set(currencyCode);
    this.validationError.set(null);
    this.resetCalculation(this.mode());
  }

  protected onSourceAccountChange(event: Event): void {
    this.sourceAccountId.set((event.target as HTMLSelectElement).value);
    this.validationError.set(null);
  }

  protected onTargetAccountChange(event: Event): void {
    this.targetAccountId.set((event.target as HTMLSelectElement).value);
    this.validationError.set(null);
  }

  protected onAmountFieldInput(field: ExchangeField, event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.validationError.set(null);

    if (field === 'source') {
      this.sourceInput.set(raw);
    } else if (field === 'target') {
      this.targetInput.set(raw);
    } else {
      this.rateInput.set(raw);
    }

    const parsed = parseDecimalInput(raw);
    const prevState = this.calcState();
    const nextState = recalculateExchange(prevState, field, parsed);
    this.calcState.set(nextState);

    if (field !== 'source' && nextState.sourceAmount !== prevState.sourceAmount) {
      this.sourceInput.set(formatEditableNumber(nextState.sourceAmount, 4));
    }
    if (field !== 'target' && nextState.targetAmount !== prevState.targetAmount) {
      this.targetInput.set(formatEditableNumber(nextState.targetAmount, 4));
    }
    if (field !== 'rate' && nextState.rate !== prevState.rate) {
      this.rateInput.set(formatEditableNumber(nextState.rate, 6));
    }
  }

  protected onFeeInput(event: Event): void {
    this.feeInput.set((event.target as HTMLInputElement).value);
    this.validationError.set(null);
  }

  protected onFeeCurrencyChange(event: Event): void {
    this.feeCurrency.set((event.target as HTMLSelectElement).value);
  }

  protected onDateInput(event: Event): void {
    this.exchangeDate.set((event.target as HTMLInputElement).value);
    this.validationError.set(null);
  }

  protected onNotesInput(event: Event): void {
    this.notes.set((event.target as HTMLInputElement).value);
  }

  protected onSubmit(event: Event): void {
    event.preventDefault();
    this.validationError.set(null);

    if (this.missingRequiredAccounts()) {
      return;
    }

    const srcId = this.sourceAccountId();
    const tgtId = this.targetAccountId();
    if (!srcId || !tgtId) {
      this.validationError.set('Seleccioná la cuenta de origen y la cuenta de destino.');
      return;
    }

    const state = this.calcState();
    if (!state.sourceAmount || state.sourceAmount <= 0 || !state.targetAmount || state.targetAmount <= 0) {
      this.validationError.set('Ingresá montos válidos mayores a cero para registrar la operación.');
      return;
    }

    const rawFee = this.feeInput().trim();
    let parsedFee: number | null = null;
    if (rawFee !== '') {
      parsedFee = parseDecimalInput(rawFee);
      if (parsedFee === null || parsedFee < 0) {
        this.validationError.set('La comisión debe ser un número válido mayor o igual a cero.');
        return;
      }
    }

    const payload: ExchangeCreate = {
      sourceAccountId: srcId,
      targetAccountId: tgtId,
      sourceAmount: formatDecimalForApi(state.sourceAmount, 4),
      targetAmount: formatDecimalForApi(state.targetAmount, 4),
    };

    if (
      state.rate !== null &&
      state.rate > 0 &&
      isRateWithinTolerance(this.mode(), state.sourceAmount, state.targetAmount, state.rate)
    ) {
      payload.appliedExchangeRate = formatDecimalForApi(state.rate, 6);
    }

    if (parsedFee !== null && parsedFee > 0) {
      payload.feeAmount = formatDecimalForApi(parsedFee, 4);
      payload.feeCurrency = this.feeCurrency();
    }

    const dateVal = this.exchangeDate().trim();
    if (dateVal) {
      payload.exchangeDate = dateVal;
    }

    const trimmedNotes = this.notes().trim();
    if (trimmedNotes) {
      payload.notes = trimmedNotes;
    }

    this.submitted.emit(payload);
  }

  private resetCalculation(mode: ExchangeMode): void {
    this.calcState.set(createInitialCalculationState(mode));
    this.sourceInput.set('');
    this.targetInput.set('');
    this.rateInput.set('');
  }

  private pickDefaultAccountId(accounts: readonly Account[], currencyCode: string): string {
    if (accounts.length === 0) {
      return '';
    }
    const preferredId = this.defaultAccountIds()[normalizeCurrency(currencyCode)];
    if (preferredId && accounts.some((acc) => acc.id === preferredId)) {
      return preferredId;
    }
    return accounts.find((acc) => acc.accountType !== 'CREDIT_CARD')?.id ?? accounts[0].id;
  }
}
