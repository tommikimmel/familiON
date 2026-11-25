import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CurrencyFlagComponent, IconComponent } from '../../../shared/ui';

/** Flag + code currency pill with its dropdown (mock `.currency-pill` / `.currency-dropdown`). */
@Component({
  selector: 'fo-quick-add-currency-chip',
  standalone: true,
  imports: [CurrencyFlagComponent, IconComponent],
  template: `
    <div class="relative shrink-0">
      <button
        type="button"
        data-testid="quick-add-currency-chip"
        [attr.data-prefilled]="prefilled() ? 'true' : null"
        [disabled]="currencies().length <= 1"
        [attr.aria-expanded]="currencies().length > 1 ? open() : null"
        aria-label="Divisa del movimiento"
        (click)="toggleMenu.emit()"
        class="currency-pill fo-focus-ring"
        [class.is-prefilled]="prefilled()"
      >
        <fo-currency-flag [currency]="currencyCode()" />
        <span>{{ currencyCode() }}</span>
      </button>

      @if (open() && currencies().length > 1) {
        <div
          role="listbox"
          aria-label="Elegir divisa"
          data-testid="quick-add-currency-menu"
          class="currency-menu"
        >
          @for (code of currencies(); track code) {
            <button
              type="button"
              role="option"
              [attr.aria-selected]="code === currencyCode()"
              [attr.data-testid]="'quick-add-currency-option-' + code"
              (click)="selectCurrency.emit(code)"
              class="currency-opt fo-focus-ring"
              [class.is-active]="code === currencyCode()"
            >
              <span class="flex items-center gap-1.5">
                <fo-currency-flag [currency]="code" />
                {{ code }}
              </span>
              @if (code === currencyCode()) {
                <fo-icon name="check" [size]="12" />
              }
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .currency-pill {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 8px;
      border: 1px solid var(--fo-border);
      border-radius: 999px;
      background: var(--fo-surface);
      color: var(--fo-text);
      font-family: inherit;
      font-size: 11px;
      font-weight: 700;
      cursor: pointer;
    }
    .currency-pill:hover:not(:disabled),
    .currency-pill.is-prefilled {
      border-color: var(--fo-accent);
    }
    .currency-pill:disabled {
      cursor: default;
    }
    .currency-menu {
      position: absolute;
      left: 0;
      z-index: 20;
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 112px;
      margin-top: 6px;
      padding: 6px;
      border: 1px solid var(--fo-border);
      border-radius: 14px;
      background: var(--fo-surface);
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.18);
    }
    .currency-opt {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 7px 8px;
      border: none;
      border-radius: 9px;
      background: transparent;
      color: var(--fo-text);
      font-family: inherit;
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
    }
    .currency-opt:hover {
      background: var(--fo-surface-2);
    }
    .currency-opt.is-active {
      background: var(--fo-accent-soft);
      color: var(--fo-accent);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuickAddCurrencyChipComponent {
  readonly currencyCode = input.required<string>();
  readonly currencies = input.required<readonly string[]>();
  readonly open = input<boolean>(false);
  readonly prefilled = input<boolean>(false);
  readonly toggleMenu = output<void>();
  readonly selectCurrency = output<string>();
}
