import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { ToastService } from './toast.service';

@Component({
  selector: 'fo-toast',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (toast.current(); as current) {
      <div
        [attr.role]="current.kind === 'error' ? 'alert' : 'status'"
        [attr.aria-live]="current.kind === 'error' ? 'assertive' : 'polite'"
        class="fo-animate-toast fixed top-4 left-1/2 z-[60] flex max-w-[calc(100vw-2rem)] items-center gap-2.5 rounded-full border border-border bg-surface px-4 py-2.5 text-sm font-medium text-text shadow-lg"
      >
        @if (current.kind === 'success') {
          <span
            class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-success/15 text-success"
            aria-hidden="true"
          >
            <svg viewBox="0 0 20 20" class="h-3.5 w-3.5" fill="none">
              <path
                d="M4.5 10.5L8.2 14.2L15.5 6.2"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="fo-animate-check"
              />
            </svg>
          </span>
        } @else {
          <span
            class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-danger/15 text-danger"
            aria-hidden="true"
          >
            <fo-icon name="alert-circle" [size]="14" />
          </span>
        }

        <span class="truncate">{{ current.message }}</span>

        <button
          type="button"
          aria-label="Cerrar notificación"
          (click)="toast.dismiss()"
          class="fo-focus-ring ml-1 inline-flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center rounded-full text-muted transition-colors hover:bg-surface-2 hover:text-text"
        >
          <fo-icon name="x" [size]="14" />
        </button>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastComponent {
  protected readonly toast = inject(ToastService);
}
