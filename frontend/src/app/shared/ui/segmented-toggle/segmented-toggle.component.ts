import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
} from '@angular/core';

export interface SegmentedOption<T = string> {
  value: T;
  label: string;
}

@Component({
  selector: 'fo-segmented-toggle',
  standalone: true,
  template: `
    <div
      role="tablist"
      [attr.aria-label]="ariaLabel()"
      class="relative inline-grid w-full items-center rounded-full border border-border bg-surface-2 p-1"
      [style.grid-template-columns]="'repeat(' + options().length + ', minmax(0, 1fr))'"
    >
      @if (activeIndex() >= 0 && options().length > 0) {
        <div
          aria-hidden="true"
          class="fo-segmented-pill pointer-events-none absolute top-1 bottom-1 left-1 rounded-full bg-surface shadow-xs"
          [style.width]="'calc((100% - 0.5rem) / ' + options().length + ')'"
          [style.transform]="'translateX(' + activeIndex() * 100 + '%)'"
        ></div>
      }

      @for (option of options(); track option.value; let i = $index) {
        @let isSelected = option.value === value();
        <button
          type="button"
          role="tab"
          [attr.aria-selected]="isSelected"
          [attr.tabindex]="isSelected ? 0 : -1"
          (click)="select(option.value)"
          (keydown)="onKeydown($event, i)"
          class="fo-focus-ring relative z-10 cursor-pointer truncate rounded-full font-medium transition-colors duration-150"
          [class.px-3]="size() === 'sm'"
          [class.py-1]="size() === 'sm'"
          [class.text-xs]="size() === 'sm'"
          [class.px-4]="size() === 'md'"
          [class.py-1.5]="size() === 'md'"
          [class.text-sm]="size() === 'md'"
          [class.text-text]="isSelected"
          [class.text-muted]="!isSelected"
        >
          {{ option.label }}
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .fo-segmented-pill {
      transition: transform 320ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1));
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SegmentedToggleComponent<T = string> {
  readonly options = input.required<readonly SegmentedOption<T>[]>();
  readonly value = model.required<T>();
  readonly ariaLabel = input<string>('Selector de opciones');
  readonly size = input<'sm' | 'md'>('md');

  protected readonly activeIndex = computed(() =>
    this.options().findIndex((option) => option.value === this.value()),
  );

  protected select(nextValue: T): void {
    if (this.value() !== nextValue) {
      this.value.set(nextValue);
    }
  }

  protected onKeydown(event: KeyboardEvent, currentIndex: number): void {
    const items = this.options();
    if (items.length === 0) {
      return;
    }

    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') {
      nextIndex = (currentIndex + 1) % items.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (currentIndex - 1 + items.length) % items.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = items.length - 1;
    }

    if (nextIndex === null) {
      return;
    }

    event.preventDefault();
    this.select(items[nextIndex].value);

    const parent = (event.currentTarget as HTMLElement | null)?.parentElement;
    const buttons = parent?.querySelectorAll<HTMLButtonElement>('button[role="tab"]');
    buttons?.[nextIndex]?.focus();
  }
}
