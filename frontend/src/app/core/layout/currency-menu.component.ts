import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { SessionStore } from '../state/session.store';
import { ViewContextStore } from '../state/view-context.store';
import { CurrencyChipComponent } from '../../shared/ui/currency-chip/currency-chip.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';

const DEFAULT_CURRENCIES = ['ARS', 'USD'] as const;

@Component({
  selector: 'fo-currency-menu',
  standalone: true,
  imports: [CurrencyChipComponent, IconComponent],
  host: {
    class: 'relative inline-block',
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'close()',
  },
  template: `
    <fo-currency-chip
      [currency]="viewContext.activeCurrency()"
      [adjusted]="isConstantBase()"
      [expanded]="isOpen()"
      (pressed)="toggle()"
    />

    @if (isOpen()) {
      <div
        role="menu"
        aria-label="Opciones de divisa y lente"
        class="absolute right-0 z-40 mt-2 w-60 rounded-[var(--fo-radius)] border border-border bg-surface p-2 shadow-lg"
      >
        <p class="px-2.5 py-1 text-[11px] font-semibold tracking-wider text-muted uppercase">
          Divisa
        </p>

        <div class="flex flex-col gap-0.5">
          @for (code of enabledCurrencies(); track code) {
            @let isActive = code === viewContext.activeCurrency();
            <button
              type="button"
              role="menuitemradio"
              [attr.aria-checked]="isActive"
              (click)="selectCurrency(code)"
              class="fo-focus-ring flex w-full cursor-pointer items-center justify-between rounded-xl px-2.5 py-2 text-left text-sm transition-colors hover:bg-surface-2"
              [class.font-semibold]="isActive"
              [class.text-accent]="isActive"
              [class.text-text]="!isActive"
            >
              <span class="flex items-center gap-2">
                <span>{{ code }}</span>
                @if (code === referenceCurrency()) {
                  <span class="rounded-full bg-surface-2 px-1.5 py-0.5 text-[10px] font-medium text-muted">
                    Referencia
                  </span>
                }
              </span>
              @if (isActive) {
                <fo-icon name="check" [size]="16" />
              }
            </button>
          }
        </div>

        <div class="my-2 border-t border-border" role="separator"></div>

        <div class="flex items-center justify-between gap-3 rounded-xl px-2.5 py-2">
          <div class="min-w-0">
            <span id="fo-constant-base-label" class="block text-sm font-medium text-text">
              A plata de hoy
            </span>
            <span class="block text-xs text-muted">Ajustado por inflación</span>
          </div>
          <button
            type="button"
            role="switch"
            aria-labelledby="fo-constant-base-label"
            [attr.aria-checked]="isConstantBase()"
            (click)="toggleConstantBase()"
            class="fo-focus-ring relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-colors duration-200"
            [class.bg-accent]="isConstantBase()"
            [class.bg-border]="!isConstantBase()"
          >
            <span
              class="inline-block h-4 w-4 rounded-full bg-white shadow-xs transition-transform duration-200"
              [class.translate-x-6]="isConstantBase()"
              [class.translate-x-1]="!isConstantBase()"
            ></span>
          </button>
        </div>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrencyMenuComponent {
  private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly session = inject(SessionStore);
  protected readonly viewContext = inject(ViewContextStore);

  protected readonly isOpen = signal(false);

  protected readonly enabledCurrencies = computed<readonly string[]>(() => {
    const configured = this.session.me()?.family.enabledCurrencies;
    return configured && configured.length > 0 ? configured : DEFAULT_CURRENCIES;
  });

  protected readonly referenceCurrency = computed<string>(
    () => this.session.me()?.family.referenceCurrency ?? 'USD',
  );

  protected readonly isConstantBase = computed(
    () => this.viewContext.lens() === 'CONSTANT_BASE',
  );

  protected toggle(): void {
    this.isOpen.update((open) => !open);
  }

  protected close(): void {
    this.isOpen.set(false);
  }

  protected selectCurrency(code: string): void {
    this.viewContext.selectCurrency(code);
    this.close();
  }

  protected toggleConstantBase(): void {
    this.viewContext.setLens(this.isConstantBase() ? 'NOMINAL' : 'CONSTANT_BASE');
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.isOpen()) {
      return;
    }
    const target = event.target as Node | null;
    if (target && !this.elementRef.nativeElement.contains(target)) {
      this.close();
    }
  }
}
