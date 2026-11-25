import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent, IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { PlanCommitmentItem, PlanInstallmentItem } from '../data-access/plan-overview.models';
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
      title="Compras en cuotas"
      linkRoute="/plan/installments"
      description="Cuánto te queda por pagar y cuánto se lleva cada mes."
      testId="plan-installments-card"
      [degraded]="isFullyDegraded()"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (isEmpty()) {
        <div data-testid="installments-empty-state" class="pl-empty">
          <p class="fo-tx-title">Sin compras en cuotas activas</p>
          <p class="pl-sub">Registrá tus cuotas con tarjeta para proyectar el compromiso de los próximos meses.</p>
          <a
            routerLink="/plan/installments"
            data-testid="installments-empty-cta"
            class="fo-btn-primary fo-focus-ring mt-2 inline-flex items-center gap-1.5"
          >
            <fo-icon name="plus" [size]="14" />
            <span>Registrar compra en cuotas</span>
          </a>
        </div>
      } @else {
        <div class="flex flex-1 flex-col gap-3">
          @if (installments(); as plans) {
            <div class="pl-head items-end">
              <div>
                <p class="fo-kpi-label">Total pendiente</p>
                <p data-testid="installments-total" class="pl-big mt-0.5">
                  {{ totalRemainingLabel() }}
                </p>
              </div>
              <span class="fo-pill-tag fo-num">
                {{ plans.length }} {{ plans.length === 1 ? 'plan activo' : 'planes activos' }}
              </span>
            </div>

            @if (plans.length > 0) {
              <table class="pl-table">
                <thead>
                  <tr>
                    <th scope="col">Compra</th>
                    <th scope="col">Progreso</th>
                    <th scope="col" class="text-right">Cuota</th>
                  </tr>
                </thead>
                <tbody>
                  @for (plan of plans.slice(0, 4); track plan.id) {
                    <tr>
                      <td class="max-w-0 truncate font-semibold">{{ plan.description }}</td>
                      <td class="fo-num whitespace-nowrap">
                        {{ plan.paidInstallments }} / {{ plan.totalInstallments }}
                      </td>
                      <td class="fo-num text-right font-bold whitespace-nowrap">
                        @if (plan.nextInstallment; as nextRow) {
                          {{ nextRow.amount | money: plan.currencyCode }}
                        } @else {
                          —
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          } @else {
            <div data-testid="installments-partial-error" class="pl-empty">
              <span class="pl-sub">No se pudo cargar el detalle de cuotas</span>
              <fo-button variant="ghost" size="sm" [loading]="retrying()" (click)="retry.emit()">
                Reintentar
              </fo-button>
            </div>
          }

          @if (commitment() !== null) {
            @if (commitmentBars().length > 0) {
              <div data-testid="installments-commitment-bars">
                <p class="fo-kpi-label">Cuotas a pagar en los próximos meses</p>
                <div
                  class="inst-bars"
                  [style.gridTemplateColumns]="'repeat(' + commitmentBars().length + ', minmax(0, 1fr))'"
                  role="img"
                  aria-label="Compromiso mensual de cuotas para los próximos meses"
                >
                  @for (bar of commitmentBars(); track bar.month; let first = $first) {
                    <div class="inst-col" [title]="bar.tooltip">
                      <span
                        data-testid="commitment-bar"
                        class="inst-rect"
                        [class.is-current]="first && bar.amount > 0"
                        [style.height.%]="bar.heightPct"
                      ></span>
                      <span class="inst-lbl">{{ bar.shortLabel }}</span>
                    </div>
                  }
                </div>
              </div>
            }
          } @else {
            <div data-testid="commitment-partial-error" class="pl-empty">
              <span class="pl-sub">No se pudo cargar la proyección</span>
              <fo-button variant="ghost" size="sm" [loading]="retrying()" (click)="retry.emit()">
                Reintentar
              </fo-button>
            </div>
          }

          @if (nextInstallment(); as next) {
            <div data-testid="installments-next" class="pl-tx-row mt-auto">
              <div class="pl-tx-body">
                <p class="fo-tx-title">{{ next.description }}</p>
                <p class="fo-tx-meta fo-num">
                  Próxima cuota {{ next.number }}/{{ next.total }} · {{ formatDate(next.date) }}
                </p>
              </div>
              <span class="fo-tx-amt">{{ next.amount | money: next.currencyCode }}</span>
            </div>
          }
        </div>
      }
    </fo-plan-section-card>
  `,
  styleUrl: '../../plan-ui.css',
  styles: `
    .inst-bars {
      display: grid;
      align-items: end;
      gap: 8px;
      height: 92px;
      margin-top: 8px;
    }
    .inst-col {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      gap: 5px;
      height: 100%;
    }
    .inst-rect {
      display: block;
      width: 100%;
      max-width: 34px;
      border-radius: 7px 7px 3px 3px;
      background: var(--fo-surface-2);
      transition: height 0.45s var(--fo-ease-smooth);
    }
    .inst-rect.is-current {
      background: var(--fo-accent);
    }
    .inst-lbl {
      font-size: 10.5px;
      font-weight: 600;
      color: var(--fo-text-muted);
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

  protected readonly isFullyDegraded = computed(() => this.installments() === null && this.commitment() === null);

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
      const summaryParts = Array.from(currencies.entries()).map(([code, val]) => this.moneyPipe.transform(val, code));
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
      heightPct: maxAmount > 0 && row.amount > 0 ? Math.max(14, Math.round((row.amount / maxAmount) * 100)) : 6,
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
