import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'fo-currency-chip',
  standalone: true,
  imports: [IconComponent],
  template: `
    <button
      type="button"
      [attr.aria-expanded]="interactive() ? expanded() : null"
      [attr.aria-haspopup]="interactive() ? 'menu' : null"
      [attr.aria-label]="'Divisa activa: ' + code() + (adjusted() ? ', a plata de hoy' : '')"
      [disabled]="!interactive()"
      (click)="pressed.emit()"
      class="fo-focus-ring inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-2.5 py-1 text-xs font-semibold text-text shadow-2xs transition-colors duration-150"
      [class.cursor-pointer]="interactive()"
      [class.hover:bg-surface-2]="interactive()"
    >
      <span class="relative inline-flex h-4 w-4 shrink-0 overflow-hidden rounded-full ring-1 ring-black/10" aria-hidden="true">
        @switch (code()) {
          @case ('ARS') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="8" y="0" fill="#74ACDF" />
              <rect width="24" height="8" y="8" fill="#FFFFFF" />
              <rect width="24" height="8" y="16" fill="#74ACDF" />
              <circle cx="12" cy="12" r="2.6" fill="#F6B40E" />
            </svg>
          }
          @case ('USD') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="24" fill="#FFFFFF" />
              <rect width="24" height="3" y="0" fill="#B22234" />
              <rect width="24" height="3" y="6" fill="#B22234" />
              <rect width="24" height="3" y="12" fill="#B22234" />
              <rect width="24" height="3" y="18" fill="#B22234" />
              <rect width="12" height="12" x="0" y="0" fill="#3C3B6E" />
            </svg>
          }
          @case ('EUR') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="24" fill="#003399" />
              <circle cx="12" cy="12" r="6" fill="none" stroke="#FFCC00" stroke-width="2" stroke-dasharray="1.8 2.9" />
            </svg>
          }
          @case ('BRL') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="24" fill="#009B3A" />
              <polygon points="12,3 21,12 12,21 3,12" fill="#FFDF00" />
              <circle cx="12" cy="12" r="4" fill="#002776" />
            </svg>
          }
          @case ('UYU') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="24" fill="#FFFFFF" />
              <rect width="24" height="3" y="4" fill="#0038A8" />
              <rect width="24" height="3" y="10" fill="#0038A8" />
              <rect width="24" height="3" y="16" fill="#0038A8" />
              <rect width="10" height="10" fill="#FFFFFF" />
              <circle cx="5" cy="5" r="2.8" fill="#FCD116" />
            </svg>
          }
          @case ('CLP') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="12" y="0" fill="#FFFFFF" />
              <rect width="24" height="12" y="12" fill="#D52B1E" />
              <rect width="10" height="12" fill="#0039A6" />
              <circle cx="5" cy="6" r="2" fill="#FFFFFF" />
            </svg>
          }
          @case ('GBP') {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="24" fill="#012169" />
              <path d="M0 0 L24 24 M24 0 L0 24" stroke="#FFFFFF" stroke-width="4" />
              <path d="M12 0 V24 M0 12 H24" stroke="#FFFFFF" stroke-width="6" />
              <path d="M12 0 V24 M0 12 H24" stroke="#C8102E" stroke-width="3.2" />
            </svg>
          }
          @default {
            <svg viewBox="0 0 24 24" class="h-full w-full">
              <rect width="24" height="24" fill="var(--fo-accent)" />
              <circle cx="12" cy="12" r="6" fill="none" stroke="var(--fo-accent-contrast)" stroke-width="1.8" />
              <path d="M6 12 H18 M12 6 C14.5 9 14.5 15 12 18 C9.5 15 9.5 9 12 6 Z" fill="none" stroke="var(--fo-accent-contrast)" stroke-width="1.5" />
            </svg>
          }
        }
      </span>

      <span class="tracking-wide">{{ code() }}</span>

      @if (adjusted()) {
        <span
          class="h-1.5 w-1.5 rounded-full bg-accent"
          title="A plata de hoy"
          aria-hidden="true"
        ></span>
      }

      @if (interactive()) {
        <fo-icon
          name="chevron-down"
          [size]="14"
          class="text-muted transition-transform duration-150"
          [class.rotate-180]="expanded()"
        />
      }
    </button>
  `,
  styles: `
    :host {
      display: inline-flex;
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
