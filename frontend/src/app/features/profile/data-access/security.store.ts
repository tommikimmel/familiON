import { Injectable, Signal, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../../../core/auth/auth.store';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { extractApiErrorMessage } from './profile.models';
import { SecurityApi } from './security.api';
import { SecuritySettings } from './security.models';

@Injectable({ providedIn: 'root' })
export class SecurityStore {
  private readonly api = inject(SecurityApi);
  private readonly authStore = inject(AuthStore);
  private readonly toast = inject(ToastService);

  private readonly _settings = signal<SecuritySettings | null>(null);
  private readonly _loading = signal<boolean>(false);
  private readonly _loadError = signal<string | null>(null);
  private readonly _updatingTwoFactor = signal<boolean>(false);
  private readonly _revokingDeviceId = signal<string | null>(null);

  readonly settings: Signal<SecuritySettings | null> = this._settings.asReadonly();
  readonly loading: Signal<boolean> = this._loading.asReadonly();
  readonly loadError: Signal<string | null> = this._loadError.asReadonly();
  readonly updatingTwoFactor: Signal<boolean> = this._updatingTwoFactor.asReadonly();
  readonly revokingDeviceId: Signal<string | null> = this._revokingDeviceId.asReadonly();

  async load(): Promise<void> {
    this._loading.set(true);
    this._loadError.set(null);

    try {
      const settings = await firstValueFrom(this.api.getSettings());
      this._settings.set(settings);
    } catch (err: unknown) {
      const message = extractApiErrorMessage(
        err,
        'No se pudo cargar tu configuración de seguridad.',
      );
      this._loadError.set(message);
      this.toast.error(message);
    } finally {
      this._loading.set(false);
    }
  }

  async setTwoFactorEnabled(twoFactorEnabled: boolean): Promise<boolean> {
    this._updatingTwoFactor.set(true);

    try {
      const updated = await firstValueFrom(this.api.updateSettings({ twoFactorEnabled }));
      this._settings.set(updated);
      try {
        await this.authStore.reloadMe();
      } catch {
        // Keep updated security state if background /me reload fails.
      }
      this.toast.success(
        updated.twoFactorEnabled
          ? 'Verificación en dos pasos activada.'
          : 'Verificación en dos pasos desactivada.',
      );
      return true;
    } catch (err: unknown) {
      this.toast.error(
        extractApiErrorMessage(err, 'No se pudo actualizar la verificación en dos pasos.'),
      );
      return false;
    } finally {
      this._updatingTwoFactor.set(false);
    }
  }

  async revokeTrustedDevice(id: string): Promise<boolean> {
    this._revokingDeviceId.set(id);

    try {
      await firstValueFrom(this.api.revokeTrustedDevice(id));
      this._settings.update((current) => {
        if (!current) {
          return current;
        }
        return {
          ...current,
          trustedDevices: current.trustedDevices.filter((device) => device.id !== id),
        };
      });
      this.toast.success('Dispositivo confiable quitado.');
      return true;
    } catch (err: unknown) {
      this.toast.error(extractApiErrorMessage(err, 'No se pudo quitar el dispositivo confiable.'));
      return false;
    } finally {
      this._revokingDeviceId.set(null);
    }
  }
}
