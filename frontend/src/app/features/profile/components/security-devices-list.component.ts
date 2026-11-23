import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { TrustedDevice } from '../data-access/security.models';

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

@Component({
  selector: 'fo-security-devices-list',
  standalone: true,
  imports: [ButtonComponent, CardComponent, EmptyStateComponent, IconComponent],
  template: `
    <fo-card padding="md">
      <div class="space-y-4">
        <div class="space-y-0.5">
          <h2 class="text-base font-semibold text-text">Dispositivos confiables</h2>
          <p class="text-xs text-muted">
            Equipos que pueden omitir el código de verificación temporalmente.
          </p>
        </div>

        @if (devices().length === 0) {
          <fo-empty-state
            icon="monitor"
            title="Sin dispositivos confiables"
            description="Cuando marques un dispositivo como confiable al iniciar sesión con 2FA, aparecerá aquí."
          />
        } @else {
          <ul class="divide-y divide-border" aria-label="Lista de dispositivos confiables">
            @for (device of devices(); track device.id) {
              <li
                data-testid="trusted-device-row"
                [attr.data-device-id]="device.id"
                class="flex flex-col gap-3 py-3 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
              >
                <div class="flex min-w-0 items-start gap-3">
                  <span
                    class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-surface-2 text-muted"
                    aria-hidden="true"
                  >
                    <fo-icon name="monitor" [size]="18" />
                  </span>
                  <div class="min-w-0 space-y-0.5">
                    <p class="truncate text-sm font-semibold text-text">{{ device.deviceName }}</p>
                    <p class="text-xs text-muted">
                      Alta: {{ formatDate(device.createdAt) }} · Vence:
                      {{ formatDate(device.expiresAt) }}
                    </p>
                  </div>
                </div>

                <fo-button
                  size="sm"
                  variant="ghost"
                  data-testid="revoke-device-btn"
                  [loading]="revokingId() === device.id"
                  [disabled]="revokingId() === device.id"
                  (click)="revokeDevice.emit(device.id)"
                >
                  Quitar
                </fo-button>
              </li>
            }
          </ul>
        }
      </div>
    </fo-card>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityDevicesListComponent {
  readonly devices = input.required<readonly TrustedDevice[]>();
  readonly revokingId = input<string | null>(null);

  readonly revokeDevice = output<string>();

  protected formatDate(iso: string): string {
    return formatDateTime(iso);
  }
}
