import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { IconComponent } from '../../../../shared/ui';
import { PlanRecurringItem } from '../data-access/plan-overview.models';
import {
  formatCurrencyTotals,
  formatShortDate,
  groupAmountsByCurrency,
} from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

@Component({
  selector: 'fo-recurring-summary-card',
  standalone: true,
  imports: [IconComponent, MoneyPipe, PlanSectionCardComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Este mes"
      icon="repeat"
      linkRoute="/plan/recurring"
      testId="plan-recurring-card"
      [degraded]="items() === null"
      [retrying]="retrying()"
      [badgeText]="overdueBadge()"
      badgeTone="danger"
      (retry)="retry.emit()"
    >
      @if (items(); as list) {
        @if (list.length === 0) {
          <div
            data-testid="recurring-empty-state"
            class="flex flex-1 flex-col items-center justify-center rounded-xl border border-dashed border-border bg-surface-2/40 px-4 py-6 text-center"
          >
            <p class="text-sm font-medium text-text">Sin gastos fijos todavía</p>
            <p class="mt-1 text-xs text-muted">
              Cargá alquiler, servicios o suscripciones para seguir los vencimientos del mes.
            </p>
            <a
              routerLink="/plan/recurring"
              data-testid="recurring-empty-cta"
              class="fo-focus-ring mt-3 inline-flex items-center gap-1.5 rounded-xl bg-accent px-3.5 py-2 text-xs font-semibold text-accent-contrast transition-opacity duration-150 hover:opacity-92"
            >
              <fo-icon name="plus" [size]="14" />
              <span>Agregar gasto fijo</span>
            </a>
          </div>
        } @else {
          <div class="flex flex-1 flex-col justify-between gap-3">
            <div class="space-y-3">
              @if (unpaidCount() > 0) {
                <p
                  data-testid="recurring-headline"
                  class="text-lg font-bold tracking-tight text-text tabular-nums"
                >
                  {{ unpaidCount() }}
                  {{ unpaidCount() === 1 ? 'pendiente' : 'pendientes' }}
                  · {{ unpaidTotalsLabel() }}
                </p>
              } @else if (paidCount() > 0) {
                <p
                  data-testid="recurring-headline"
                  class="flex items-center gap-1.5 text-base font-semibold text-success"
                >
                  <fo-icon name="check-circle" [size]="17" />
                  <span>
                    Todo al día · {{ paidCount() }}
                    {{ paidCount() === 1 ? 'pagado' : 'pagados' }}
                  </span>
                </p>
              } @else {
                <p data-testid="recurring-headline" class="text-sm font-medium text-muted">
                  Sin vencimientos programados este mes.
                </p>
              }

              @if (overdueItems().length > 0) {
                <div
                  data-testid="recurring-overdue-list"
                  class="space-y-1.5 rounded-xl border border-danger/30 bg-danger/8 p-2.5"
                >
                  <p class="flex items-center gap-1.5 text-xs font-semibold text-danger">
                    <fo-icon name="alert-circle" [size]="14" />
                    <span>
                      {{ overdueItems().length === 1 ? 'Vencido' : 'Vencidos' }}
                    </span>
                  </p>
                  <ul class="space-y-1">
                    @for (item of visibleOverdue(); track item.id) {
                      <li class="flex items-center justify-between gap-2 text-xs">
                        <span class="min-w-0 truncate font-medium text-danger">
                          {{ item.name }}
                          <span class="font-normal opacity-85">
                            · {{ formatDate(item.month.dueDate) }}
                          </span>
                        </span>
                        <span class="shrink-0 font-semibold text-danger tabular-nums">
                          {{ item.month.estimatedAmount | money: item.currencyCode }}
                        </span>
                      </li>
                    }
                  </ul>
                  @if (extraOverdueCount() > 0) {
                    <p class="text-[11px] font-medium text-danger/90">
                      +{{ extraOverdueCount() }}
                      {{ extraOverdueCount() === 1 ? 'vencido más' : 'vencidos más' }}
                    </p>
                  }
                </div>
              }

              @if (nextPending(); as next) {
                <div
                  data-testid="recurring-next-due"
                  class="rounded-xl border border-border bg-surface-2/60 p-3"
                >
                  <p class="text-[11px] font-semibold tracking-wide text-muted uppercase">
                    Próximo vencimiento
                  </p>
                  <div class="mt-1 flex items-baseline justify-between gap-2">
                    <span class="min-w-0 truncate text-sm font-semibold text-text">
                      {{ next.name }}
                    </span>
                    <span class="shrink-0 text-sm font-bold text-text tabular-nums">
                      {{ next.month.estimatedAmount | money: next.currencyCode }}
                    </span>
                  </div>
                  <p class="mt-0.5 text-xs text-muted">
                    Vence el {{ formatDate(next.month.dueDate) }}
                  </p>
                </div>
              }
            </div>

            @if (applicableCount() > 0) {
              <p class="text-xs text-muted tabular-nums">
                {{ paidCount() }} de {{ applicableCount() }} gastos fijos pagados este mes
              </p>
            }
          </div>
        }
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
export class RecurringSummaryCardComponent {
  readonly items = input<PlanRecurringItem[] | null>(null);
  readonly baseCurrency = input<string>('ARS');
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  private readonly applicable = computed(() =>
    (this.items() ?? []).filter((item) => item.month.status !== 'NOT_APPLICABLE'),
  );

  protected readonly applicableCount = computed(() => this.applicable().length);

  protected readonly overdueItems = computed(() =>
    this.applicable()
      .filter((item) => item.month.status === 'OVERDUE')
      .sort((a, b) => a.month.dueDate.localeCompare(b.month.dueDate)),
  );

  protected readonly visibleOverdue = computed(() => this.overdueItems().slice(0, 2));

  protected readonly extraOverdueCount = computed(() =>
    Math.max(0, this.overdueItems().length - this.visibleOverdue().length),
  );

  protected readonly overdueBadge = computed<string | null>(() => {
    const count = this.overdueItems().length;
    if (count === 0) {
      return null;
    }
    return count === 1 ? '1 vencido' : `${count} vencidos`;
  });

  private readonly pendingItems = computed(() =>
    this.applicable()
      .filter((item) => item.month.status === 'PENDING')
      .sort((a, b) => a.month.dueDate.localeCompare(b.month.dueDate)),
  );

  protected readonly paidCount = computed(
    () => this.applicable().filter((item) => item.month.status === 'PAID').length,
  );

  private readonly unpaidItems = computed(() => [...this.overdueItems(), ...this.pendingItems()]);

  protected readonly unpaidCount = computed(() => this.unpaidItems().length);

  protected readonly unpaidTotalsLabel = computed(() => {
    const totals = groupAmountsByCurrency(
      this.unpaidItems().map((item) => ({
        amount: item.month.estimatedAmount,
        currencyCode: item.currencyCode,
      })),
      this.baseCurrency(),
    );
    return formatCurrencyTotals(totals);
  });

  protected readonly nextPending = computed<PlanRecurringItem | null>(
    () => this.pendingItems()[0] ?? null,
  );

  protected formatDate(isoDate: string): string {
    return formatShortDate(isoDate);
  }
}
