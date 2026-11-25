import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  signal,
  untracked,
  viewChildren,
} from '@angular/core';

const OTP_LENGTH = 6;
const OTP_INDICES = [0, 1, 2, 3, 4, 5] as const;

@Component({
  selector: 'fo-otp-code-input',
  standalone: true,
  template: `
    <fieldset class="space-y-1.5 border-0 p-0" [disabled]="disabled()">
      <legend class="fo-auth-label">
        {{ label() }}
      </legend>

      <div
        class="grid grid-cols-6 gap-2"
        role="group"
        [attr.aria-label]="label()"
      >
        @for (index of indices; track index) {
          <input
            #digitInput
            type="text"
            inputmode="numeric"
            pattern="[0-9]*"
            [attr.autocomplete]="index === 0 ? 'one-time-code' : 'off'"
            [attr.aria-label]="'Dígito ' + (index + 1) + ' de ' + length"
            [attr.aria-invalid]="hasError() ? 'true' : null"
            [attr.aria-describedby]="error() ? errorId() : null"
            [value]="digits()[index]"
            [disabled]="disabled()"
            (input)="onInput(index, $event)"
            (keydown)="onKeydown(index, $event)"
            (paste)="onPaste(index, $event)"
            (focus)="onFocus($event)"
            class="fo-auth-input fo-auth-otp"
          />
        }
      </div>

      @if (error(); as errorMsg) {
        <p [id]="errorId()" role="alert" class="fo-auth-error">
          {{ errorMsg }}
        </p>
      }
    </fieldset>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OtpCodeInputComponent {
  readonly value = input<string>('');
  readonly label = input<string>('Código de 6 dígitos');
  readonly inputIdPrefix = input<string>('otp-code');
  readonly disabled = input<boolean>(false);
  readonly invalid = input<boolean>(false);
  readonly error = input<string | null>(null);

  readonly codeChange = output<string>();
  readonly completed = output<string>();

  protected readonly length = OTP_LENGTH;
  protected readonly indices = OTP_INDICES;
  protected readonly digits = signal<string[]>(Array.from({ length: OTP_LENGTH }, () => ''));

  protected readonly hasError = computed(() => this.invalid() || Boolean(this.error()));
  protected readonly errorId = computed(() => `${this.inputIdPrefix()}-error`);

  private readonly digitInputs = viewChildren<ElementRef<HTMLInputElement>>('digitInput');

  constructor() {
    effect(() => {
      const normalized = this.value().replace(/\D/g, '').slice(0, OTP_LENGTH);
      const next = Array.from({ length: OTP_LENGTH }, (_, idx) => normalized[idx] ?? '');
      const current = untracked(() => this.digits());
      if (next.join('') !== current.join('')) {
        this.digits.set(next);
      }
    });
  }

  protected onFocus(event: FocusEvent): void {
    (event.target as HTMLInputElement).select();
  }

  protected onInput(index: number, event: Event): void {
    const inputEl = event.target as HTMLInputElement;
    const rawDigits = inputEl.value.replace(/\D/g, '');

    if (rawDigits.length === 0) {
      this.updateDigitAt(index, '');
      inputEl.value = '';
      return;
    }

    if (rawDigits.length > 1) {
      this.applyDigits(index, rawDigits);
      return;
    }

    const digit = rawDigits[0];
    this.updateDigitAt(index, digit);
    inputEl.value = digit;

    if (index < OTP_LENGTH - 1) {
      this.focusBox(index + 1);
    }
  }

  protected onKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace') {
      const current = this.digits();
      if (current[index]) {
        event.preventDefault();
        this.updateDigitAt(index, '');
      } else if (index > 0) {
        event.preventDefault();
        this.updateDigitAt(index - 1, '');
        this.focusBox(index - 1);
      }
      return;
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      this.focusBox(index - 1);
      return;
    }

    if (event.key === 'ArrowRight' && index < OTP_LENGTH - 1) {
      event.preventDefault();
      this.focusBox(index + 1);
    }
  }

  protected onPaste(index: number, event: ClipboardEvent): void {
    const pasted = event.clipboardData?.getData('text') ?? '';
    const rawDigits = pasted.replace(/\D/g, '');
    if (!rawDigits) {
      return;
    }

    event.preventDefault();
    const startIndex = rawDigits.length >= OTP_LENGTH ? 0 : index;
    this.applyDigits(startIndex, rawDigits);
  }

  private applyDigits(startIndex: number, rawDigits: string): void {
    const next = [...this.digits()];
    const chars = rawDigits.slice(0, OTP_LENGTH - startIndex);
    for (let offset = 0; offset < chars.length; offset++) {
      next[startIndex + offset] = chars[offset];
    }

    this.commitDigits(next);

    const lastFilled = Math.min(startIndex + chars.length, OTP_LENGTH - 1);
    this.focusBox(lastFilled);
  }

  private updateDigitAt(index: number, digit: string): void {
    const next = [...this.digits()];
    next[index] = digit;
    this.commitDigits(next);
  }

  private commitDigits(next: string[]): void {
    this.digits.set(next);
    const inputs = this.digitInputs();
    for (let i = 0; i < inputs.length; i++) {
      const el = inputs[i].nativeElement;
      if (el.value !== next[i]) {
        el.value = next[i];
      }
    }

    const joined = next.join('');
    this.codeChange.emit(joined);
    if (joined.length === OTP_LENGTH && next.every((d) => d.length === 1)) {
      this.completed.emit(joined);
    }
  }

  private focusBox(index: number): void {
    const target = this.digitInputs()[index]?.nativeElement;
    if (target) {
      target.focus();
      target.select();
    }
  }
}
