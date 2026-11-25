import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** `primary` = mock `.btn-primary`, `soft` = mock `.btn-soft`. */
export type ButtonVariant = 'primary' | 'soft' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'fo-button',
  standalone: true,
  template: `
    <button
      [attr.type]="type()"
      [disabled]="isDisabled()"
      [attr.aria-busy]="loading() || null"
      [attr.aria-label]="ariaLabel()"
      class="fo-btn fo-focus-ring"
      [class.w-full]="fullWidth()"
      [class.fo-btn--sm]="size() === 'sm'"
      [class.fo-btn--lg]="size() === 'lg'"
      [class.fo-btn--primary]="variant() === 'primary'"
      [class.fo-btn--soft]="variant() === 'soft'"
      [class.fo-btn--ghost]="variant() === 'ghost'"
      [class.fo-btn--danger]="variant() === 'danger'"
    >
      @if (loading()) {
        <svg
          class="h-4 w-4 animate-spin"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
        >
          <circle
            cx="12"
            cy="12"
            r="9"
            stroke="currentColor"
            stroke-opacity="0.25"
            stroke-width="3"
          />
          <path
            d="M21 12a9 9 0 0 0-9-9"
            stroke="currentColor"
            stroke-width="3"
            stroke-linecap="round"
          />
        </svg>
      }
      <ng-content />
    </button>
  `,
  styles: `
    :host {
      display: inline-block;
    }

    :host([fullWidth='true']),
    :host(.w-full) {
      display: block;
    }

    .fo-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      border: 1px solid transparent;
      font-family: inherit;
      font-size: 12px;
      font-weight: 600;
      padding: 10px 12px;
      border-radius: 12px;
      cursor: pointer;
      transition:
        transform 0.14s var(--fo-ease-spring),
        filter 0.15s ease,
        border-color 0.15s ease,
        background 0.15s ease;
    }

    .fo-btn:active:not(:disabled) {
      transform: scale(0.96);
    }

    .fo-btn:disabled {
      opacity: 0.38;
      cursor: not-allowed;
      pointer-events: none;
    }

    .fo-btn[aria-busy='true'] {
      opacity: 0.75;
    }

    .fo-btn--sm {
      font-size: 11px;
      padding: 6px 10px;
      border-radius: 9px;
    }

    .fo-btn--lg {
      font-size: 13.5px;
      font-weight: 700;
      padding: 12px;
      border-radius: 13px;
    }

    .fo-btn--primary {
      background: var(--fo-accent);
      color: var(--fo-accent-contrast);
    }

    .fo-btn--primary:hover {
      filter: brightness(1.05);
    }

    .fo-btn--soft {
      background: var(--fo-surface);
      border-color: var(--fo-border);
      color: var(--fo-text);
    }

    .fo-btn--soft:hover {
      border-color: var(--fo-accent);
    }

    .fo-btn--ghost {
      background: transparent;
      color: var(--fo-text);
    }

    .fo-btn--ghost:hover {
      background: var(--fo-surface-2);
    }

    .fo-btn--danger {
      background: var(--fo-danger);
      color: #ffffff;
    }

    .fo-btn--danger:hover {
      filter: brightness(1.05);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly fullWidth = input<boolean>(false);
  readonly ariaLabel = input<string | null>(null);

  protected readonly isDisabled = computed(() => this.disabled() || this.loading());
}
