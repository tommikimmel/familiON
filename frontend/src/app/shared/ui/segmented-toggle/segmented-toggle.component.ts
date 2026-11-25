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

/**
 * Visual variants, each mirroring a control of the mocks:
 * - `segmented`: `.mini-seg` + `.mini-seg-slider` (default).
 * - `scope`: `.scope-bar` + `.scope-slider` (Personal | Familia); the slider turns accent
 *   when any option other than the first is active.
 * - `tabs`: mobile `.gran-tabs` (plain, active pill in accent-soft).
 * - `tabs-boxed`: desktop `.gran-tabs` (boxed, active pill raised on card).
 */
export type SegmentedVariant = 'segmented' | 'scope' | 'tabs' | 'tabs-boxed';

@Component({
  selector: 'fo-segmented-toggle',
  standalone: true,
  template: `
    <div
      role="tablist"
      [attr.aria-label]="ariaLabel()"
      class="fo-seg"
      [class.fo-seg--segmented]="variant() === 'segmented'"
      [class.fo-seg--md]="variant() === 'segmented' && size() === 'md'"
      [class.fo-seg--scope]="variant() === 'scope'"
      [class.fo-seg--tabs]="isTabs()"
      [class.fo-seg--boxed]="variant() === 'tabs-boxed'"
      [class.is-accent]="variant() === 'scope' && activeIndex() > 0"
      [style.grid-template-columns]="isTabs() ? null : 'repeat(' + options().length + ', minmax(0, 1fr))'"
    >
      @if (!isTabs() && activeIndex() >= 0 && options().length > 0) {
        <div
          aria-hidden="true"
          class="fo-seg-slider"
          [style.width]="'calc((100% - 2 * var(--fo-seg-pad)) / ' + options().length + ')'"
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
          class="fo-seg-btn fo-focus-ring"
          [class.is-active]="isSelected"
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

    :host(.inline) {
      display: inline-block;
    }

    .fo-seg {
      --fo-seg-pad: 2px;
      position: relative;
      display: grid;
      width: 100%;
      align-items: center;
    }

    .fo-seg-slider {
      position: absolute;
      top: var(--fo-seg-pad);
      bottom: var(--fo-seg-pad);
      left: var(--fo-seg-pad);
      pointer-events: none;
      background: var(--fo-surface);
      transition:
        transform 0.28s var(--fo-ease-spring),
        background 0.25s ease;
    }

    .fo-seg-btn {
      position: relative;
      z-index: 1;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      border: none;
      background: transparent;
      color: var(--fo-text-muted);
      font-family: inherit;
      font-weight: 600;
      cursor: pointer;
      transition: color 0.18s ease;
    }

    .fo-seg-btn.is-active {
      color: var(--fo-text);
    }

    /* mini-seg */
    .fo-seg--segmented {
      background: var(--fo-surface-2);
      border-radius: 8px;
      padding: var(--fo-seg-pad);
    }

    .fo-seg--segmented .fo-seg-slider {
      border-radius: 6px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    }

    .fo-seg--segmented .fo-seg-btn {
      font-size: 10.5px;
      padding: 3px 9px;
      border-radius: 6px;
    }

    .fo-seg--segmented.fo-seg--md {
      --fo-seg-pad: 3px;
      border-radius: 10px;
    }

    .fo-seg--segmented.fo-seg--md .fo-seg-slider {
      border-radius: 8px;
    }

    .fo-seg--segmented.fo-seg--md .fo-seg-btn {
      font-size: 12px;
      padding: 6px 12px;
    }

    /* scope-bar */
    .fo-seg--scope {
      --fo-seg-pad: 3px;
      background: var(--fo-surface-2);
      border: 1px solid var(--fo-border);
      border-radius: 12px;
      padding: var(--fo-seg-pad);
    }

    .fo-seg--scope .fo-seg-slider {
      border-radius: 9px;
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
      transition:
        transform 0.32s var(--fo-ease-spring),
        background 0.25s ease;
    }

    .fo-seg--scope .fo-seg-btn {
      font-size: 11.5px;
      padding: 6px;
      border-radius: 9px;
      transition: color 0.2s ease;
    }

    .fo-seg--scope.is-accent .fo-seg-slider {
      background: var(--fo-accent);
    }

    .fo-seg--scope.is-accent .fo-seg-btn.is-active {
      color: var(--fo-accent-contrast);
    }

    @media (min-width: 1024px) {
      .fo-seg--scope {
        border-radius: 10px;
      }

      .fo-seg--scope .fo-seg-slider {
        border-radius: 7px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
        transition:
          transform 0.3s var(--fo-ease-spring),
          background 0.25s ease;
      }

      .fo-seg--scope .fo-seg-btn {
        padding: 5px 4px;
        border-radius: 0;
      }
    }

    /* gran-tabs */
    .fo-seg--tabs {
      display: inline-flex;
      width: auto;
      gap: 2px;
    }

    .fo-seg--tabs .fo-seg-btn {
      font-size: 11px;
      padding: 4px 8px;
      border-radius: 7px;
      transition: all 0.18s ease;
    }

    .fo-seg--tabs .fo-seg-btn.is-active {
      background: var(--fo-accent-soft);
      color: var(--fo-accent);
    }

    .fo-seg--tabs.fo-seg--boxed {
      background: var(--fo-surface-2);
      border: 1px solid var(--fo-border);
      border-radius: 9px;
      padding: 2px;
    }

    .fo-seg--tabs.fo-seg--boxed .fo-seg-btn {
      padding: 4px 9px;
      transition: all 0.15s ease;
    }

    .fo-seg--tabs.fo-seg--boxed .fo-seg-btn.is-active {
      background: var(--fo-surface);
      color: var(--fo-text);
      box-shadow: var(--fo-shadow);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SegmentedToggleComponent<T = string> {
  readonly options = input.required<readonly SegmentedOption<T>[]>();
  readonly value = model.required<T>();
  readonly ariaLabel = input<string>('Selector de opciones');
  readonly size = input<'sm' | 'md'>('md');
  readonly variant = input<SegmentedVariant>('segmented');

  protected readonly activeIndex = computed(() =>
    this.options().findIndex((option) => option.value === this.value()),
  );

  protected readonly isTabs = computed(
    () => this.variant() === 'tabs' || this.variant() === 'tabs-boxed',
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
