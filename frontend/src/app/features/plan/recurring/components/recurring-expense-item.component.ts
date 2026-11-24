import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent, CardComponent, IconComponent } from '../../../../shared/ui';
import { RecurringExpense } from '../data-access/recurring.models';
import {
  adjustmentRuleLabel,
  formatShortDayMonth,
} from '../utils/recurring-format';

@Component({
  selector: 'fo-recurring-expense-item',
  standalone: true,
  imports: [ButtonComponent, CardComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card padding="sm">
      <div
        data-testid="recurring-expense-item"
        class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
      >
        <div class="flex min-w-0 items-start gap-3">
          <div
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
            [style.background-color]="categoryTint()"
            [style.color]="expense().category.colorHex || 'var(--fo-accent)'"
          >
            <fo-icon [name]="expense().category.icon || 'repeat'" [size]="18" />
          </div>

          <div class="min-w-0 flex-1 space-y-1">
            <div class="flex flex-wrap items-center gap-2">
              <h3 class="truncate text-sm font-semibold text-text">
                {{ expense().name }}
              </h3>

              <span
                data-testid="recurring-status-badge"
                class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
                [class.bg-surface-2]="expense().month.status === 'PENDING'"
                [class.text-muted]="expense().month.status === 'PENDING'"
                [class.bg-danger/12]="expense().month.status === 'OVERDUE'"
                [class.text-danger]="expense().month.status === 'OVERDUE'"
                [class.bg-success/15]="expense().month.status === 'PAID'"
                [class.text-success]="expense().month.status === 'PAID'"
              >
                @if (expense().month.status === 'PAID') {
                  <fo-icon name="check-circle" [size]="12" />
                  <span>Pagado</span>
                } @else if (expense().month.status === 'OVERDUE') {
                  <fo-icon name="alert-circle" [size]="12" />
                  <span>Vencido</span>
                } @else {
                  <span>Pendiente</span>
                }
              </span>
            </div>

            <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted">
              <span>{{ expense().category.name }}</span>
              <span>·</span>
              <span>{{ scopeLabel() }}</span>
              <span>·</span>
              <span>{{ ruleLabel() }}</span>
              @if (expense().account; as acc) {
                <span>·</span>
                <span>{{ acc.name }}</span>
              }
            </div>

            <p class="text-xs" [class.text-danger]="expense().month.status === 'OVERDUE'" [class.text-muted]="expense().month.status !== 'OVERDUE'">
              @if (expense().month.status === 'PAID') {
                Pagado el {{ formatDay(expense().month.paidDate ?? expense().month.dueDate) }}
              } @else if (expense().month.status === 'OVERDUE') {
                Venció el {{ formatDay(expense().month.dueDate) }}
              } @else {
                Vence el {{ formatDay(expense().month.dueDate) }}
              }
            </p>
          </div>
        </div>

        <div class="flex items-center justify-between gap-3 border-t border-border pt-2.5 sm:justify-end sm:border-t-0 sm:pt-0">
          <div class="text-left sm:text-right">
            <p
              data-testid="recurring-item-amount"
              class="text-base font-semibold text-text tabular-nums"
            >
              {{ displayAmount() | money: expense().currencyCode }}
            </p>
            @if (showEstimatedNote()) {
              <p class="text-[11px] text-muted tabular-nums">
                Est. {{ expense().month.estimatedAmount | money: expense().currencyCode }}
              </p>
            }
          </div>

          <div class="flex items-center gap-1.5">
            @if (expense().month.status !== 'PAID') {
              <fo-button
                variant="primary"
                size="sm"
                data-testid="recurring-pay-btn"
                (click)="payRequested.emit(expense())"
              >
                Pagar
              </fo-button>
            }

            @if (isAdult()) {
              <button
                type="button"
                data-testid="recurring-edit-btn"
                [attr.aria-label]="'Editar ' + expense().name"
                (click)="editRequested.emit(expense())"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-xl text-muted transition-colors hover:bg-surface-2 hover:text-text"
              >
                <fo-icon name="pencil" [size]="15" />
              </button>

              <button
                type="button"
                data-testid="recurring-archive-btn"
                [attr.aria-label]="'Archivar ' + expense().name"
                (click)="archiveRequested.emit(expense())"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-xl text-muted transition-colors hover:bg-danger/10 hover:text-danger"
              >
                <fo-icon name="trash-2" [size]="15" />
              </button>
            }
          </div>
        </div>
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecurringExpenseItemComponent {
  readonly expense = input.required<RecurringExpense>();
  readonly isAdult = input<boolean>(false);

  readonly payRequested = output<RecurringExpense>();
  readonly editRequested = output<RecurringExpense>();
  readonly archiveRequested = output<RecurringExpense>();

  protected readonly displayAmount = computed<string>(() => {
    const current = this.expense();
    if (current.month.status === 'PAID' && current.month.paidAmount !== null) {
      return current.month.paidAmount;
    }
    return current.month.estimatedAmount;
  });

  protected readonly showEstimatedNote = computed<boolean>(() => {
    const current = this.expense();
    return (
      current.month.status === 'PAID' &&
      current.month.paidAmount !== null &&
      Number(current.month.paidAmount) !== Number(current.month.estimatedAmount)
    );
  });

  protected readonly scopeLabel = computed<string>(() =>
    this.expense().scope === 'FAMILY' ? 'Familia' : 'Personal',
  );

  protected readonly ruleLabel = computed<string>(() =>
    adjustmentRuleLabel(this.expense().adjustmentRule),
  );

  protected readonly categoryTint = computed<string>(() => {
    const color = this.expense().category.colorHex;
    if (color && /^#[0-9a-fA-F]{3,6}$/.test(color.trim())) {
      return `color-mix(in srgb, ${color.trim()} 14%, transparent)`;
    }
    return 'var(--fo-accent-soft)';
  });

  protected formatDay(isoDate: string): string {
    return formatShortDayMonth(isoDate);
  }
}
