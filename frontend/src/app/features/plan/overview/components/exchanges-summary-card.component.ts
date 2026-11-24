import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../../../shared/ui';
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
  imports: [IconComponent, PlanSectionCardComponent, RouterLink],
  template: `
    <fo-plan-section-card
      title="Divisas"
      icon="arrow-left-right"
      linkRoute="/plan/exchanges"
      testId="plan-exchanges-card"
      [degraded]="positions() === null"
      [retrying]="retrying()"
      (retry)="retry.emit()"
    >
      @if (positions(); as list) {
        @if (list.length === 0) {
          <div
            data-testid="exchanges-empty-state"
            class="flex flex-1 flex-col items-center justify-center rounded-xl border border-dashed border-border bg-surface-2/40 px-4 py-6 text-center"
          >
            <p class="text-sm font-medium text-text">Sin divisas registradas</p>
            <p class="mt-1 text-xs text-muted">
              Registrá compras o ventas de moneda extranjera y seguí su resultado.
            </p>
            <a
              routerLink="/plan/exchanges"
              data-testid="exchanges-empty-cta"
              class="fo-focus-ring mt-3 inline-flex items-center gap-1.5 rounded-xl bg-accent px-3.5 py-2 text-xs font-semibold text-accent-contrast transition-opacity duration-150 hover:opacity-92"
            >
              <fo-icon name="arrow-left-right" [size]="14" />
              <span>Comprar/Vender</span>
            </a>
          </div>
        } @else {
          <div class="flex flex-1 flex-col justify-between gap-4">
            <ul class="flex flex-wrap gap-2" aria-label="Tenencias por divisa">
              @for (chip of chips(); track chip.currencyCode) {
                <li
                  data-testid="exchange-position-chip"
                  class="flex items-center gap-2 rounded-xl border border-border bg-surface-2/60 px-3 py-2"
                >
                  <span
                    class="inline-flex items-center rounded-md bg-surface px-1.5 py-0.5 text-[11px] font-bold tracking-wide text-text ring-1 ring-border"
                  >
                    {{ chip.currencyCode }}
                  </span>
                  <span class="text-sm font-semibold text-text tabular-nums">
                    {{ chip.unitsLabel }}
                  </span>
                  @if (chip.resultPctLabel; as pct) {
                    <span
                      data-testid="exchange-position-pct"
                      class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums"
                      [class.bg-success/12]="chip.tone === 'positive'"
                      [class.text-success]="chip.tone === 'positive'"
                      [class.bg-danger/12]="chip.tone === 'negative'"
                      [class.text-danger]="chip.tone === 'negative'"
                      [class.bg-surface]="chip.tone === 'neutral'"
                      [class.text-muted]="chip.tone === 'neutral'"
                    >
                      {{ pct }}
                    </span>
                  }
                </li>
              }
            </ul>

            <div>
              <a
                routerLink="/plan/exchanges"
                data-testid="exchanges-trade-cta"
                class="fo-focus-ring inline-flex w-full items-center justify-center gap-2 rounded-xl bg-accent px-4 py-2.5 text-sm font-semibold text-accent-contrast transition-opacity duration-150 hover:opacity-92"
              >
                <fo-icon name="arrow-left-right" [size]="16" />
                <span>Comprar/Vender</span>
              </a>
            </div>
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
export class ExchangesSummaryCardComponent {
  readonly positions = input<PlanPositionItem[] | null>(null);
  readonly retrying = input<boolean>(false);

  readonly retry = output<void>();

  protected readonly chips = computed<PositionChipViewModel[]>(() =>
    (this.positions() ?? []).map((pos) => {
      const code = pos.currencyCode.trim().toUpperCase();
      const pctNumeric = pos.unrealizedResultPct !== null ? toNumber(pos.unrealizedResultPct) : null;
      const tone: PositionChipViewModel['tone'] =
        pctNumeric === null || Math.abs(pctNumeric) < 0.05
          ? 'neutral'
          : pctNumeric > 0
            ? 'positive'
            : 'negative';

      return {
        currencyCode: code,
        unitsLabel: formatPositionUnits(pos.units, code),
        resultPctLabel: formatSignedPercent(pos.unrealizedResultPct),
        tone,
      };
    }),
  );
}
