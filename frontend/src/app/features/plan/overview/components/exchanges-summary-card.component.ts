import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CurrencyFlagComponent, IconComponent } from '../../../../shared/ui';
import { toNumber } from '../../../../shared/utils/decimal';
import { PlanPositionItem } from '../data-access/plan-overview.models';
import { formatPositionUnits, formatSignedPercent } from '../utils/plan-overview.formatters';
import { PlanSectionCardComponent } from './plan-section-card.component';

interface PositionChipViewModel {
  currencyCode: string;
  unitsLabel: string;
  resultPctLabel: string | null;
  tone: 'positive' | 'negative' | 'neutral';
}

@Component({
  selector: 'fo-exchanges-summary-card',
  standalone: true,
  imports: [CurrencyFlagComponent, IconComponent, PlanSectionCardComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Ahorro en divisas"
      linkRoute="/plan/exchanges"
      testId="plan-exchanges-card"
      [glow]="true"
      [degraded]="positions() === null"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (positions(); as list) {
        @if (list.length === 0) {
          <div data-testid="exchanges-empty-state" class="pl-empty">
            <p class="fo-tx-title">Sin divisas registradas</p>
            <p class="pl-sub">Registrá compras o ventas de moneda extranjera y seguí su resultado.</p>
            <a
              routerLink="/plan/exchanges"
              data-testid="exchanges-empty-cta"
              class="fo-btn-primary fo-focus-ring mt-2 inline-flex items-center gap-1.5"
            >
              <fo-icon name="arrow-left-right" [size]="14" />
              <span>Comprar/Vender</span>
            </a>
          </div>
        } @else {
          <div class="flex flex-1 flex-col justify-between gap-3">
            <ul class="flex flex-col gap-2" aria-label="Tenencias por divisa">
              @for (chip of chips(); track chip.currencyCode; let first = $first) {
                <li data-testid="exchange-position-chip" class="flex items-center justify-between gap-2">
                  <span class="flex min-w-0 items-center gap-2">
                    <fo-currency-flag [currency]="chip.currencyCode" />
                    <span [class.pl-big]="first" [class.fo-tx-amt]="!first">
                      {{ chip.unitsLabel }}
                    </span>
                  </span>
                  @if (chip.resultPctLabel; as pct) {
                    <span
                      data-testid="exchange-position-pct"
                      class="fo-pill-tag fo-num"
                      [class.fo-pill-accent]="chip.tone === 'positive'"
                      [class.pl-pill-danger]="chip.tone === 'negative'"
                    >
                      {{ pct }}
                    </span>
                  }
                </li>
              }
            </ul>

            <div class="flex items-center justify-between gap-2">
              <p class="pl-sub">Resultado sobre tu costo de compra.</p>
              <a
                routerLink="/plan/exchanges"
                data-testid="exchanges-trade-cta"
                class="fo-pill-tag fo-pill-accent fo-focus-ring gap-1"
              >
                <fo-icon name="arrow-left-right" [size]="11" />
                <span>Comprar/Vender</span>
              </a>
            </div>
          </div>
        }
      }
    </fo-plan-section-card>
  `,
  styleUrl: '../../plan-ui.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExchangesSummaryCardComponent {
  readonly positions = input<PlanPositionItem[] | null>(null);
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  protected readonly chips = computed<PositionChipViewModel[]>(() =>
    (this.positions() ?? []).map((pos) => {
      const code = pos.currencyCode.trim().toUpperCase();
      const pctNumeric = pos.unrealizedResultPct !== null ? toNumber(pos.unrealizedResultPct) : null;
      const tone: PositionChipViewModel['tone'] =
        pctNumeric === null || Math.abs(pctNumeric) < 0.05 ? 'neutral' : pctNumeric > 0 ? 'positive' : 'negative';

      return {
        currencyCode: code,
        unitsLabel: formatPositionUnits(pos.units, code),
        resultPctLabel: formatSignedPercent(pos.unrealizedResultPct),
        tone,
      };
    }),
  );
}
