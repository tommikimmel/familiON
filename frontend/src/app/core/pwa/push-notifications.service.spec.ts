import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { BehaviorSubject, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PushNotificationsService } from './push-notifications.service';

interface MockSwPush {
  isEnabled: boolean;
  subscription: BehaviorSubject<PushSubscription | null>;
  notificationClicks: Subject<{
    action: string;
    notification: NotificationOptions & { title: string };
  }>;
  requestSubscription: ReturnType<typeof vi.fn>;
  unsubscribe: ReturnType<typeof vi.fn>;
}

function createFakeSubscription(endpoint = 'https://push.example/sub-1'): PushSubscription {
  return {
    endpoint,
    expirationTime: null,
    options: {} as PushSubscriptionOptions,
    getKey: () => null,
    toJSON: () => ({
      endpoint,
      keys: {
        p256dh: 'p256dh-key',
        auth: 'auth-key',
      },
    }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  };
}

describe('PushNotificationsService', () => {
  let httpMock: HttpTestingController;
  let router: Router;
  let mockSwPush: MockSwPush;

  beforeEach(() => {
    mockSwPush = {
      isEnabled: true,
      subscription: new BehaviorSubject<PushSubscription | null>(null),
      notificationClicks: new Subject(),
      requestSubscription: vi.fn(),
      unsubscribe: vi.fn().mockResolvedValue(undefined),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SwPush, useValue: mockSwPush },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('is available only when SwPush.isEnabled and publicKey is non-null', async () => {
    const service = TestBed.inject(PushNotificationsService);

    const loadNull = service.loadConfig();
    httpMock.expectOne('/bff/api/notifications/push-config').flush({ publicKey: null });
    await loadNull;
    expect(service.isAvailable()).toBe(false);

    const loadKey = service.loadConfig();
    httpMock.expectOne('/bff/api/notifications/push-config').flush({ publicKey: 'VAPID-KEY-123' });
    await loadKey;
    expect(service.isAvailable()).toBe(true);
  });

  it('enables push via SwPush.requestSubscription + POST and disables via unsubscribe + DELETE', async () => {
    const service = TestBed.inject(PushNotificationsService);

    const cfgPromise = service.loadConfig();
    httpMock.expectOne('/bff/api/notifications/push-config').flush({ publicKey: 'VAPID-KEY-123' });
    await cfgPromise;

    const fakeSub = createFakeSubscription('https://push.example/sub-42');
    mockSwPush.requestSubscription.mockResolvedValueOnce(fakeSub);

    const enablePromise = service.enable();
    await Promise.resolve();

    const postReq = httpMock.expectOne('/bff/api/notifications/push-subscriptions');
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({
      endpoint: 'https://push.example/sub-42',
      keys: { p256dh: 'p256dh-key', auth: 'auth-key' },
      userAgent: navigator.userAgent,
    });
    postReq.flush(null, { status: 201, statusText: 'Created' });

    const enabled = await enablePromise;
    expect(enabled).toBe(true);
    expect(mockSwPush.requestSubscription).toHaveBeenCalledWith({
      serverPublicKey: 'VAPID-KEY-123',
    });
    expect(service.isSubscribed()).toBe(true);

    const disablePromise = service.disable();
    await Promise.resolve();

    const deleteReq = httpMock.expectOne('/bff/api/notifications/push-subscriptions');
    expect(deleteReq.request.method).toBe('DELETE');
    expect(deleteReq.request.body).toEqual({ endpoint: 'https://push.example/sub-42' });
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    const disabled = await disablePromise;
    expect(disabled).toBe(true);
    expect(mockSwPush.unsubscribe).toHaveBeenCalledTimes(1);
    expect(service.isSubscribed()).toBe(false);
  });

  it('handles permission denied gracefully with a clear message', async () => {
    const service = TestBed.inject(PushNotificationsService);

    const cfgPromise = service.loadConfig();
    httpMock.expectOne('/bff/api/notifications/push-config').flush({ publicKey: 'VAPID-KEY-123' });
    await cfgPromise;

    const deniedError = new Error('Permission denied');
    deniedError.name = 'NotAllowedError';
    mockSwPush.requestSubscription.mockRejectedValueOnce(deniedError);

    const ok = await service.enable();
    expect(ok).toBe(false);
    expect(service.permissionDenied()).toBe(true);
    expect(service.error()).toContain('bloqueados');
  });

  it('navigates to internal link when a push notification is clicked', () => {
    TestBed.inject(PushNotificationsService);
    const navSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(window, 'focus').mockImplementation(() => undefined);

    mockSwPush.notificationClicks.next({
      action: '',
      notification: {
        title: 'Gasto atribuido',
        data: { link: '/transactions?id=99' },
      },
    });

    expect(navSpy).toHaveBeenCalledWith('/transactions?id=99');

    navSpy.mockClear();
    mockSwPush.notificationClicks.next({
      action: '',
      notification: {
        title: 'External link ignored',
        data: { link: 'https://evil.example/phish' },
      },
    });
    expect(navSpy).not.toHaveBeenCalled();
  });
});
