import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { KeypadKey } from '../amount-input';

const KEYPAD_KEYS: readonly KeypadKey[] = [
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  ',',
  '0',
  '000',
];

@Component({
  selector: 'fo-amount-keypad',
  standalone: true,
  template: `
    <div class="keypad" role="group" aria-label="Teclado numérico">
      @for (key of keys; track key) {
        <button
          type="button"
          [disabled]="disabled()"
          [attr.data-testid]="'keypad-key-' + key"
          [attr.aria-label]="ariaLabelFor(key)"
          (click)="press(key)"
          class="key-btn fo-focus-ring"
        >
          {{ key }}
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .keypad {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 6px;
    }
    .key-btn {
      display: grid;
      place-items: center;
      height: 40px;
      border: none;
      border-radius: 11px;
      background: var(--fo-surface-2);
      color: var(--fo-text);
      font-family: inherit;
      font-size: 16px;
      font-weight: 600;
      font-variant-numeric: tabular-nums;
      cursor: pointer;
      user-select: none;
      transition:
        transform 0.1s var(--fo-ease-spring),
        background 0.12s ease;
    }
    .key-btn:active:not(:disabled) {
      transform: scale(0.92);
      background: var(--fo-accent-soft);
      color: var(--fo-accent);
    }
    .key-btn:disabled {
      opacity: 0.5;
      pointer-events: none;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AmountKeypadComponent {
  readonly disabled = input<boolean>(false);
  readonly keyPress = output<KeypadKey>();

  protected readonly keys = KEYPAD_KEYS;

  protected press(key: KeypadKey): void {
    this.keyPress.emit(key);
  }

  protected ariaLabelFor(key: KeypadKey): string {
    if (key === ',') {
      return 'Coma decimal';
    }
    if (key === '000') {
      return 'Agregar tres ceros';
    }
    return key;
  }
}
