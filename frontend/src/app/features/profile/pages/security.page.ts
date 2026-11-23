import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent } from '../../../shared/ui/button/button.component';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state/empty-state.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton/skeleton.component';
import { SecurityDevicesListComponent } from '../components/security-devices-list.component';
import { SecurityStore } from '../data-access/security.store';

@Component({
  selector: 'fo-security-page',
  standalone: true,
  imports: [
    RouterLink,
    ButtonComponent,
    CardComponent,
    EmptyStateComponent,
    IconComponent,
    SecurityDevicesListComponent,
    SkeletonComponent,
  ],
  template: `
    <section class="mx-auto max-w-2xl space-y-5">
      <header class="space-y-2">
        <a
          routerLink="/profile"
          class="fo-focus-ring inline-flex items-center gap-1 rounded-lg text-xs font-medium text-muted transition-colors hover:text-text"
        >
          <fo-icon name="chevron-left" [size]="14" />
          <span>Volver a Perfil</span>
        </a>
        <div class="space-y-0.5">
          <h1 class="text-xl font-semibold text-text">Seguridad</h1>
          <p class="text-sm text-muted">
            Protege tu cuenta con verificación en dos pasos y controla tus dispositivos confiables.
          </p>
        </div>
      </header>

      @if (store.loading()) {
        <div data-testid="security-loading" class="space-y-4">
          <fo-card padding="md">
            <div class="flex items-center justify-between gap-4">
              <div class="flex-1 space-y-2">
                <fo-skeleton width="55%" height="1.1rem" />
                <fo-skeleton width="80%" height="0.85rem" />
              </div>
              <fo-skeleton width="2.75rem" height="1.5rem" rounded="full" />
            </div>
          </fo-card>
          <fo-card padding="md">
            <div class="space-y-3">
              <fo-skeleton width="45%" height="1.1rem" />
              <fo-skeleton width="100%" height="3.25rem" />
            </div>
          </fo-card>
        </div>
      } @else if (store.loadError(); as error) {
        <fo-empty-state
          icon="alert-circle"
          title="No pudimos cargar tu configuración de seguridad"
          [description]="error"
        >
          <fo-button size="sm" (click)="reload()">Reintentar</fo-button>
        </fo-empty-state>
      } @else if (store.settings(); as settings) {
        <fo-card padding="md">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-1">
              <h2 id="two-factor-label" class="text-base font-semibold text-text">
                Verificación en dos pasos por email
              </h2>
              <p id="two-factor-desc" class="text-xs text-muted">
                Al activarla, te enviaremos un código de 6 dígitos a tu correo cada vez que inicies
                sesión desde un dispositivo nuevo.
              </p>
            </div>

            <button
              type="button"
              role="switch"
              data-testid="two-factor-switch"
              aria-labelledby="two-factor-label"
              aria-describedby="two-factor-desc"
              [attr.aria-checked]="settings.twoFactorEnabled"
              [disabled]="store.updatingTwoFactor()"
              (click)="onToggleTwoFactor(settings.twoFactorEnabled)"
              class="fo-focus-ring relative mt-0.5 inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-55"
              [class.bg-accent]="settings.twoFactorEnabled"
              [class.bg-border]="!settings.twoFactorEnabled"
            >
              <span
                aria-hidden="true"
                class="inline-block h-4 w-4 rounded-full bg-white shadow-xs transition-transform duration-150"
                [class.translate-x-6]="settings.twoFactorEnabled"
                [class.translate-x-1]="!settings.twoFactorEnabled"
              ></span>
            </button>
          </div>
        </fo-card>

        <fo-security-devices-list
          [devices]="settings.trustedDevices"
          [revokingId]="store.revokingDeviceId()"
          (revokeDevice)="onRevokeDevice($event)"
        />
      }
    </section>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityPage implements OnInit {
  protected readonly store = inject(SecurityStore);

  ngOnInit(): void {
    void this.store.load();
  }

  protected reload(): void {
    void this.store.load();
  }

  protected onToggleTwoFactor(currentValue: boolean): void {
    void this.store.setTwoFactorEnabled(!currentValue);
  }

  protected onRevokeDevice(id: string): void {
    void this.store.revokeTrustedDevice(id);
  }
}
