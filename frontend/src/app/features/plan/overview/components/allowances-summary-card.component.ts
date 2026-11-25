import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { readableTextOn } from '../../../../shared/utils/color';
import { PlanAllowanceItem } from '../data-access/plan-overview.models';
import { formatShortDate, getChildInitials, sanitizeAccentHex } from '../utils/plan-overview.formatters';
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
      linkRoute="/plan/allowances"
      description="Lo que le das a cada hijo y cuándo toca el próximo pago."
      testId="plan-allowances-card"
      [degraded]="allowances() === null"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (rows().length > 0) {
        <ul class="pl-tx-list" aria-label="Mensualidades por hijo">
          @for (row of rows(); track row.id) {
            <li data-testid="allowance-item" class="pl-tx-row">
              <span
                class="fo-avatar"
                [style.backgroundColor]="row.avatarBg"
                [style.color]="row.avatarText"
                aria-hidden="true"
              >
                {{ row.initials }}
              </span>
              <div class="pl-tx-body">
                <p class="fo-tx-title">{{ row.fullName }}</p>
                <p class="fo-tx-meta">Próxima: {{ row.nextDateLabel }}</p>
              </div>
              <span class="fo-tx-amt">{{ row.currentAmount | money: row.currencyCode }}</span>
            </li>
          }
        </ul>
      }
    </fo-plan-section-card>
  `,
  styleUrl: '../../plan-ui.css',
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
