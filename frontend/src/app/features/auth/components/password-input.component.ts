import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from '../../../shared/ui/icon/icon.component';

@Component({
  selector: 'fo-password-input',
  standalone: true,
  imports: [IconComponent],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PasswordInputComponent),
      multi: true,
    },
  ],
  template: `
    <div class="space-y-1.5">
      <label [for]="inputId()" class="block text-sm font-medium text-text">
        {{ label() }}
      </label>

      <div class="relative">
        <input
          [id]="inputId()"
          [type]="visible() ? 'text' : 'password'"
          [value]="value()"
          [disabled]="isDisabled()"
          [placeholder]="placeholder()"
          [attr.autocomplete]="autocomplete()"
          [attr.aria-invalid]="error() ? 'true' : null"
          [attr.aria-describedby]="describedById()"
          (input)="handleInput($event)"
          (blur)="handleBlur()"
          class="fo-focus-ring block w-full rounded-xl border bg-surface px-3.5 py-2.5 pr-11 text-sm text-text placeholder:text-muted/70 transition-colors disabled:opacity-60"
          [class.border-border]="!error()"
          [class.border-danger]="!!error()"
        />

        <button
          type="button"
          [disabled]="isDisabled()"
          [attr.aria-label]="visible() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
          [attr.aria-pressed]="visible()"
          (click)="toggleVisible()"
          class="fo-focus-ring absolute inset-y-0 right-1.5 my-auto inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text disabled:pointer-events-none disabled:opacity-50"
        >
          <fo-icon [name]="visible() ? 'eye-off' : 'eye'" [size]="18" />
        </button>
      </div>

      @if (error(); as errorMsg) {
        <p [id]="errorId()" role="alert" class="text-xs font-medium text-danger">
          {{ errorMsg }}
        </p>
      } @else if (hint(); as hintMsg) {
        <p [id]="hintId()" class="text-xs text-muted">
          {{ hintMsg }}
        </p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordInputComponent implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly label = input.required<string>();
  readonly autocomplete = input<'current-password' | 'new-password'>('current-password');
  readonly placeholder = input<string>('');
  readonly error = input<string | null>(null);
  readonly hint = input<string | null>(null);

  protected readonly value = signal<string>('');
  protected readonly visible = signal<boolean>(false);
  protected readonly isDisabled = signal<boolean>(false);
  protected readonly errorId = computed(() => `${this.inputId()}-error`);
  protected readonly hintId = computed(() => `${this.inputId()}-hint`);
  protected readonly describedById = computed(() => {
    if (this.error()) {
      return this.errorId();
    }
    if (this.hint()) {
      return this.hintId();
    }
    return null;
  });

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.isDisabled.set(isDisabled);
  }

  protected toggleVisible(): void {
    this.visible.update((current) => !current);
  }

  protected handleInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChange(next);
  }

  protected handleBlur(): void {
    this.onTouched();
  }
}
