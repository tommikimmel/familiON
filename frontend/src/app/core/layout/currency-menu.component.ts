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
import { CurrencyFlagComponent } from '../../shared/ui/currency-chip/currency-flag.component';

const DEFAULT_CURRENCIES = ['ARS', 'USD'] as const;

@Component({
  selector: 'fo-currency-menu',
  standalone: true,
  imports: [CurrencyChipComponent, CurrencyFlagComponent],
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

    <div
      role="menu"
      aria-label="Opciones de divisa y lente"
      class="fo-currency-dropdown"
      [class.is-open]="isOpen()"
      [attr.aria-hidden]="!isOpen()"
      [attr.inert]="isOpen() ? null : ''"
    >
      @for (code of enabledCurrencies(); track code) {
        @let isActive = code === viewContext.activeCurrency();
        <button
          type="button"
          role="menuitemradio"
          [attr.aria-checked]="isActive"
          (click)="selectCurrency(code)"
          class="fo-curr-opt fo-focus-ring"
          [class.is-active]="isActive"
        >
          <span class="flex items-center gap-[7px]">
            <fo-currency-flag [currency]="code" />
            <span>{{ code }}</span>
          </span>
          @if (code === referenceCurrency()) {
            <span class="fo-curr-meta">Referencia</span>
          }
        </button>
      }

      <div class="fo-curr-divider" role="separator"></div>

      <button
        type="button"
        role="switch"
        aria-labelledby="fo-constant-base-label"
        [attr.aria-checked]="isConstantBase()"
        (click)="toggleConstantBase()"
        class="fo-ipc-row fo-focus-ring"
      >
        <span id="fo-constant-base-label">A plata de hoy</span>
        <span class="fo-mini-switch" [class.is-on]="isConstantBase()" aria-hidden="true"></span>
      </button>
    </div>
  `,
  styles: `
    .fo-currency-dropdown {
      position: absolute;
      top: calc(100% + 6px);
      right: 0;
      width: 196px;
      z-index: 40;
      display: flex;
      flex-direction: column;
      gap: 2px;
      padding: 6px;
      background: var(--fo-surface);
      border: 1px solid var(--fo-border);
      border-radius: 14px;
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.18);
      opacity: 0;
      transform: translateY(-6px) scale(0.96);
      transform-origin: top right;
      pointer-events: none;
      visibility: hidden;
      transition:
        opacity 0.18s ease,
        transform 0.24s var(--fo-ease-spring),
        visibility 0s linear 0.24s;
    }

    .fo-currency-dropdown.is-open {
      opacity: 1;
      transform: translateY(0) scale(1);
      pointer-events: auto;
      visibility: visible;
      transition:
        opacity 0.18s ease,
        transform 0.24s var(--fo-ease-spring),
        visibility 0s;
    }

    .fo-curr-opt {
      display: flex;
      align-items: center;
      justify-content: space-between;
      border: none;
      background: transparent;
      color: var(--fo-text);
      font-family: inherit;
      font-size: 11.5px;
      font-weight: 600;
      padding: 7px 8px;
      border-radius: 9px;
      cursor: pointer;
      transition: background 0.14s ease;
    }

    .fo-curr-opt:hover {
      background: var(--fo-surface-2);
    }

    .fo-curr-opt.is-active {
      background: var(--fo-accent-soft);
      color: var(--fo-accent);
    }

    .fo-curr-meta {
      font-size: 10px;
      color: var(--fo-text-muted);
    }

    .fo-curr-divider {
      height: 1px;
      background: var(--fo-border);
      margin: 4px 2px;
    }

    .fo-ipc-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      border: none;
      background: transparent;
      padding: 6px 8px;
      font-family: inherit;
      font-size: 11px;
      text-align: left;
      color: var(--fo-text-2);
      cursor: pointer;
      border-radius: 8px;
    }

    @media (min-width: 1024px) {
      .fo-currency-dropdown {
        width: 204px;
        border-radius: 13px;
        box-shadow: 0 16px 34px rgba(0, 0, 0, 0.18);
        transition:
          opacity 0.16s ease,
          transform 0.22s var(--fo-ease-spring),
          visibility 0s linear 0.22s;
      }

      .fo-curr-opt {
        border-radius: 8px;
      }
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
