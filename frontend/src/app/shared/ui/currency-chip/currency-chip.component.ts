import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { CurrencyFlagComponent } from './currency-flag.component';

/** Currency pill (mock `.currency-pill` + `.flag-svg`). */
@Component({
  selector: 'fo-currency-chip',
  standalone: true,
  imports: [CurrencyFlagComponent, IconComponent],
  template: `
    <button
      type="button"
      [attr.aria-expanded]="interactive() ? expanded() : null"
      [attr.aria-haspopup]="interactive() ? 'menu' : null"
      [attr.aria-label]="'Divisa activa: ' + code() + (adjusted() ? ', a plata de hoy' : '')"
      [disabled]="!interactive()"
      (click)="pressed.emit()"
      class="fo-currency-pill fo-focus-ring"
      [class.is-open]="expanded()"
      [class.is-interactive]="interactive()"
    >
      <fo-currency-flag [currency]="code()" />

      <span>{{ code() }}</span>

      @if (adjusted()) {
        <span class="fo-adjusted-dot" title="A plata de hoy" aria-hidden="true"></span>
      }

      @if (interactive()) {
        <fo-icon name="chevron-down" [size]="10" [strokeWidth]="2.6" class="fo-chev" />
      }
    </button>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .fo-currency-pill {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 5px 9px;
      border-radius: 999px;
      border: 1px solid var(--fo-border);
      background: var(--fo-surface);
      color: var(--fo-text);
      font-family: inherit;
      font-size: 11px;
      font-weight: 700;
      line-height: 1.45;
      box-shadow: var(--fo-shadow);
      transition:
        transform 0.14s var(--fo-ease-spring),
        border-color 0.18s ease;
    }

    .fo-currency-pill.is-interactive {
      cursor: pointer;
    }

    .fo-currency-pill.is-interactive:hover {
      border-color: var(--fo-accent);
    }

    .fo-currency-pill.is-interactive:active {
      transform: scale(0.94);
    }

    .fo-adjusted-dot {
      width: 6px;
      height: 6px;
      border-radius: 999px;
      background: var(--fo-accent);
    }

    .fo-chev {
      transition: transform 0.22s var(--fo-ease-smooth);
    }

    .is-open .fo-chev {
      transform: rotate(180deg);
    }

    @media (min-width: 1024px) {
      .fo-currency-pill {
        gap: 6px;
        padding: 5px 10px;
        font-size: 11.5px;
        box-shadow: none;
        transition:
          border-color 0.15s ease,
          transform 0.12s ease;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrencyChipComponent {
  readonly currency = input.required<string>();
  readonly adjusted = input<boolean>(false);
  readonly expanded = input<boolean>(false);
  readonly interactive = input<boolean>(true);
  readonly pressed = output<void>();

  protected readonly code = computed(() => this.currency().trim().toUpperCase().slice(0, 3));
}
