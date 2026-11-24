import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { readableTextOn } from '../../../../shared/utils/color';
import { PlanAllowanceItem } from '../data-access/plan-overview.models';
import {
  formatShortDate,
  getChildInitials,
  sanitizeAccentHex,
} from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

interface AllowanceRowViewModel {
  id: string;
  fullName: string;
  initials: string;
  avatarBg: string;
  avatarText: string;
  currentAmount: string;
  currencyCode: string;
  nextDateLabel: string;
}

@Component({
  selector: 'fo-allowances-summary-card',
  standalone: true,
  imports: [MoneyPipe, PlanSectionCardComponent],
  template: `
    <fo-plan-section-card
      title="Mensualidades"
      icon="baby"
      linkRoute="/plan/allowances"
      testId="plan-allowances-card"
      [degraded]="allowances() === null"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (rows().length > 0) {
        <ul class="space-y-2.5" aria-label="Mensualidades por hijo">
          @for (row of rows(); track row.id) {
            <li
              data-testid="allowance-item"
              class="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface-2/50 px-3 py-2.5"
            >
              <div class="flex min-w-0 items-center gap-2.5">
                <span
                  class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold"
                  [style.backgroundColor]="row.avatarBg"
                  [style.color]="row.avatarText"
                  aria-hidden="true"
                >
                  {{ row.initials }}
                </span>
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-text">{{ row.fullName }}</p>
                  <p class="text-xs text-muted">Próxima: {{ row.nextDateLabel }}</p>
                </div>
              </div>

              <span class="shrink-0 text-sm font-bold text-text tabular-nums">
                {{ row.currentAmount | money: row.currencyCode }}
              </span>
            </li>
          }
        </ul>
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
export class AllowancesSummaryCardComponent {
  readonly allowances = input<PlanAllowanceItem[] | null>(null);
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  protected readonly rows = computed<AllowanceRowViewModel[]>(() =>
    (this.allowances() ?? []).map((item) => {
      const avatarBg = sanitizeAccentHex(item.child.accentColor);
      return {
        id: item.id,
        fullName: item.child.fullName,
        initials: getChildInitials(item.child.fullName),
        avatarBg,
        avatarText: readableTextOn(avatarBg),
        currentAmount: item.currentAmount,
        currencyCode: item.currencyCode,
        nextDateLabel: formatShortDate(item.nextDisbursementDate),
      };
    }),
  );
}
