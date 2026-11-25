import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { IconComponent } from '../../../../shared/ui';
import { PlanRecurringItem } from '../data-access/plan-overview.models';
import { formatCurrencyTotals, formatShortDate, groupAmountsByCurrency } from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

/** Maximum rows shown in the overview list (mock shows a short month checklist). */
const MAX_ROWS = 4;

@Component({
  selector: 'fo-recurring-summary-card',
  standalone: true,
  imports: [IconComponent, MoneyPipe, PlanSectionCardComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Gastos fijos del mes"
      linkRoute="/plan/recurring"
      description="Alquiler, servicios y suscripciones: qué ya pagaste y qué falta."
      testId="plan-recurring-card"
      [flatOnMobile]="true"
      [degraded]="items() === null"
      [retrying]="retrying()"
      [badgeText]="overdueBadge()"
      badgeTone="danger"
      (retry)="retry.emit()"
    >
      @if (items(); as list) {
        @if (list.length === 0) {
          <div data-testid="recurring-empty-state" class="pl-empty">
            <p class="fo-tx-title">Sin gastos fijos todavía</p>
            <p class="pl-sub">Cargá alquiler, servicios o suscripciones para seguir los vencimientos del mes.</p>
            <a
              routerLink="/plan/recurring"
              data-testid="recurring-empty-cta"
              class="fo-btn-primary fo-focus-ring mt-2 inline-flex items-center gap-1.5"
            >
              <fo-icon name="plus" [size]="14" />
              <span>Agregar gasto fijo</span>
            </a>
          </div>
        } @else {
          <div class="flex flex-1 flex-col gap-2">
            @if (unpaidCount() > 0) {
              <p data-testid="recurring-headline" class="pl-sub fo-num">
                {{ unpaidCount() }}
                {{ unpaidCount() === 1 ? 'pendiente' : 'pendientes' }}
                · {{ unpaidTotalsLabel() }}
              </p>
            } @else if (paidCount() > 0) {
              <p data-testid="recurring-headline" class="pl-sub">
                Todo al día · {{ paidCount() }} {{ paidCount() === 1 ? 'pagado' : 'pagados' }}
              </p>
            } @else {
              <p data-testid="recurring-headline" class="pl-sub">Sin vencimientos programados este mes.</p>
            }

            <div class="pl-tx-list">
              @if (overdueItems().length > 0) {
                <div data-testid="recurring-overdue-list" class="pl-tx-list">
                  @for (item of visibleOverdue(); track item.id) {
                    <div class="pl-tx-row">
                      <span class="pl-check is-late" aria-hidden="true"></span>
                      <div class="pl-tx-body">
                        <p class="fo-tx-title">{{ item.name }}</p>
                        <p class="fo-tx-meta pl-danger">Vencido · {{ formatDate(item.month.dueDate) }}</p>
                      </div>
                      <span class="fo-tx-amt">
                        {{ item.month.estimatedAmount | money: item.currencyCode }}
                      </span>
                    </div>
                  }
                  @if (extraOverdueCount() > 0) {
                    <p class="pl-sub pl-danger">
                      +{{ extraOverdueCount() }}
                      {{ extraOverdueCount() === 1 ? 'vencido más' : 'vencidos más' }}
                    </p>
                  }
                </div>
              }

              @for (item of visiblePending(); track item.id; let first = $first) {
                <div class="pl-tx-row" [attr.data-testid]="first ? 'recurring-next-due' : null">
                  <span class="pl-check" aria-hidden="true"></span>
                  <div class="pl-tx-body">
                    <p class="fo-tx-title">{{ item.name }}</p>
                    <p class="fo-tx-meta">Vence el {{ formatDate(item.month.dueDate) }}</p>
                  </div>
                  <span class="fo-tx-amt">
                    {{ item.month.estimatedAmount | money: item.currencyCode }}
                  </span>
                </div>
              }

              @for (item of visiblePaid(); track item.id) {
                <div class="pl-tx-row">
                  <span class="pl-check is-done" aria-hidden="true">
                    <fo-icon name="check" [size]="12" [strokeWidth]="3" />
                  </span>
                  <div class="pl-tx-body">
                    <p class="fo-tx-title line-through opacity-60">{{ item.name }}</p>
                    <p class="fo-tx-meta">Pagado</p>
                  </div>
                  <span class="fo-tx-amt">
                    {{ item.month.estimatedAmount | money: item.currencyCode }}
                  </span>
                </div>
              }
            </div>

            @if (applicableCount() > 0) {
              <p class="pl-sub fo-num mt-auto">
                {{ paidCount() }} de {{ applicableCount() }} gastos fijos pagados este mes
              </p>
            }
          </div>
        }
      }
    </fo-plan-section-card>
  `,
  styleUrl: '../../plan-ui.css',
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

  protected readonly visiblePending = computed(() =>
    this.pendingItems().slice(0, Math.max(1, MAX_ROWS - this.visibleOverdue().length)),
  );

  protected readonly visiblePaid = computed(() => {
    const used = this.visibleOverdue().length + this.visiblePending().length;
    return this.applicable()
      .filter((item) => item.month.status === 'PAID')
      .slice(0, Math.max(0, MAX_ROWS - used));
  });

  protected formatDate(isoDate: string): string {
    return formatShortDate(isoDate);
  }
}
