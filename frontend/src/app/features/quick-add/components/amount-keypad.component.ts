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
    <div class="space-y-2" role="group" aria-label="Teclado numérico">
      <div class="grid grid-cols-3 gap-2">
        @for (key of keys; track key) {
          <button
            type="button"
            [disabled]="disabled()"
            [attr.data-testid]="'keypad-key-' + key"
            [attr.aria-label]="ariaLabelFor(key)"
            (click)="press(key)"
            class="fo-focus-ring flex h-12 cursor-pointer items-center justify-center rounded-xl border border-border bg-surface-2 text-lg font-semibold text-text tabular-nums transition-transform duration-100 select-none hover:bg-border/40 active:scale-95 disabled:pointer-events-none disabled:opacity-50"
          >
            {{ key }}
          </button>
        }
      </div>

      <button
        type="button"
        [disabled]="disabled() || !canDelete()"
        data-testid="keypad-backspace"
        aria-label="Borrar"
        (click)="press('BACKSPACE')"
        class="fo-focus-ring flex h-10 w-full cursor-pointer items-center justify-center gap-1.5 rounded-xl border border-border bg-surface text-sm font-medium text-muted transition-colors hover:bg-surface-2 hover:text-text active:scale-[0.98] disabled:pointer-events-none disabled:opacity-45"
      >
        <span aria-hidden="true">⌫</span>
        <span>Borrar</span>
      </button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AmountKeypadComponent {
  readonly disabled = input<boolean>(false);
  readonly canDelete = input<boolean>(true);
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
