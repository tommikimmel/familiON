import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Currency flag (mock `.flag-svg`). ARS/USD/EUR are copied from the mock `FLAGS` map
 * (18×12 viewBox rendered at 16×11); other currencies follow the same geometry.
 */
@Component({
  selector: 'fo-currency-flag',
  standalone: true,
  template: `
    <span class="fo-flag" aria-hidden="true">
      @switch (code()) {
        @case ('ARS') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="4" fill="#74ACDF" />
            <rect y="4" width="18" height="4" fill="#FFFFFF" />
            <rect y="8" width="18" height="4" fill="#74ACDF" />
            <circle cx="9" cy="6" r="1.35" fill="#F6B40E" />
          </svg>
        }
        @case ('USD') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="12" fill="#FFFFFF" />
            <rect y="0" width="18" height="1.7" fill="#B22234" />
            <rect y="3.4" width="18" height="1.7" fill="#B22234" />
            <rect y="6.8" width="18" height="1.7" fill="#B22234" />
            <rect y="10.2" width="18" height="1.8" fill="#B22234" />
            <rect width="8" height="6.8" fill="#3C3B6E" />
          </svg>
        }
        @case ('EUR') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="12" fill="#003399" />
            <circle
              cx="9"
              cy="6"
              r="3.4"
              fill="none"
              stroke="#FFCC00"
              stroke-width="1.1"
              stroke-dasharray="0.9 1.75"
            />
          </svg>
        }
        @case ('BRL') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="12" fill="#009B3A" />
            <polygon points="9,1.5 16.5,6 9,10.5 1.5,6" fill="#FFDF00" />
            <circle cx="9" cy="6" r="2.6" fill="#002776" />
          </svg>
        }
        @case ('UYU') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="12" fill="#FFFFFF" />
            <rect y="1.33" width="18" height="1.33" fill="#0038A8" />
            <rect y="4" width="18" height="1.33" fill="#0038A8" />
            <rect y="6.67" width="18" height="1.33" fill="#0038A8" />
            <rect y="9.33" width="18" height="1.33" fill="#0038A8" />
            <rect width="7" height="6.67" fill="#FFFFFF" />
            <circle cx="3.5" cy="3.33" r="1.9" fill="#FCD116" />
          </svg>
        }
        @case ('CLP') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="6" fill="#FFFFFF" />
            <rect y="6" width="18" height="6" fill="#D52B1E" />
            <rect width="6" height="6" fill="#0039A6" />
            <circle cx="3" cy="3" r="1.2" fill="#FFFFFF" />
          </svg>
        }
        @case ('GBP') {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="12" fill="#012169" />
            <path d="M0 0 L18 12 M18 0 L0 12" stroke="#FFFFFF" stroke-width="2.4" />
            <path d="M9 0 V12 M0 6 H18" stroke="#FFFFFF" stroke-width="3.6" />
            <path d="M9 0 V12 M0 6 H18" stroke="#C8102E" stroke-width="2" />
          </svg>
        }
        @default {
          <svg viewBox="0 0 18 12">
            <rect width="18" height="12" fill="var(--fo-accent)" />
            <circle
              cx="9"
              cy="6"
              r="3.6"
              fill="none"
              stroke="var(--fo-accent-contrast)"
              stroke-width="1.1"
            />
          </svg>
        }
      }
    </span>

  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .fo-flag svg {
      display: block;
      width: 16px;
      height: 11px;
      border-radius: 2px;
      box-shadow: 0 0 0 0.5px rgba(0, 0, 0, 0.14);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrencyFlagComponent {
  readonly currency = input.required<string>();

  protected readonly code = computed(() => this.currency().trim().toUpperCase().slice(0, 3));
}
