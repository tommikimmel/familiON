import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  Category,
  CategoryCreate,
  CategoryKind,
  CategoryUpdate,
} from '../../../core/finance/finance.models';
import {
  ButtonComponent,
  IconComponent,
  SegmentedOption,
  SegmentedToggleComponent,
} from '../../../shared/ui';
import {
  CATEGORY_COLOR_OPTIONS,
  CATEGORY_ICON_OPTIONS,
  DEFAULT_CATEGORY_COLOR,
  DEFAULT_CATEGORY_ICON,
} from '../utils/category-picker-options';

export type CategoryFormSubmit =
  | { mode: 'create'; payload: CategoryCreate }
  | { mode: 'edit'; id: string; payload: CategoryUpdate };

const KIND_OPTIONS: readonly SegmentedOption<CategoryKind>[] = [
  { value: 'EXPENSE', label: 'Gasto' },
  { value: 'INCOME', label: 'Ingreso' },
];

function trimmedRequiredValidator(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value.length > 0 ? null : { required: true };
}

@Component({
  selector: 'fo-category-form',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonComponent, IconComponent, SegmentedToggleComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4" novalidate>
      @if (errorMessage(); as error) {
        <div
          role="alert"
          class="rounded-xl border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger"
        >
          {{ error }}
        </div>
      }

      <div class="flex items-center gap-3 rounded-xl border border-border bg-surface-2/60 p-3">
        <span
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl"
          [style.background-color]="previewBg()"
          [style.color]="form.controls.colorHex.value"
          aria-hidden="true"
        >
          <fo-icon [name]="form.controls.icon.value || 'circle-ellipsis'" [size]="20" />
        </span>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-text">
            {{ form.controls.name.value.trim() || 'Vista previa' }}
          </p>
          <p class="text-xs text-muted">
            {{ form.controls.kind.value === 'EXPENSE' ? 'Gasto' : 'Ingreso' }}
          </p>
        </div>
      </div>

      <div class="space-y-1.5">
        <label for="fo-category-name" class="block text-sm font-medium text-text">
          Nombre de la categoría
        </label>
        <input
          id="fo-category-name"
          type="text"
          formControlName="name"
          maxlength="60"
          placeholder="Ej. Gimnasio, Mascotas, Freelance"
          [attr.aria-invalid]="isNameInvalid()"
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text placeholder:text-muted"
        />
        @if (isNameInvalid()) {
          <p role="alert" class="text-xs text-danger">Ingresá un nombre para la categoría.</p>
        }
      </div>

      @if (!category()) {
        <div class="space-y-1.5">
          <span class="block text-sm font-medium text-text">Tipo</span>
          <fo-segmented-toggle
            [options]="kindOptions"
            [value]="form.controls.kind.value"
            (valueChange)="onKindChange($event)"
            ariaLabel="Tipo de categoría"
          />
        </div>

        <div class="space-y-1.5">
          <label for="fo-category-parent" class="block text-sm font-medium text-text">
            Categoría padre (opcional)
          </label>
          <select
            id="fo-category-parent"
            formControlName="parentCategoryId"
            class="fo-focus-ring w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-text"
          >
            <option value="">Sin categoría padre (raíz)</option>
            @for (root of availableRoots(); track root.id) {
              <option [value]="root.id">{{ root.name }}</option>
            }
          </select>
        </div>
      }

      <fieldset class="space-y-2">
        <legend class="text-sm font-medium text-text">Color</legend>
        <div class="flex flex-wrap gap-2" role="group" aria-label="Color de la categoría">
          @for (swatch of colorOptions; track swatch.hex) {
            @let selected = form.controls.colorHex.value === swatch.hex;
            <button
              type="button"
              [attr.aria-label]="'Color ' + swatch.label"
              [attr.aria-pressed]="selected"
              [title]="swatch.label"
              (click)="form.controls.colorHex.setValue(swatch.hex)"
              class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full border-2 transition-transform active:scale-95"
              [style.background-color]="swatch.hex"
              [class.border-text]="selected"
              [class.border-transparent]="!selected"
            >
              @if (selected) {
                <fo-icon name="check" [size]="15" class="text-white" />
              }
            </button>
          }
        </div>
      </fieldset>

      <fieldset class="space-y-2">
        <legend class="text-sm font-medium text-text">Ícono</legend>
        <div
          class="grid grid-cols-5 gap-2"
          role="group"
          aria-label="Ícono de la categoría"
          data-testid="category-icon-picker"
        >
          @for (option of iconOptions; track option.name) {
            @let selected = form.controls.icon.value === option.name;
            <button
              type="button"
              [attr.aria-label]="'Ícono ' + option.label"
              [attr.aria-pressed]="selected"
              [title]="option.label"
              (click)="form.controls.icon.setValue(option.name)"
              class="fo-focus-ring flex h-10 cursor-pointer items-center justify-center rounded-xl border transition-colors"
              [class.border-accent]="selected"
              [class.bg-accent-soft]="selected"
              [class.text-accent]="selected"
              [class.border-border]="!selected"
              [class.bg-surface]="!selected"
              [class.text-muted]="!selected"
              [class.hover:bg-surface-2]="!selected"
              [class.hover:text-text]="!selected"
            >
              <fo-icon [name]="option.name" [size]="18" />
            </button>
          }
        </div>
      </fieldset>

      <div class="flex items-center justify-end gap-2 pt-2">
        <fo-button variant="ghost" type="button" [disabled]="saving()" (click)="cancelled.emit()">
          Cancelar
        </fo-button>
        <fo-button variant="primary" type="submit" [loading]="saving()">
          {{ category() ? 'Guardar cambios' : 'Crear categoría' }}
        </fo-button>
      </div>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryFormComponent {
  private readonly fb = inject(FormBuilder);

  readonly category = input<Category | null>(null);
  readonly initialKind = input<CategoryKind>('EXPENSE');
  readonly initialParentId = input<string | null>(null);
  readonly expenseRoots = input<readonly Category[]>([]);
  readonly incomeRoots = input<readonly Category[]>([]);
  readonly saving = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly submitted = output<CategoryFormSubmit>();
  readonly cancelled = output<void>();

  protected readonly kindOptions = KIND_OPTIONS;
  protected readonly iconOptions = CATEGORY_ICON_OPTIONS;
  protected readonly colorOptions = CATEGORY_COLOR_OPTIONS;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, trimmedRequiredValidator, Validators.maxLength(60)]],
    kind: this.fb.nonNullable.control<CategoryKind>('EXPENSE', [Validators.required]),
    parentCategoryId: [''],
    icon: [DEFAULT_CATEGORY_ICON as string],
    colorHex: [DEFAULT_CATEGORY_COLOR],
  });

  protected readonly availableRoots = computed(() =>
    this.form.controls.kind.value === 'EXPENSE' ? this.expenseRoots() : this.incomeRoots(),
  );

  constructor() {
    effect(() => {
      const existing = this.category();
      if (existing) {
        this.form.reset(
          {
            name: existing.name,
            kind: existing.kind,
            parentCategoryId: existing.parentCategoryId ?? '',
            icon: existing.icon ?? DEFAULT_CATEGORY_ICON,
            colorHex: existing.colorHex ?? DEFAULT_CATEGORY_COLOR,
          },
          { emitEvent: false },
        );
      } else {
        this.form.reset(
          {
            name: '',
            kind: this.initialKind(),
            parentCategoryId: this.initialParentId() ?? '',
            icon: DEFAULT_CATEGORY_ICON,
            colorHex: DEFAULT_CATEGORY_COLOR,
          },
          { emitEvent: false },
        );
      }
    });
  }

  protected previewBg(): string {
    return `color-mix(in srgb, ${this.form.controls.colorHex.value} 14%, transparent)`;
  }

  protected isNameInvalid(): boolean {
    const ctrl = this.form.controls.name;
    return ctrl.invalid && (ctrl.touched || ctrl.dirty);
  }

  protected onKindChange(nextKind: CategoryKind): void {
    if (this.form.controls.kind.value !== nextKind) {
      this.form.controls.kind.setValue(nextKind);
      this.form.controls.parentCategoryId.setValue('');
    }
  }

  protected onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }

    const raw = this.form.getRawValue();
    const existing = this.category();

    if (existing) {
      this.submitted.emit({
        mode: 'edit',
        id: existing.id,
        payload: {
          name: raw.name.trim(),
          icon: raw.icon || null,
          colorHex: raw.colorHex || null,
        },
      });
      return;
    }

    this.submitted.emit({
      mode: 'create',
      payload: {
        name: raw.name.trim(),
        kind: raw.kind,
        parentCategoryId: raw.parentCategoryId ? raw.parentCategoryId : null,
        icon: raw.icon || null,
        colorHex: raw.colorHex || null,
      },
    });
  }
}
