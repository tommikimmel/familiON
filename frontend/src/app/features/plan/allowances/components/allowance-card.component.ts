import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { ButtonComponent } from '../../../../shared/ui/button/button.component';
import { CardComponent } from '../../../../shared/ui/card/card.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { readableTextOn } from '../../../../shared/utils/color';
import { Allowance } from '../data-access/allowances.models';
import {
  formatAdjustmentRule,
  formatFrequency,
  formatShortDate,
  getInitials,
} from '../utils/allowance-formatters';

@Component({
  selector: 'fo-allowance-card',
  standalone: true,
  imports: [CardComponent, ButtonComponent, IconComponent, MoneyPipe],
  template: `
    <fo-card class="relative transition-all duration-150">
      <div class="flex items-start justify-between gap-3">
        <!-- Child Avatar & Name -->
        <div class="flex items-center gap-3 min-w-0">
          <div
            class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full font-bold text-sm shadow-xs"
            [style.backgroundColor]="allowance().child.accentColor"
            [style.color]="avatarTextColor()"
          >
            {{ childInitials() }}
          </div>

          <div class="min-w-0">
            <h3 class="truncate text-base font-semibold text-text">
              {{ allowance().child.fullName }}
            </h3>
            <p class="text-xs text-muted truncate">
              Asignada por {{ allowance().assignedBy.fullName }}
            </p>
          </div>
        </div>

        <!-- Options/Edit Button -->
        <div class="flex items-center gap-1 -mr-1 -mt-1">
          <button
            type="button"
            aria-label="Editar mensualidad"
            (click)="edit.emit(allowance())"
            class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
          >
            <fo-icon name="pencil" [size]="16" />
          </button>
        </div>
      </div>

      <!-- Main Financial Details -->
      <div class="mt-4 grid grid-cols-2 gap-3 rounded-xl border border-border bg-surface-2/60 p-3">
        <div>
          <span class="text-xs text-muted">Monto vigente</span>
          <div class="mt-0.5">
            <strong class="text-base font-bold text-text tabular-nums">
              {{ allowance().currentAmount | money: allowance().currencyCode }}
            </strong>
            @if (hasAdjustment()) {
              <span class="block text-2xs text-muted tabular-nums">
                Base: {{ allowance().amount | money: allowance().currencyCode }}
              </span>
            }
          </div>
        </div>

        <div>
          <span class="text-xs text-muted">Saldo del hijo</span>
          <div class="mt-0.5">
            <strong
              class="text-base font-bold tabular-nums"
              [class.text-text]="!isNegativeBalance()"
              [class.text-danger]="isNegativeBalance()"
            >
              {{ allowance().childBalance | money: allowance().currencyCode }}
            </strong>
          </div>
        </div>
      </div>

      <!-- Schedule & Details -->
      <div class="mt-3 space-y-1.5 text-xs text-muted">
        <div class="flex items-center gap-1.5">
          <fo-icon name="calendar-range" [size]="14" class="text-muted" />
          <span>{{ frequencyText() }}</span>
        </div>

        <div class="flex items-center justify-between gap-2">
          <span>Próxima acreditación:</span>
          <span class="font-medium text-text">{{ nextDateText() }}</span>
        </div>

        @if (allowance().targetAccount?.name) {
          <div class="flex items-center justify-between gap-2">
            <span>Cuenta destino:</span>
            <span class="font-medium text-text truncate max-w-[60%]">
              {{ allowance().targetAccount.name }}
            </span>
          </div>
        }
      </div>

      <!-- Rule Badge -->
      <div class="mt-3 flex items-center justify-between gap-2 border-t border-border pt-3">
        <span class="inline-flex items-center rounded-full bg-surface-2 px-2.5 py-0.5 text-2xs font-medium text-muted">
          {{ adjustmentRuleText() }}
        </span>

        <!-- Disburse action button -->
        <fo-button
          variant="primary"
          size="sm"
          type="button"
          (click)="disburse.emit(allowance())"
          ariaLabel="Acreditar ahora la mensualidad"
        >
          <fo-icon name="banknote" [size]="14" />
          <span>Acreditar ahora</span>
        </fo-button>
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AllowanceCardComponent {
  readonly allowance = input.required<Allowance>();

  readonly disburse = output<Allowance>();
  readonly edit = output<Allowance>();

  protected readonly childInitials = computed(() =>
    getInitials(this.allowance().child.fullName),
  );

  protected readonly avatarTextColor = computed(() =>
    readableTextOn(this.allowance().child.accentColor),
  );

  protected readonly frequencyText = computed(() =>
    formatFrequency(this.allowance().frequency, this.allowance().dayOfPeriod),
  );

  protected readonly nextDateText = computed(() =>
    formatShortDate(this.allowance().nextDisbursementDate),
  );

  protected readonly adjustmentRuleText = computed(() =>
    formatAdjustmentRule(this.allowance().adjustmentRule),
  );

  protected readonly hasAdjustment = computed(
    () => this.allowance().currentAmount !== this.allowance().amount,
  );

  protected readonly isNegativeBalance = computed(
    () => Number(this.allowance().childBalance) < 0,
  );
}
