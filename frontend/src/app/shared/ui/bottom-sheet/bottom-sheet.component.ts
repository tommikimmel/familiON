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
          class="fo-animate-backdrop fixed inset-0 cursor-default bg-black/45 backdrop-blur-[5px]"
        ></button>

        <section
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="title() || ariaLabel()"
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
          class="fo-sheet-panel relative z-10 max-h-[90dvh] w-full overflow-y-auto rounded-t-[20px] border border-border bg-surface p-5 shadow-xl lg:max-w-lg lg:rounded-[var(--fo-radius)]"
        >
          <div class="mx-auto mb-3 h-1 w-10 rounded-full bg-border lg:hidden" aria-hidden="true"></div>

          @if (title()) {
            <header class="mb-4 flex items-center justify-between gap-3">
              <h2 class="text-base font-semibold text-text">{{ title() }}</h2>
              <button
                type="button"
                aria-label="Cerrar"
                (click)="close()"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-2 hover:text-text"
              >
                <fo-icon name="x" [size]="18" />
              </button>
            </header>
          }

          <ng-content />
        </section>
      </div>
    }
  `,
  styles: `
    .fo-sheet-panel {
      animation: fo-sheet-slide-up 360ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1)) both;
    }

    @media (min-width: 1024px) {
      .fo-sheet-panel {
        animation: fo-modal-enter 220ms var(--fo-ease-spring, cubic-bezier(0.34, 1.35, 0.64, 1)) both;
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
