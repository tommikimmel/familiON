import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth.store';
import { SecuritySettings } from '../data-access/security.models';
import { SecurityPage } from './security.page';

const SAMPLE_SETTINGS: SecuritySettings = {
  twoFactorEnabled: false,
  trustedDevices: [
    {
      id: 'dev-1',
      deviceName: 'Chrome en Linux',
      createdAt: '2026-11-10T15:00:00Z',
      expiresAt: '2026-12-10T15:00:00Z',
    },
  ],
};

async function flushMicrotasks(): Promise<void> {
  for (let i = 0; i < 6; i++) {
    await Promise.resolve();
  }
}

describe('SecurityPage', () => {
  let httpMock: HttpTestingController;
  let authStore: AuthStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    authStore = TestBed.inject(AuthStore);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads security settings, toggles 2FA, and revokes trusted devices', async () => {
    vi.spyOn(authStore, 'reloadMe').mockResolvedValue({} as never);

    const fixture = TestBed.createComponent(SecurityPage);
    fixture.detectChanges();

    const getReq = httpMock.expectOne('/bff/api/auth/me/security');
    expect(getReq.request.method).toBe('GET');
    getReq.flush(SAMPLE_SETTINGS);

    await flushMicrotasks();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const toggle = el.querySelector('[data-testid="two-factor-switch"]') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-checked')).toBe('false');
    expect(el.querySelectorAll('[data-testid="trusted-device-row"]').length).toBe(1);

    toggle.click();
    const patchReq = httpMock.expectOne('/bff/api/auth/me/security');
    expect(patchReq.request.method).toBe('PATCH');
    expect(patchReq.request.body).toEqual({ twoFactorEnabled: true });
    patchReq.flush({ ...SAMPLE_SETTINGS, twoFactorEnabled: true });

    await flushMicrotasks();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-checked')).toBe('true');

    const revokeBtn = el.querySelector(
      '[data-testid="revoke-device-btn"] button',
    ) as HTMLButtonElement;
    revokeBtn.click();

    const deleteReq = httpMock.expectOne('/bff/api/auth/me/security/trusted-devices/dev-1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    await flushMicrotasks();
    fixture.detectChanges();
    expect(el.querySelectorAll('[data-testid="trusted-device-row"]').length).toBe(0);
    expect(el.textContent).toContain('Sin dispositivos confiables');
  });
});
