import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

/**
 * Selectable chip. `mini` = mock `.mini-chip` (inline options),
 * `category` = mock `.cat-pill` (category grid).
 */
@Component({
  selector: 'fo-chip',
  standalone: true,
  template: `
    <button
      type="button"
      [attr.aria-pressed]="active()"
      [disabled]="disabled()"
      (click)="pressed.emit()"
      class="fo-focus-ring w-full"
      [class.fo-mini-chip]="variant() === 'mini'"
      [class.fo-cat-pill]="variant() === 'category'"
      [class.is-active]="active()"
    >
      <ng-content />
    </button>
  `,
  styles: `
    :host {
      display: inline-block;
    }

    button:disabled {
      opacity: 0.45;
      cursor: not-allowed;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChipComponent {
  readonly variant = input<'mini' | 'category'>('mini');
  readonly active = input<boolean>(false);
  readonly disabled = input<boolean>(false);
  readonly pressed = output<void>();
}
