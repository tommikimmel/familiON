import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'ghost' | 'danger';
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
      class="fo-focus-ring inline-flex items-center justify-center gap-2 rounded-xl font-medium transition-all duration-150 active:scale-[0.98] disabled:pointer-events-none disabled:opacity-55"
      [class.w-full]="fullWidth()"
      [class.cursor-pointer]="!isDisabled()"
      [class.px-3]="size() === 'sm'"
      [class.py-1.5]="size() === 'sm'"
      [class.text-xs]="size() === 'sm'"
      [class.px-4]="size() === 'md'"
      [class.py-2.5]="size() === 'md'"
      [class.text-sm]="size() === 'md'"
      [class.px-5]="size() === 'lg'"
      [class.py-3]="size() === 'lg'"
      [class.text-base]="size() === 'lg'"
      [class.bg-accent]="variant() === 'primary'"
      [class.text-accent-contrast]="variant() === 'primary'"
      [class.hover:opacity-92]="variant() === 'primary'"
      [class.bg-transparent]="variant() === 'ghost'"
      [class.text-text]="variant() === 'ghost'"
      [class.hover:bg-surface-2]="variant() === 'ghost'"
      [class.bg-danger]="variant() === 'danger'"
      [class.text-white]="variant() === 'danger'"
      [class.hover:opacity-92]="variant() === 'danger'"
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
