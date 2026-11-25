import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'fo-bottom-sheet',
  standalone: true,
  imports: [A11yModule, IconComponent],
  host: {
    '(document:keydown.escape)': 'onEscape()',
  },
  template: `
    @if (open()) {
      <div
        class="fixed inset-0 z-50 flex items-end justify-center lg:items-center lg:p-4"
        role="presentation"
      >
        <button
          type="button"
          tabindex="-1"
          aria-label="Cerrar panel"
          (click)="close()"
          class="fo-sheet-backdrop fo-animate-backdrop"
        ></button>

        <section
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="title() || ariaLabel()"
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
          class="fo-sheet-panel"
        >
          <div class="fo-sheet-handle" aria-hidden="true"></div>

          @if (title()) {
            <header class="fo-sheet-header">
              <h2 class="fo-sheet-title">{{ title() }}</h2>
              <button type="button" aria-label="Cerrar" (click)="close()" class="fo-sheet-close fo-focus-ring">
                <fo-icon name="x" [size]="15" />
              </button>
            </header>
          }

          <ng-content />
        </section>
      </div>
    }
  `,
  styles: `
    /* Mobile: mock .sheet-backdrop + .sheet + .sheet-handle. Desktop: .modal-backdrop + .modal-box. */
    .fo-sheet-backdrop {
      position: fixed;
      inset: 0;
      border: none;
      cursor: default;
      background: var(--fo-overlay);
      backdrop-filter: blur(5px);
      -webkit-backdrop-filter: blur(5px);
    }

    .fo-sheet-panel {
      position: relative;
      z-index: 10;
      width: 100%;
      max-height: 92dvh;
      overflow-y: auto;
      scrollbar-width: none;
      display: flex;
      flex-direction: column;
      gap: 11px;
      padding: 10px 16px calc(16px + env(safe-area-inset-bottom));
      background: var(--fo-surface);
      border-top: 1px solid var(--fo-border);
      border-top-left-radius: 26px;
      border-top-right-radius: 26px;
      animation: fo-sheet-slide-up 0.36s var(--fo-ease-smooth) both;
    }

    .fo-sheet-handle {
      flex-shrink: 0;
      width: 34px;
      height: 4px;
      margin: 0 auto;
      border-radius: 999px;
      background: var(--fo-border);
    }

    .fo-sheet-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .fo-sheet-title {
      font-size: 14px;
      font-weight: 700;
      letter-spacing: -0.01em;
    }

    .fo-sheet-close {
      width: 31px;
      height: 31px;
      display: grid;
      place-items: center;
      border-radius: 999px;
      border: 1px solid var(--fo-border);
      background: transparent;
      color: var(--fo-text-muted);
      cursor: pointer;
      transition:
        transform 0.14s var(--fo-ease-spring),
        color 0.15s ease;
    }

    .fo-sheet-close:active {
      transform: scale(0.88);
    }

    @media (min-width: 1024px) {
      .fo-sheet-panel {
        max-width: 440px;
        max-height: 90dvh;
        gap: 12px;
        padding: 18px 20px;
        border: 1px solid var(--fo-border);
        border-radius: 20px;
        box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
        animation: fo-modal-enter 0.28s var(--fo-ease-spring) both;
      }

      .fo-sheet-handle {
        display: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomSheetComponent {
  readonly open = model<boolean>(false);
  readonly title = input<string>('');
  readonly ariaLabel = input<string>('Panel');
  readonly closed = output<void>();

  protected onEscape(): void {
    if (this.open()) {
      this.close();
    }
  }

  protected close(): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    this.closed.emit();
  }
}
