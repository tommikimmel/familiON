import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  DestroyRef,
  Injectable,
  Signal,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { API } from '../api/api-config';

interface PushConfigDto {
  publicKey: string | null;
}

interface PushSubscriptionDto {
  endpoint: string;
  keys: {
    p256dh: string;
    auth: string;
  };
  userAgent: string;
}

interface PushNotificationClickData {
  link?: unknown;
  url?: unknown;
}

const PERMISSION_DENIED_MESSAGE =
  'Los avisos están bloqueados en este navegador. Habilitalos desde la configuración del sitio para recibir notificaciones.';
const ENABLE_ERROR_MESSAGE =
  'No pudimos activar los avisos en este dispositivo. Intentá nuevamente.';
const DISABLE_ERROR_MESSAGE =
  'No pudimos desactivar los avisos en este dispositivo. Intentá nuevamente.';

function bufferToBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) {
    return '';
  }
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function extractSubscriptionKeys(subscription: PushSubscription): {
  p256dh: string;
  auth: string;
} {
  const json = typeof subscription.toJSON === 'function' ? subscription.toJSON() : null;
  const jsonKeys = json?.keys;
  if (jsonKeys?.['p256dh'] && jsonKeys?.['auth']) {
    return {
      p256dh: jsonKeys['p256dh'],
      auth: jsonKeys['auth'],
    };
  }
  const p256dh =
    typeof subscription.getKey === 'function'
      ? bufferToBase64Url(subscription.getKey('p256dh'))
      : '';
  const auth =
    typeof subscription.getKey === 'function'
      ? bufferToBase64Url(subscription.getKey('auth'))
      : '';
  return { p256dh, auth };
}

function resolveClickLink(raw: unknown, origin: string | undefined): string | null {
  if (typeof raw !== 'string') {
    return null;
  }
  const trimmed = raw.trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.startsWith('/') && !trimmed.startsWith('//')) {
    return trimmed;
  }
  if (origin) {
    try {
      const parsed = new URL(trimmed);
      if (parsed.origin === origin) {
        return `${parsed.pathname}${parsed.search}${parsed.hash}`;
      }
    } catch {
      return null;
    }
  }
  return null;
}

@Injectable({ providedIn: 'root' })
export class PushNotificationsService {
  private readonly swPush = inject(SwPush, { optional: true });
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _publicKey = signal<string | null>(null);
  private readonly _configLoaded = signal<boolean>(false);
  private readonly _subscription = signal<PushSubscription | null>(null);
  private readonly _busy = signal<boolean>(false);
  private readonly _permissionDenied = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  private configRequest: Promise<string | null> | null = null;

  readonly publicKey: Signal<string | null> = this._publicKey.asReadonly();
  readonly configLoaded: Signal<boolean> = this._configLoaded.asReadonly();
  readonly isSubscribed: Signal<boolean> = computed(() => this._subscription() !== null);
  readonly isAvailable: Signal<boolean> = computed(
    () => Boolean(this.swPush?.isEnabled) && this._publicKey() !== null,
  );
  readonly busy: Signal<boolean> = this._busy.asReadonly();
  readonly permissionDenied: Signal<boolean> = this._permissionDenied.asReadonly();
  readonly error: Signal<string | null> = this._error.asReadonly();

  constructor() {
    this._permissionDenied.set(this.readNotificationPermission() === 'denied');

    if (this.swPush?.isEnabled) {
      this.swPush.subscription
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((subscription) => {
          this._subscription.set(subscription);
        });

      this.swPush.notificationClicks
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((event) => {
          const data = event.notification?.data as PushNotificationClickData | undefined;
          const origin = this.doc.defaultView?.location?.origin;
          const target =
            resolveClickLink(data?.link, origin) ?? resolveClickLink(data?.url, origin);
          if (target) {
            void this.router.navigateByUrl(target);
          }
          this.doc.defaultView?.focus?.();
        });
    }
  }

  loadConfig(): Promise<string | null> {
    if (!this.swPush?.isEnabled) {
      this._publicKey.set(null);
      this._configLoaded.set(true);
      return Promise.resolve(null);
    }

    if (this.configRequest) {
      return this.configRequest;
    }

    this._permissionDenied.set(this.readNotificationPermission() === 'denied');

    const request = this.fetchConfig();
    this.configRequest = request;
    return request;
  }

  async toggle(): Promise<boolean> {
    if (this.isSubscribed()) {
      return this.disable();
    }
    return this.enable();
  }

  async enable(): Promise<boolean> {
    const publicKey = this._publicKey();
    if (!this.swPush?.isEnabled || !publicKey || this._busy()) {
      return false;
    }

    if (this.readNotificationPermission() === 'denied') {
      this._permissionDenied.set(true);
      this._error.set(PERMISSION_DENIED_MESSAGE);
      return false;
    }

    this._busy.set(true);
    this._error.set(null);

    let subscription: PushSubscription;
    try {
      subscription = await this.swPush.requestSubscription({
        serverPublicKey: publicKey,
      });
    } catch (err: unknown) {
      const isDenied =
        this.readNotificationPermission() === 'denied' ||
        (err instanceof Error && err.name === 'NotAllowedError');
      this._permissionDenied.set(isDenied);
      this._error.set(isDenied ? PERMISSION_DENIED_MESSAGE : ENABLE_ERROR_MESSAGE);
      this._busy.set(false);
      return false;
    }

    const keys = extractSubscriptionKeys(subscription);
    const payload: PushSubscriptionDto = {
      endpoint: subscription.endpoint,
      keys,
      userAgent: this.doc.defaultView?.navigator?.userAgent ?? '',
    };

    try {
      await firstValueFrom(
        this.http.post<void>(`${API}/notifications/push-subscriptions`, payload),
      );
      this._subscription.set(subscription);
      this._permissionDenied.set(false);
      return true;
    } catch {
      await subscription.unsubscribe().catch(() => undefined);
      this._subscription.set(null);
      this._error.set(ENABLE_ERROR_MESSAGE);
      return false;
    } finally {
      this._busy.set(false);
    }
  }

  async disable(): Promise<boolean> {
    if (!this.swPush?.isEnabled || this._busy()) {
      return false;
    }

    const endpoint = this._subscription()?.endpoint ?? null;
    this._busy.set(true);
    this._error.set(null);

    try {
      await this.swPush.unsubscribe();
      this._subscription.set(null);

      if (endpoint) {
        await firstValueFrom(
          this.http.delete<void>(`${API}/notifications/push-subscriptions`, {
            body: { endpoint },
          }),
        );
      }
      return true;
    } catch {
      this._error.set(DISABLE_ERROR_MESSAGE);
      return false;
    } finally {
      this._busy.set(false);
    }
  }

  private async fetchConfig(): Promise<string | null> {
    try {
      const response = await firstValueFrom(
        this.http.get<PushConfigDto>(`${API}/notifications/push-config`),
      );
      const rawKey = response?.publicKey;
      const trimmed = typeof rawKey === 'string' && rawKey.trim().length > 0 ? rawKey.trim() : null;
      this._publicKey.set(trimmed);
      return trimmed;
    } catch {
      this._publicKey.set(null);
      return null;
    } finally {
      this._configLoaded.set(true);
      this.configRequest = null;
    }
  }

  private readNotificationPermission(): NotificationPermission | null {
    const win = this.doc.defaultView as (Window & { Notification?: typeof Notification }) | null;
    if (!win || typeof win.Notification === 'undefined') {
      return null;
    }
    return win.Notification.permission;
  }
}
