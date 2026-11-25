import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { CardComponent, IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { InstallmentPlan } from '../data-access/installments.models';
import { formatIsoDate, formatSurchargePct, formatYearMonthShort } from '../utils/installments-format';

@Component({
  selector: 'fo-installment-plan-card',
  standalone: true,
  imports: [CardComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card padding="none" [interactive]="true">
      <button
        type="button"
        data-testid="installment-plan-card"
        (click)="selected.emit(plan())"
        class="fo-focus-ring block w-full cursor-pointer rounded-[var(--fo-radius)] p-3.5 text-left lg:px-4"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex min-w-0 items-center gap-3">
            <div
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-[10px]"
              [style.background-color]="categoryTint()"
              [style.color]="plan().category.colorHex || 'var(--fo-accent)'"
            >
              <fo-icon [name]="plan().category.icon || 'credit-card'" [size]="15" />
            </div>

            <div class="min-w-0">
              <h3 class="fo-tx-title truncate">
                {{ plan().description }}
              </h3>
              <p class="fo-tx-meta mt-px flex flex-wrap items-center gap-x-2">
                <span class="inline-flex items-center gap-1">
                  <fo-icon name="credit-card" [size]="11" />
                  <span>{{ plan().creditCardAccount.name }}</span>
                </span>
                @if (showAttributedTo()) {
                  <span>· {{ plan().attributedTo.fullName }}</span>
                }
              </p>
            </div>
          </div>

          <span
            data-testid="plan-surcharge-chip"
            class="fo-pill-tag fo-num shrink-0"
            [class.fo-pill-accent]="plan().hasInterest"
          >
            {{ surchargeChipLabel() }}
          </span>
        </div>

        <p data-testid="plan-summary-line" class="fo-tx-meta fo-num mt-2.5">
          {{ plan().totalInstallments }} cuotas de {{ plan().installmentAmount | money: plan().currencyCode }}/mes ·
          Total
          {{ plan().financedTotal | money: plan().currencyCode }}
          ({{ billingRange() }})
        </p>

        <div class="mt-2.5 space-y-1.5">
          <div class="flex items-center justify-between text-[11px]">
            <span data-testid="plan-progress-label" class="font-medium text-text tabular-nums">
              Cuota {{ plan().paidInstallments }} de {{ plan().totalInstallments }}
            </span>
            <span class="text-muted tabular-nums">{{ progressPct() }}%</span>
          </div>

          <div
            role="progressbar"
            [attr.aria-valuenow]="plan().paidInstallments"
            aria-valuemin="0"
            [attr.aria-valuemax]="plan().totalInstallments"
            [attr.aria-label]="'Progreso de cuotas de ' + plan().description"
            class="fo-progress-track"
          >
            <div
              class="fo-progress-fill"
              [style.width.%]="progressPct()"
              [class.bg-success]="plan().status === 'COMPLETED'"
              [class.bg-muted]="plan().status === 'CANCELLED'"
            ></div>
          </div>
        </div>

        <div class="mt-3 grid grid-cols-2 gap-3 border-t border-border pt-2.5">
          @if (plan().nextInstallment; as next) {
            <div>
              <p class="text-[10.5px] text-muted">Próxima cuota</p>
              <p data-testid="plan-next-installment" class="fo-tx-amt mt-px">
                {{ next.amount | money: plan().currencyCode }}
              </p>
              <p class="text-[10.5px] text-muted">{{ formatDate(next.date) }}</p>
            </div>
          } @else {
            <div>
              <p class="text-[10.5px] text-muted">Total financiado</p>
              <p class="fo-tx-amt mt-px">
                {{ plan().financedTotal | money: plan().currencyCode }}
              </p>
              <p class="text-[10.5px] text-muted">{{ statusSubtitle() }}</p>
            </div>
          }

          <div class="text-right">
            <p class="text-[10.5px] text-muted">Restante</p>
            <p data-testid="plan-remaining-amount" class="fo-tx-amt mt-px">
              {{ plan().remainingAmount | money: plan().currencyCode }}
            </p>
            @if (showRealRemaining()) {
              <p data-testid="plan-real-remaining" class="mt-0.5 text-xs font-medium text-accent tabular-nums">
                ≈ {{ plan().realRemainingAmount | money: plan().currencyCode }} en plata de hoy
              </p>
            }
          </div>
        </div>

        @if (showRealRemaining()) {
          <p
            data-testid="plan-liquation-note"
            class="mt-2.5 flex items-center gap-1.5 rounded-[9px] bg-accent-soft px-2.5 py-1.5 text-[10.5px] text-text-2"
          >
            <fo-icon name="trending-up" [size]="13" class="text-accent" />
            <span>Las cuotas fijas se licúan con la inflación</span>
          </p>
        }
      </button>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstallmentPlanCardComponent {
  readonly plan = input.required<InstallmentPlan>();
  readonly showAttributedTo = input<boolean>(false);

  readonly selected = output<InstallmentPlan>();

  protected readonly progressPct = computed<number>(() => {
    const total = this.plan().totalInstallments;
    if (total <= 0) {
      return 0;
    }
    const raw = Math.round((this.plan().paidInstallments / total) * 100);
    return Math.max(0, Math.min(100, raw));
  });

  protected readonly billingRange = computed<string>(() => {
    const current = this.plan();
    const from = formatYearMonthShort(current.firstBillingDate.slice(0, 7));
    const to = formatYearMonthShort(current.lastBillingDate.slice(0, 7));
    return `${from} → ${to}`;
  });

  protected readonly surchargeChipLabel = computed<string>(() => {
    const current = this.plan();
    if (!current.hasInterest) {
      return 'Sin interés';
    }
    if (current.surchargePct !== null) {
      return `Recargo ${formatSurchargePct(current.surchargePct)}`;
    }
    return 'Con interés';
  });

  protected readonly showRealRemaining = computed<boolean>(() => {
    const current = this.plan();
    return current.status === 'ACTIVE' && current.realRemainingAmount !== null && toNumber(current.remainingAmount) > 0;
  });

  protected readonly categoryTint = computed<string>(() => {
    const color = this.plan().category.colorHex;
    if (color && /^#[0-9a-fA-F]{3,6}$/.test(color.trim())) {
      return `color-mix(in srgb, ${color.trim()} 14%, transparent)`;
    }
    return 'var(--fo-accent-soft)';
  });

  protected readonly statusSubtitle = computed<string>(() => {
    switch (this.plan().status) {
      case 'COMPLETED':
        return 'Plan terminado';
      case 'CANCELLED':
        return 'Plan cancelado';
      case 'ACTIVE':
        return 'Sin cuotas pendientes';
    }
  });

  protected formatDate(isoDate: string): string {
    return formatIsoDate(isoDate);
  }
}
