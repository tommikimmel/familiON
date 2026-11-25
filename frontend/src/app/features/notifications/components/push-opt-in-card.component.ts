import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CardComponent, IconComponent } from '../../../shared/ui';

@Component({
  selector: 'fo-push-opt-in-card',
  standalone: true,
  imports: [CardComponent, IconComponent],
  template: `
    <fo-card padding="md" data-testid="push-opt-in-card">
      <div class="flex items-start justify-between gap-4">
        <div class="flex min-w-0 items-start gap-3">
          <span
            class="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent"
            aria-hidden="true"
          >
            <fo-icon name="bell" [size]="18" />
          </span>

          <div class="min-w-0">
            <h2 class="text-sm font-semibold text-text">Avisos en este dispositivo</h2>
            <p class="mt-0.5 text-xs text-muted">
              Recibí notificaciones push cuando haya vencimientos, gastos atribuidos o alertas de tus vehículos.
            </p>
          </div>
        </div>

        <button
          type="button"
          role="switch"
          data-testid="push-opt-in-switch"
          aria-label="Avisos en este dispositivo"
          [attr.aria-checked]="subscribed()"
          [disabled]="busy()"
          (click)="togglePush.emit()"
          class="fo-focus-ring relative mt-0.5 inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border border-transparent transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-55"
          [class.bg-accent]="subscribed()"
          [class.bg-border]="!subscribed()"
        >
          <span
            aria-hidden="true"
            class="inline-block h-5 w-5 rounded-full bg-white shadow-xs transition-transform duration-200"
            [class.translate-x-5]="subscribed()"
            [class.translate-x-0.5]="!subscribed()"
          ></span>
        </button>
      </div>

      @if (errorMessage(); as message) {
        <div
          role="alert"
          data-testid="push-opt-in-error"
          class="mt-3 flex items-start gap-2 rounded-xl border px-3 py-2 text-xs"
          [class.border-warning/40]="permissionDenied()"
          [class.bg-warning/10]="permissionDenied()"
          [class.text-warning]="permissionDenied()"
          [class.border-danger/30]="!permissionDenied()"
          [class.bg-danger/10]="!permissionDenied()"
          [class.text-danger]="!permissionDenied()"
        >
          <fo-icon name="alert-circle" [size]="15" class="mt-0.5 shrink-0" />
          <span>{{ message }}</span>
        </div>
      } @else if (permissionDenied()) {
        <div
          role="status"
          data-testid="push-permission-denied"
          class="mt-3 flex items-start gap-2 rounded-xl border border-warning/40 bg-warning/10 px-3 py-2 text-xs text-warning"
        >
          <fo-icon name="alert-circle" [size]="15" class="mt-0.5 shrink-0" />
          <span>
            Los avisos están bloqueados en este navegador. Habilitalos desde la configuración del sitio para activarlos.
          </span>
        </div>
      }
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PushOptInCardComponent {
  readonly subscribed = input<boolean>(false);
  readonly busy = input<boolean>(false);
  readonly permissionDenied = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);

  readonly togglePush = output<void>();
}
