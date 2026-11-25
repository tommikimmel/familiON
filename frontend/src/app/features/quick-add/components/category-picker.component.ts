import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { Category } from '../../../core/finance/finance.models';

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

  template: `
    <div class="flex flex-col gap-3">
      @if (showDescription()) {
      <div>
        <label for="fo-quick-add-description" class="sr-only">Nombre opcional</label>
        <input
          id="fo-quick-add-description"
          data-testid="quick-add-description"
          [attr.data-prefilled]="descriptionPrefilled() ? 'true' : null"
          type="text"
          maxlength="140"
          [value]="description()"
          (input)="onDescriptionInput($event)"
          (keydown.enter)="onDescriptionEnter($event)"
          placeholder="Nombre opcional (ej. Café, Coto...)"
          class="qa-input"
          [class.is-prefilled]="descriptionPrefilled()"
        />
      </div>
      }

      <div class="cat-grid" role="group" aria-label="Categorías frecuentes">
        @for (category of gridChips(); track category.id) {
          @let selected = selectedCategoryId() === category.id;
          <button
            type="button"
            [attr.data-testid]="'category-chip-' + category.id"
            [attr.data-prefilled]="selected && categoryPrefilled() ? 'true' : null"
            [attr.aria-pressed]="selected"
            (click)="toggleCategory(category.id)"
            class="fo-cat-pill fo-focus-ring truncate"
            [class.is-active]="selected"
            [class.is-prefilled]="selected && categoryPrefilled()"
          >
            {{ category.name }}
          </button>
        }

        @if (allCategories().length > 0) {
          <button
            type="button"
            data-testid="category-more-btn"
            [attr.aria-expanded]="searchOpen()"
            (click)="toggleSearch()"
            class="fo-cat-pill fo-focus-ring"
            [class.is-active]="searchOpen()"
          >
            {{ searchOpen() ? 'Cerrar' : 'Otros' }}
          </button>
        }
      </div>

      @if (searchOpen()) {
        <div data-testid="category-search-panel" class="search-panel">
          <label for="fo-quick-add-cat-search" class="sr-only">Buscar categoría</label>
          <input
            id="fo-quick-add-cat-search"
            type="search"
            [value]="searchQuery()"
            (input)="onSearchInput($event)"
            placeholder="Buscar categoría..."
            class="qa-input"
          />

          @if (filteredCategories().length === 0) {
            <p class="py-1 text-center text-[11px] text-muted">No encontramos esa categoría.</p>
          } @else {
            <div class="flex max-h-36 flex-wrap gap-1 overflow-y-auto">
              @for (category of filteredCategories(); track category.id) {
                @let selected = selectedCategoryId() === category.id;
                <button
                  type="button"
                  [attr.data-testid]="'category-search-item-' + category.id"
                  [attr.aria-pressed]="selected"
                  (click)="selectFromSearch(category.id)"
                  class="fo-mini-chip fo-focus-ring"
                  [class.is-active]="selected"
                >
                  {{ category.name }}
                </button>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .qa-input {
      width: 100%;
      padding: 10px 12px;
      border: 1px solid var(--fo-border);
      border-radius: 12px;
      outline: none;
      background: var(--fo-bg);
      color: var(--fo-text);
      font-family: inherit;
      font-size: 13px;
      transition: border-color 0.18s ease;
    }
    .qa-input::placeholder {
      color: var(--fo-text-muted);
    }
    .qa-input:focus,
    .qa-input.is-prefilled {
      border-color: var(--fo-accent);
    }
    .cat-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 6px;
    }
    .fo-cat-pill.is-prefilled {
      box-shadow: 0 0 0 2px var(--fo-accent-soft);
    }
    .search-panel {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 10px;
      border: 1px solid var(--fo-border);
      border-radius: 12px;
      background: var(--fo-bg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryPickerComponent {
  readonly frequentCategories = input.required<readonly Category[]>();
  readonly allCategories = input.required<readonly Category[]>();
  readonly selectedCategoryId = input<string | null>(null);
  readonly description = input<string>('');
  readonly categoryPrefilled = input<boolean>(false);
  readonly descriptionPrefilled = input<boolean>(false);
  /** Desktop renders the name input next to the amount (mock modal), so the picker hides it. */
  readonly showDescription = input<boolean>(true);

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

  /** Mock `cat-grid`: five quick pills plus the `Otros` pill (six cells, two rows of three). */
  protected readonly gridChips = computed<readonly Category[]>(() =>
    this.visibleChips().slice(0, this.allCategories().length > 0 ? 5 : 6),
  );

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
