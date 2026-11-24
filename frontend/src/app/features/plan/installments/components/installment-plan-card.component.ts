import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { CardComponent, IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { InstallmentPlan } from '../data-access/installments.models';
import { formatIsoDate, formatSurchargePct } from '../utils/installments-format';

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
        class="fo-focus-ring block w-full cursor-pointer rounded-[var(--fo-radius)] p-4 text-left"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex min-w-0 items-center gap-3">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
              [style.background-color]="categoryTint()"
              [style.color]="plan().category.colorHex || 'var(--fo-accent)'"
            >
              <fo-icon [name]="plan().category.icon || 'credit-card'" [size]="18" />
            </div>

            <div class="min-w-0">
              <h3 class="truncate text-sm font-semibold text-text">
                {{ plan().description }}
              </h3>
              <p class="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted">
                <span class="inline-flex items-center gap-1">
                  <fo-icon name="credit-card" [size]="13" />
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
            class="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium tabular-nums"
            [class.bg-accent-soft]="!plan().hasInterest"
            [class.text-accent]="!plan().hasInterest"
            [class.bg-warning/15]="plan().hasInterest"
            [class.text-warning]="plan().hasInterest"
          >
            {{ surchargeChipLabel() }}
          </span>
        </div>

        <div class="mt-4 space-y-1.5">
          <div class="flex items-center justify-between text-xs">
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
            class="h-2 w-full overflow-hidden rounded-full bg-surface-2"
          >
            <div
              class="h-full rounded-full transition-all duration-300"
              [style.width.%]="progressPct()"
              [class.bg-accent]="plan().status === 'ACTIVE'"
              [class.bg-success]="plan().status === 'COMPLETED'"
              [class.bg-muted]="plan().status === 'CANCELLED'"
            ></div>
          </div>
        </div>

        <div class="mt-4 grid grid-cols-2 gap-3 border-t border-border pt-3">
          @if (plan().nextInstallment; as next) {
            <div>
              <p class="text-xs text-muted">Próxima cuota</p>
              <p
                data-testid="plan-next-installment"
                class="mt-0.5 text-sm font-semibold text-text tabular-nums"
              >
                {{ next.amount | money: plan().currencyCode }}
              </p>
              <p class="text-xs text-muted">{{ formatDate(next.date) }}</p>
            </div>
          } @else {
            <div>
              <p class="text-xs text-muted">Total financiado</p>
              <p class="mt-0.5 text-sm font-semibold text-text tabular-nums">
                {{ plan().financedTotal | money: plan().currencyCode }}
              </p>
              <p class="text-xs text-muted">{{ statusSubtitle() }}</p>
            </div>
          }

          <div class="text-right">
            <p class="text-xs text-muted">Restante</p>
            <p
              data-testid="plan-remaining-amount"
              class="mt-0.5 text-sm font-semibold text-text tabular-nums"
            >
              {{ plan().remainingAmount | money: plan().currencyCode }}
            </p>
            @if (showRealRemaining()) {
              <p
                data-testid="plan-real-remaining"
                class="mt-0.5 text-xs font-medium text-accent tabular-nums"
              >
                ≈ {{ plan().realRemainingAmount | money: plan().currencyCode }} en plata de hoy
              </p>
            }
          </div>
        </div>

        @if (showRealRemaining()) {
          <p
            data-testid="plan-liquation-note"
            class="mt-2.5 flex items-center gap-1.5 rounded-lg bg-surface-2 px-2.5 py-1.5 text-[11px] text-muted"
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
    return (
      current.status === 'ACTIVE' &&
      current.realRemainingAmount !== null &&
      toNumber(current.remainingAmount) > 0
    );
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
