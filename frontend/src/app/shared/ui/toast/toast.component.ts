import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { ToastService } from './toast.service';

/**
 * Toast (mock `.toast` + `.toast-check`): inverted pill, dynamic-island style at the top on
 * mobile and anchored bottom-right on desktop.
 */
@Component({
  selector: 'fo-toast',
  standalone: true,
  imports: [IconComponent],
  template: `
    @if (toast.current(); as current) {
      <div
        [attr.role]="current.kind === 'error' ? 'alert' : 'status'"
        [attr.aria-live]="current.kind === 'error' ? 'assertive' : 'polite'"
        class="fo-toast"
        [class.fo-toast--error]="current.kind === 'error'"
      >
        @if (current.kind === 'success') {
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="3"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
            class="shrink-0"
          >
            <polyline class="fo-animate-check" points="20 6 9 17 4 12" />
          </svg>
        } @else {
          <fo-icon name="alert-circle" [size]="14" [strokeWidth]="2.6" class="shrink-0" />
        }

        <span class="truncate">{{ current.message }}</span>

        <button
          type="button"
          aria-label="Cerrar notificación"
          (click)="toast.dismiss()"
          class="fo-toast-close fo-focus-ring"
        >
          <fo-icon name="x" [size]="12" [strokeWidth]="2.6" />
        </button>
      </div>
    }
  `,
  styles: `
    .fo-toast {
      position: fixed;
      top: calc(12px + env(safe-area-inset-top));
      left: 50%;
      z-index: 70;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      max-width: calc(100vw - 2rem);
      padding: 7px 8px 7px 14px;
      border-radius: 999px;
      background: var(--fo-text);
      color: var(--fo-bg);
      font-size: 11.5px;
      font-weight: 600;
      white-space: nowrap;
      box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
      transform: translateX(-50%);
      animation: fo-toast-enter 0.34s var(--fo-ease-spring) both;
    }

    .fo-toast--error {
      background: var(--fo-danger);
      color: #ffffff;
    }

    .fo-toast-close {
      display: inline-grid;
      place-items: center;
      width: 18px;
      height: 18px;
      margin-left: 2px;
      border: none;
      border-radius: 999px;
      background: transparent;
      color: inherit;
      opacity: 0.6;
      cursor: pointer;
      transition: opacity 0.15s ease;
    }

    .fo-toast-close:hover {
      opacity: 1;
    }

    @media (min-width: 1024px) {
      .fo-toast {
        top: auto;
        left: auto;
        right: 20px;
        bottom: 18px;
        padding: 8px 10px 8px 14px;
        font-size: 12px;
        transform: none;
        animation: fo-toast-enter-desktop 0.24s var(--fo-ease-spring) both;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastComponent {
  protected readonly toast = inject(ToastService);
}
