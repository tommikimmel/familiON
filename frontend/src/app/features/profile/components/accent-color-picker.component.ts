import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  input,
  output,
  viewChild,
} from '@angular/core';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { LogoComponent } from '../../../shared/ui/logo/logo.component';
import { readableTextOn } from '../../../shared/utils/color';
import { ACCENT_PRESETS, AccentPreset, normalizeHexColor } from '../data-access/profile.models';

@Component({
  selector: 'fo-accent-color-picker',
  standalone: true,
  imports: [IconComponent, LogoComponent],
  template: `
    <div class="space-y-4">
      <div
        role="group"
        aria-label="Color de perfil"
        class="flex flex-wrap items-center gap-3"
      >
        @for (preset of presets(); track preset.hex) {
          @let selected = isPresetSelected(preset.hex);
          <button
            type="button"
            data-testid="accent-preset-btn"
            [attr.data-color]="preset.hex"
            [attr.aria-label]="'Color ' + preset.label"
            [attr.aria-pressed]="selected"
            [title]="preset.label"
            (click)="selectColor(preset.hex)"
            class="fo-focus-ring relative inline-flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full shadow-2xs transition-transform duration-150 hover:scale-105 active:scale-95"
            [class.ring-2]="selected"
            [class.ring-offset-2]="selected"
            [class.ring-offset-surface]="selected"
            [class.ring-text]="selected"
            [style.background-color]="preset.hex"
            [style.color]="contrastFor(preset.hex)"
          >
            @if (selected) {
              <fo-icon name="check" [size]="18" [strokeWidth]="2.5" />
            }
          </button>
        }

        <div class="relative inline-flex">
          <button
            type="button"
            data-testid="accent-custom-btn"
            aria-label="Elegir color personalizado"
            [attr.aria-pressed]="isCustomSelected()"
            title="Color personalizado"
            (click)="openNativePicker()"
            class="fo-focus-ring inline-flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full border-2 transition-transform duration-150 hover:scale-105 active:scale-95"
            [class.border-dashed]="!isCustomSelected()"
            [class.border-border]="!isCustomSelected()"
            [class.bg-surface-2]="!isCustomSelected()"
            [class.text-text]="!isCustomSelected()"
            [class.border-transparent]="isCustomSelected()"
            [class.ring-2]="isCustomSelected()"
            [class.ring-offset-2]="isCustomSelected()"
            [class.ring-offset-surface]="isCustomSelected()"
            [class.ring-text]="isCustomSelected()"
            [style.background-color]="isCustomSelected() ? normalizedSelected() : null"
            [style.color]="isCustomSelected() ? contrastFor(normalizedSelected()) : null"
          >
            <fo-icon name="plus" [size]="18" [strokeWidth]="2.5" />
          </button>

          <input
            #nativeColorInput
            type="color"
            data-testid="accent-native-input"
            aria-label="Selector de color personalizado"
            tabindex="-1"
            [value]="normalizedSelected()"
            (input)="onNativeColorChange($event)"
            (change)="onNativeColorChange($event)"
            class="sr-only"
          />
        </div>
      </div>

      <div
        class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-surface-2/70 px-4 py-3"
      >
        <div class="space-y-0.5">
          <p class="text-xs font-medium text-muted">Vista previa</p>
          <p class="font-mono text-xs text-text tabular-nums">{{ normalizedSelected() }}</p>
        </div>

        <fo-logo data-testid="accent-logo-preview" />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccentColorPickerComponent {
  readonly selectedColor = input.required<string>();
  readonly presets = input<readonly AccentPreset[]>(ACCENT_PRESETS);

  readonly colorSelected = output<string>();

  private readonly nativeColorInput = viewChild<ElementRef<HTMLInputElement>>('nativeColorInput');

  protected readonly normalizedSelected = computed(() => normalizeHexColor(this.selectedColor()));

  protected readonly isCustomSelected = computed(() => {
    const current = this.normalizedSelected();
    return !this.presets().some((preset) => preset.hex.toUpperCase() === current);
  });

  protected isPresetSelected(hex: string): boolean {
    return this.normalizedSelected() === hex.toUpperCase();
  }

  protected contrastFor(hex: string): string {
    return readableTextOn(hex);
  }

  protected selectColor(hex: string): void {
    this.colorSelected.emit(normalizeHexColor(hex));
  }

  protected openNativePicker(): void {
    this.nativeColorInput()?.nativeElement.click();
  }

  protected onNativeColorChange(event: Event): void {
    const inputEl = event.target as HTMLInputElement | null;
    if (!inputEl?.value) {
      return;
    }
    this.colorSelected.emit(normalizeHexColor(inputEl.value));
  }
}
