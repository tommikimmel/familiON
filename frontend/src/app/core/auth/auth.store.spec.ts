import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Me } from '../api/models/me.model';
import { SessionStore } from '../state/session.store';
import { ThemeService } from '../theme/theme.service';
import { AuthStore } from './auth.store';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'ana@familion.test',
    fullName: 'Ana García',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'DARK',
    accentColor: '#2563EB',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

describe('AuthStore', () => {
  let store: AuthStore;
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let theme: ThemeService;
  let router: Router;

  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    store = TestBed.inject(AuthStore);
    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    theme = TestBed.inject(ThemeService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('bootstraps CSRF then /me and sets authenticated session and theme', async () => {
    const me = buildMe();
    const applySpy = vi.spyOn(theme, 'apply');

    const initPromise = store.init();

    const csrfReq = httpMock.expectOne('/bff/csrf');
    expect(csrfReq.request.method).toBe('GET');
    csrfReq.flush(null, { status: 204, statusText: 'No Content' });

    await Promise.resolve();

    const meReq = httpMock.expectOne('/bff/api/auth/me');
    expect(meReq.request.method).toBe('GET');
    meReq.flush(me);

    await initPromise;

    expect(store.status()).toBe('authenticated');
    expect(store.isAuthenticated()).toBe(true);
    expect(session.me()).toEqual(me);
    expect(applySpy).toHaveBeenCalledWith(me);
  });

  it('sets anonymous status when /me returns 401 during init', async () => {
    const initPromise = store.init();

    httpMock.expectOne('/bff/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();

    httpMock
      .expectOne('/bff/api/auth/me')
      .flush({ status: 401, detail: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    await initPromise;

    expect(store.status()).toBe('anonymous');
    expect(session.me()).toBeNull();
  });

  it('deduplicates concurrent init calls and skips repeat calls once initialized', async () => {
    const first = store.init();
    const second = store.init();

    httpMock.expectOne('/bff/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    httpMock.expectOne('/bff/api/auth/me').flush(buildMe());

    await Promise.all([first, second]);
    await store.init();

    expect(store.status()).toBe('authenticated');
  });

  it('logs in without 2FA and loads /me without storing any tokens in web storage', async () => {
    const me = buildMe();
    const loginPromise = store.login('ana@familion.test', 'Secret1234');

    const loginReq = httpMock.expectOne('/bff/auth/login');
    expect(loginReq.request.method).toBe('POST');
    expect(loginReq.request.body).toEqual({
      email: 'ana@familion.test',
      password: 'Secret1234',
    });
    loginReq.flush({ requires2fa: false });

    await Promise.resolve();

    httpMock.expectOne('/bff/api/auth/me').flush(me);

    const response = await loginPromise;

    expect(response).toEqual({ requires2fa: false });
    expect(store.status()).toBe('authenticated');
    expect(store.pendingChallenge()).toBeNull();
    expect(session.me()).toEqual(me);
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('stores pendingChallenge in memory when login requires 2FA and completes verify2fa', async () => {
    const me = buildMe({ twoFactorEnabled: true });

    const loginPromise = store.login('ana@familion.test', 'Secret1234');
    httpMock
      .expectOne('/bff/auth/login')
      .flush({ requires2fa: true, challengeToken: 'challenge-xyz' });

    const loginResponse = await loginPromise;

    expect(loginResponse.requires2fa).toBe(true);
    expect(store.pendingChallenge()).toBe('challenge-xyz');
    expect(store.status()).toBe('unknown');
    expect(localStorage.getItem('challengeToken')).toBeNull();
    expect(sessionStorage.length).toBe(0);

    const verifyPromise = store.verify2fa('123456', true, 'Chrome Linux');
    const verifyReq = httpMock.expectOne('/bff/auth/2fa/verify');
    expect(verifyReq.request.method).toBe('POST');
    expect(verifyReq.request.body).toEqual({
      challengeToken: 'challenge-xyz',
      code: '123456',
      trustDevice: true,
      deviceName: 'Chrome Linux',
    });
    verifyReq.flush({ requires2fa: false });

    await Promise.resolve();
    httpMock.expectOne('/bff/api/auth/me').flush(me);

    await verifyPromise;

    expect(store.pendingChallenge()).toBeNull();
    expect(store.status()).toBe('authenticated');
    expect(session.me()).toEqual(me);
  });

  it('clears session and navigates to /auth/login on logout', async () => {
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const initPromise = store.init();
    httpMock.expectOne('/bff/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    httpMock.expectOne('/bff/api/auth/me').flush(buildMe());
    await initPromise;

    const logoutPromise = store.logout();
    const logoutReq = httpMock.expectOne('/bff/auth/logout');
    expect(logoutReq.request.method).toBe('POST');
    logoutReq.flush(null, { status: 204, statusText: 'No Content' });

    await logoutPromise;

    expect(session.me()).toBeNull();
    expect(store.status()).toBe('anonymous');
    expect(store.pendingChallenge()).toBeNull();
    expect(navSpy).toHaveBeenCalledWith(['/auth/login']);
  });

  it('accepts invitation and loads authenticated session', async () => {
    const me = buildMe({ role: 'HIJO' });
    const promise = store.acceptInvite('inv-token', 'Lucas García', 'Password123');

    const inviteReq = httpMock.expectOne('/bff/auth/register-invite');
    expect(inviteReq.request.method).toBe('POST');
    expect(inviteReq.request.body).toEqual({
      token: 'inv-token',
      fullName: 'Lucas García',
      password: 'Password123',
    });
    inviteReq.flush({ requires2fa: false });

    await Promise.resolve();
    httpMock.expectOne('/bff/api/auth/me').flush(me);

    await promise;

    expect(store.status()).toBe('authenticated');
    expect(session.me()).toEqual(me);
  });
});
