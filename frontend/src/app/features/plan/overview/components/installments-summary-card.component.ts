import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent, IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import {
  PlanCommitmentItem,
  PlanInstallmentItem,
} from '../data-access/plan-overview.models';
import {
  formatCurrencyTotals,
  formatShortDate,
  formatYearMonthLong,
  formatYearMonthShort,
  groupAmountsByCurrency,
} from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

interface CommitmentBar {
  month: string;
  shortLabel: string;
  heightPct: number;
  amount: number;
  tooltip: string;
}

interface NextInstallmentHighlight {
  description: string;
  number: number;
  total: number;
  date: string;
  amount: string;
  currencyCode: string;
}

@Component({
  selector: 'fo-installments-summary-card',
  standalone: true,
  imports: [ButtonComponent, IconComponent, MoneyPipe, PlanSectionCardComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Cuotas"
      icon="credit-card"
      linkRoute="/plan/installments"
      testId="plan-installments-card"
      [degraded]="isFullyDegraded()"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (isEmpty()) {
        <div
          data-testid="installments-empty-state"
          class="flex flex-1 flex-col items-center justify-center rounded-xl border border-dashed border-border bg-surface-2/40 px-4 py-6 text-center"
        >
          <p class="text-sm font-medium text-text">Sin compras en cuotas activas</p>
          <p class="mt-1 text-xs text-muted">
            Registrá tus cuotas con tarjeta para proyectar el compromiso de los próximos meses.
          </p>
          <a
            routerLink="/plan/installments"
            data-testid="installments-empty-cta"
            class="fo-focus-ring mt-3 inline-flex items-center gap-1.5 rounded-xl bg-accent px-3.5 py-2 text-xs font-semibold text-accent-contrast transition-opacity duration-150 hover:opacity-92"
          >
            <fo-icon name="plus" [size]="14" />
            <span>Registrar cuota</span>
          </a>
        </div>
      } @else {
        <div class="flex flex-1 flex-col justify-between gap-4">
          @if (installments(); as plans) {
            <div>
              <p class="text-xs font-medium text-muted">Total pendiente</p>
              <p
                data-testid="installments-total"
                class="mt-0.5 text-xl font-bold tracking-tight text-text tabular-nums"
              >
                {{ totalRemainingLabel() }}
              </p>
              <p class="mt-0.5 text-xs text-muted tabular-nums">
                {{ plans.length }} {{ plans.length === 1 ? 'plan activo' : 'planes activos' }}
              </p>
            </div>
          } @else {
            <div
              data-testid="installments-partial-error"
              class="flex items-center justify-between gap-2 rounded-xl border border-dashed border-border bg-surface-2/40 px-3 py-2"
            >
              <span class="text-xs text-muted">No se pudo cargar el detalle de cuotas</span>
              <fo-button variant="ghost" size="sm" [loading]="retrying()" (click)="retry.emit()">
                Reintentar
              </fo-button>
            </div>
          }

          @if (commitment() !== null) {
            @if (commitmentBars().length > 0) {
              <div data-testid="installments-commitment-bars" class="space-y-1.5">
                <p class="text-[11px] font-semibold tracking-wide text-muted uppercase">
                  Próximos 6 meses
                </p>
                <div
                  class="grid h-18 items-end gap-1.5 rounded-xl bg-surface-2/50 px-2.5 pt-3 pb-2"
                  [style.gridTemplateColumns]="'repeat(' + commitmentBars().length + ', minmax(0, 1fr))'"
                  role="img"
                  aria-label="Compromiso mensual de cuotas para los próximos meses"
                >
                  @for (bar of commitmentBars(); track bar.month) {
                    <div
                      class="flex h-full flex-col items-center justify-end gap-1"
                      [title]="bar.tooltip"
                    >
                      <div class="flex w-full flex-1 items-end justify-center">
                        <span
                          data-testid="commitment-bar"
                          class="block w-full max-w-6 rounded-t-md transition-all duration-300"
                          [class.bg-accent]="bar.amount > 0"
                          [class.bg-border]="bar.amount <= 0"
                          [style.height.%]="bar.heightPct"
                        ></span>
                      </div>
                      <span class="text-[10px] font-medium text-muted">
                        {{ bar.shortLabel }}
                      </span>
                    </div>
                  }
                </div>
              </div>
            }
          } @else {
            <div
              data-testid="commitment-partial-error"
              class="flex items-center justify-between gap-2 rounded-xl border border-dashed border-border bg-surface-2/40 px-3 py-2"
            >
              <span class="text-xs text-muted">No se pudo cargar la proyección</span>
              <fo-button variant="ghost" size="sm" [loading]="retrying()" (click)="retry.emit()">
                Reintentar
              </fo-button>
            </div>
          }

          @if (nextInstallment(); as next) {
            <div
              data-testid="installments-next"
              class="rounded-xl border border-border bg-surface-2/60 p-3"
            >
              <div class="flex items-center justify-between gap-2">
                <span class="text-[11px] font-semibold tracking-wide text-muted uppercase">
                  Próxima cuota
                </span>
                <span class="text-[11px] font-medium text-muted tabular-nums">
                  Cuota {{ next.number }}/{{ next.total }}
                </span>
              </div>
              <div class="mt-1 flex items-baseline justify-between gap-2">
                <span class="min-w-0 truncate text-sm font-semibold text-text">
                  {{ next.description }}
                </span>
                <span class="shrink-0 text-sm font-bold text-text tabular-nums">
                  {{ next.amount | money: next.currencyCode }}
                </span>
              </div>
              <p class="mt-0.5 text-xs text-muted">{{ formatDate(next.date) }}</p>
            </div>
          }
        </div>
      }
    </fo-plan-section-card>
  `,
  styles: `
    :host {
      display: block;
      height: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstallmentsSummaryCardComponent {
  readonly installments = input<PlanInstallmentItem[] | null>(null);
  readonly commitment = input<PlanCommitmentItem[] | null>(null);
  readonly baseCurrency = input<string>('ARS');
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  private readonly moneyPipe = new MoneyPipe();

  protected readonly isFullyDegraded = computed(
    () => this.installments() === null && this.commitment() === null,
  );

  protected readonly isEmpty = computed(() => {
    const plans = this.installments();
    const comm = this.commitment();
    if (plans === null) {
      return false;
    }
    const noPlans = plans.length === 0;
    const noCommitment = comm === null || comm.every((item) => toNumber(item.amount) <= 0);
    return noPlans && noCommitment;
  });

  protected readonly totalRemainingLabel = computed(() => {
    const plans = this.installments() ?? [];
    if (plans.length === 0) {
      return this.moneyPipe.transform(0, this.baseCurrency());
    }
    const totals = groupAmountsByCurrency(
      plans.map((plan) => ({
        amount: plan.remainingAmount,
        currencyCode: plan.currencyCode,
      })),
      this.baseCurrency(),
    );
    return formatCurrencyTotals(totals);
  });

  protected readonly commitmentBars = computed<CommitmentBar[]>(() => {
    const raw = this.commitment();
    if (!raw || raw.length === 0) {
      return [];
    }

    const preferred = this.baseCurrency().trim().toUpperCase();
    const byMonth = new Map<string, Map<string, number>>();
    for (const entry of raw) {
      const monthKey = entry.month.trim();
      const currency = entry.currencyCode.trim().toUpperCase() || preferred;
      let monthMap = byMonth.get(monthKey);
      if (!monthMap) {
        monthMap = new Map<string, number>();
        byMonth.set(monthKey, monthMap);
      }
      monthMap.set(currency, (monthMap.get(currency) ?? 0) + toNumber(entry.amount));
    }

    const sortedMonths = Array.from(byMonth.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(0, 6);

    const rows = sortedMonths.map(([month, currencies]) => {
      const amount = currencies.get(preferred) ?? Array.from(currencies.values())[0] ?? 0;
      const summaryParts = Array.from(currencies.entries()).map(([code, val]) =>
        this.moneyPipe.transform(val, code),
      );
      return {
        month,
        amount: Math.max(0, amount),
        tooltip: `${formatYearMonthLong(month)}: ${summaryParts.join(' + ')}`,
      };
    });

    const maxAmount = rows.reduce((max, row) => Math.max(max, row.amount), 0);

    return rows.map((row) => ({
      month: row.month,
      shortLabel: formatYearMonthShort(row.month),
      amount: row.amount,
      heightPct:
        maxAmount > 0 && row.amount > 0
          ? Math.max(14, Math.round((row.amount / maxAmount) * 100))
          : 6,
      tooltip: row.tooltip,
    }));
  });

  protected readonly nextInstallment = computed<NextInstallmentHighlight | null>(() => {
    const plans = this.installments();
    if (!plans || plans.length === 0) {
      return null;
    }

    const upcoming = plans
      .filter(
        (
          plan,
        ): plan is PlanInstallmentItem & {
          nextInstallment: NonNullable<PlanInstallmentItem['nextInstallment']>;
        } => plan.nextInstallment !== null,
      )
      .sort((a, b) => a.nextInstallment.date.localeCompare(b.nextInstallment.date));

    const earliest = upcoming[0];
    if (!earliest) {
      return null;
    }

    return {
      description: earliest.description,
      number: earliest.nextInstallment.number,
      total: earliest.totalInstallments,
      date: earliest.nextInstallment.date,
      amount: earliest.nextInstallment.amount,
      currencyCode: earliest.currencyCode,
    };
  });

  protected formatDate(isoDate: string): string {
    return formatShortDate(isoDate);
  }
}
