import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { Category } from '../../../core/finance/finance.models';
import { IconComponent } from '../../../shared/ui';

function normalizeSearch(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

@Component({
  selector: 'fo-category-picker',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="space-y-2.5">
      <div class="flex flex-wrap items-center gap-1.5" role="group" aria-label="Categorías frecuentes">
        @for (category of visibleChips(); track category.id) {
          @let selected = selectedCategoryId() === category.id;
          <button
            type="button"
            [attr.data-testid]="'category-chip-' + category.id"
            [attr.aria-pressed]="selected"
            (click)="toggleCategory(category.id)"
            class="fo-focus-ring inline-flex max-w-full cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors duration-150"
            [class.border-accent]="selected"
            [class.bg-accent]="selected"
            [class.text-accent-contrast]="selected"
            [class.border-border]="!selected"
            [class.bg-surface-2]="!selected"
            [class.text-text]="!selected"
            [class.hover:border-accent]="!selected"
          >
            <span
              class="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full"
              [style.background-color]="selected ? 'rgba(255,255,255,0.2)' : (category.colorHex || 'var(--fo-accent-soft)')"
              [style.color]="selected ? 'currentColor' : (category.colorHex ? '#FFFFFF' : 'var(--fo-accent)')"
            >
              <fo-icon [name]="category.icon || 'circle-ellipsis'" [size]="11" />
            </span>
            <span class="truncate">{{ category.name }}</span>
          </button>
        }

        @if (allCategories().length > 0) {
          <button
            type="button"
            data-testid="category-more-btn"
            [attr.aria-expanded]="searchOpen()"
            (click)="toggleSearch()"
            class="fo-focus-ring inline-flex cursor-pointer items-center gap-1 rounded-full border border-dashed border-border bg-surface px-3 py-1.5 text-xs font-medium text-muted transition-colors hover:border-accent hover:text-text"
          >
            <span>{{ searchOpen() ? 'Cerrar' : 'Más' }}</span>
            <fo-icon [name]="searchOpen() ? 'x' : 'chevron-down'" [size]="12" />
          </button>
        }
      </div>

      @if (searchOpen()) {
        <div
          data-testid="category-search-panel"
          class="space-y-2 rounded-xl border border-border bg-surface-2/60 p-2.5"
        >
          <label for="fo-quick-add-cat-search" class="sr-only">Buscar categoría</label>
          <input
            id="fo-quick-add-cat-search"
            type="search"
            [value]="searchQuery()"
            (input)="onSearchInput($event)"
            placeholder="Buscar categoría..."
            class="fo-focus-ring w-full rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-text placeholder:text-muted"
          />

          @if (filteredCategories().length === 0) {
            <p class="py-1 text-center text-xs text-muted">No encontramos esa categoría.</p>
          } @else {
            <div class="flex max-h-36 flex-wrap gap-1.5 overflow-y-auto">
              @for (category of filteredCategories(); track category.id) {
                @let selected = selectedCategoryId() === category.id;
                <button
                  type="button"
                  [attr.data-testid]="'category-search-item-' + category.id"
                  [attr.aria-pressed]="selected"
                  (click)="selectFromSearch(category.id)"
                  class="fo-focus-ring inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors"
                  [class.border-accent]="selected"
                  [class.bg-accent]="selected"
                  [class.text-accent-contrast]="selected"
                  [class.border-border]="!selected"
                  [class.bg-surface]="!selected"
                  [class.text-text]="!selected"
                  [class.hover:border-accent]="!selected"
                >
                  <fo-icon [name]="category.icon || 'circle-ellipsis'" [size]="12" />
                  <span>{{ category.name }}</span>
                </button>
              }
            </div>
          }
        </div>
      }

      <div>
        <label for="fo-quick-add-description" class="sr-only">¿En qué?</label>
        <input
          id="fo-quick-add-description"
          data-testid="quick-add-description"
          type="text"
          maxlength="140"
          [value]="description()"
          (input)="onDescriptionInput($event)"
          (keydown.enter)="onDescriptionEnter($event)"
          placeholder="¿En qué?"
          class="fo-focus-ring w-full rounded-xl border border-border bg-surface-2/60 px-3.5 py-2 text-sm text-text placeholder:text-muted"
        />
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryPickerComponent {
  readonly frequentCategories = input.required<readonly Category[]>();
  readonly allCategories = input.required<readonly Category[]>();
  readonly selectedCategoryId = input<string | null>(null);
  readonly description = input<string>('');

  readonly selectedCategoryIdChange = output<string | null>();
  readonly descriptionChange = output<string>();
  readonly enterSubmit = output<void>();

  protected readonly searchOpen = signal<boolean>(false);
  protected readonly searchQuery = signal<string>('');

  protected readonly visibleChips = computed<readonly Category[]>(() => {
    const frequent = this.frequentCategories();
    const selectedId = this.selectedCategoryId();
    if (!selectedId || frequent.some((cat) => cat.id === selectedId)) {
      return frequent;
    }
    const selectedCategory = this.allCategories().find((cat) => cat.id === selectedId);
    return selectedCategory ? [selectedCategory, ...frequent.slice(0, 7)] : frequent;
  });

  protected readonly filteredCategories = computed<readonly Category[]>(() => {
    const query = normalizeSearch(this.searchQuery());
    const all = this.allCategories();
    if (!query) {
      return all;
    }
    return all.filter((category) => normalizeSearch(category.name).includes(query));
  });

  protected toggleCategory(categoryId: string): void {
    const next = this.selectedCategoryId() === categoryId ? null : categoryId;
    this.selectedCategoryIdChange.emit(next);
  }

  protected toggleSearch(): void {
    this.searchOpen.update((open) => {
      if (open) {
        this.searchQuery.set('');
      }
      return !open;
    });
  }

  protected onSearchInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.searchQuery.set(target?.value ?? '');
  }

  protected selectFromSearch(categoryId: string): void {
    this.selectedCategoryIdChange.emit(categoryId);
    this.searchOpen.set(false);
    this.searchQuery.set('');
  }

  protected onDescriptionInput(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.descriptionChange.emit(target?.value ?? '');
  }

  protected onDescriptionEnter(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.enterSubmit.emit();
  }
}
