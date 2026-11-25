import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { CardComponent } from '../../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { Allowance } from '../data-access/allowances.models';
import {
  formatAdjustmentRule,
  formatFrequency,
  formatShortDate,
} from '../utils/allowance-formatters';

@Component({
  selector: 'fo-allowance-child-view',
  standalone: true,
  imports: [
    CardComponent,
    IconComponent,
    MoneyPipe,
    RouterLink,
    EmptyStateComponent,
  ],
  template: `
    @if (allowance(); as item) {
      <div class="max-w-lg mx-auto space-y-4">
        <fo-card class="overflow-hidden border-accent/20 shadow-sm">
          <div class="flex items-center justify-between gap-3 border-b border-border pb-3">
            <div class="flex items-center gap-2">
              <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-accent-soft text-accent">
                <fo-icon name="wallet" [size]="18" />
              </div>
              <div>
                <h2 class="text-base font-semibold text-text">Tu mensualidad</h2>
                <p class="text-xs text-muted">
                  Asignada por {{ item.assignedBy.fullName }}
                </p>
              </div>
            </div>

            <span class="rounded-full bg-accent-soft px-2.5 py-0.5 text-2xs font-medium text-accent">
              {{ adjustmentText() }}
            </span>
          </div>

          <!-- Financial Highlights -->
          <div class="mt-4 grid grid-cols-2 gap-3">
            <div class="rounded-xl border border-border bg-surface-2/60 p-3.5">
              <span class="text-xs text-muted">Monto mensual</span>
              <p class="mt-1 text-xl font-bold text-text tabular-nums">
                {{ item.currentAmount | money: item.currencyCode }}
              </p>
              <span class="text-2xs text-muted mt-0.5 block">
                {{ frequencyText() }}
              </span>
            </div>

            <div class="rounded-xl border border-border bg-surface-2/60 p-3.5">
              <span class="text-xs text-muted">Saldo disponible</span>
              <p class="mt-1 text-xl font-bold text-accent tabular-nums">
                {{ item.childBalance | money: item.currencyCode }}
              </p>
              <span class="text-2xs text-muted mt-0.5 block truncate">
                En {{ item.targetAccount.name }}
              </span>
            </div>
          </div>

          <!-- Next disbursement info -->
          <div class="mt-4 rounded-xl bg-surface-2/40 px-3.5 py-2.5 text-xs text-muted flex items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <fo-icon name="calendar-range" [size]="15" class="text-accent" />
              <span>Próxima acreditación:</span>
            </div>
            <strong class="text-text font-medium">{{ nextDateText() }}</strong>
          </div>

          <!-- Link to Transactions -->
          <div class="mt-5 border-t border-border pt-4">
            <a
              routerLink="/transactions"
              class="fo-focus-ring flex w-full items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast shadow-xs transition-opacity hover:opacity-90"
            >
              <fo-icon name="file-text" [size]="16" />
              <span>Ver mis movimientos</span>
            </a>
          </div>
        </fo-card>
      </div>
    } @else {
      <fo-empty-state
        icon="wallet"
        title="Sin mensualidad asignada"
        description="Todavía no tenés una mensualidad configurada. Hablá con tus padres o tutores para que la habiliten."
      >
        <a
          routerLink="/transactions"
          class="fo-focus-ring inline-flex items-center gap-2 rounded-xl bg-surface-2 px-4 py-2 text-xs font-semibold text-text hover:bg-border transition-colors"
        >
          <fo-icon name="file-text" [size]="14" />
          <span>Ver mis movimientos</span>
        </a>
      </fo-empty-state>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AllowanceChildViewComponent {
  readonly allowance = input<Allowance | null>(null);

  protected readonly frequencyText = computed(() => {
    const item = this.allowance();
    return item ? formatFrequency(item.frequency, item.dayOfPeriod) : '';
  });

  protected readonly nextDateText = computed(() => {
    const item = this.allowance();
    return item ? formatShortDate(item.nextDisbursementDate) : '';
  });

  protected readonly adjustmentText = computed(() => {
    const item = this.allowance();
    return item ? formatAdjustmentRule(item.adjustmentRule) : '';
  });
}
