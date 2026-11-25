import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ButtonComponent, IconComponent } from '../../shared/ui';
import { InstallPromptService } from './install-prompt.service';

@Component({
  selector: 'fo-pwa-install-banner',
  standalone: true,
  imports: [ButtonComponent, IconComponent],
  template: `
    @if (installPrompt.shouldShow()) {
      <aside
        aria-label="Instalar aplicación"
        data-testid="pwa-install-banner"
        class="border-b border-border bg-surface px-4 py-2.5 text-text"
      >
        <div class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3">
          @if (installPrompt.mode() === 'native') {
            <div class="flex min-w-0 items-center gap-2.5">
              <span
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
                aria-hidden="true"
              >
                <fo-icon name="smartphone" [size]="17" />
              </span>
              <p class="text-xs font-medium text-text sm:text-sm">
                Agrega FamiliON a tu inicio
              </p>
            </div>

            <div class="flex items-center gap-1.5">
              <fo-button
                data-testid="pwa-install-btn"
                size="sm"
                (click)="onInstall()"
              >
                Instalar
              </fo-button>

              <button
                type="button"
                data-testid="pwa-install-dismiss-btn"
                aria-label="Ocultar sugerencia de instalación"
                (click)="installPrompt.dismiss()"
                class="fo-focus-ring inline-flex h-8 w-8 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
              >
                <fo-icon name="x" [size]="16" />
              </button>
            </div>
          } @else if (installPrompt.mode() === 'ios') {
            <div data-testid="pwa-ios-install-guide" class="flex min-w-0 flex-1 flex-wrap items-center gap-x-4 gap-y-2">
              <div class="flex items-center gap-2">
                <span
                  class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
                  aria-hidden="true"
                >
                  <fo-icon name="smartphone" [size]="17" />
                </span>
                <p class="text-xs font-semibold text-text sm:text-sm">
                  Agrega FamiliON a tu inicio
                </p>
              </div>

              <ol class="flex flex-wrap items-center gap-2 text-xs text-muted">
                <li class="inline-flex items-center gap-1.5 rounded-full bg-surface-2 px-2.5 py-1 text-text">
                  <fo-icon name="smartphone" [size]="14" class="text-accent" />
                  <span>1. Toca Compartir</span>
                </li>
                <li aria-hidden="true">
                  <fo-icon name="chevron-right" [size]="14" class="text-muted" />
                </li>
                <li class="inline-flex items-center gap-1.5 rounded-full bg-surface-2 px-2.5 py-1 text-text">
                  <fo-icon name="plus" [size]="14" class="text-accent" />
                  <span>2. Agregar a inicio</span>
                </li>
              </ol>
            </div>

            <button
              type="button"
              data-testid="pwa-install-dismiss-btn"
              aria-label="Cerrar guía de instalación"
              (click)="installPrompt.dismiss()"
              class="fo-focus-ring inline-flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-lg text-muted transition-colors hover:bg-surface-2 hover:text-text"
            >
              <fo-icon name="x" [size]="16" />
            </button>
          }
        </div>
      </aside>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PwaInstallBannerComponent {
  protected readonly installPrompt = inject(InstallPromptService);

  protected onInstall(): void {
    void this.installPrompt.install();
  }
}
