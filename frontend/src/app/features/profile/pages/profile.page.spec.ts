import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Me } from '../../../core/api/models/me.model';
import { AuthStore } from '../../../core/auth/auth.store';
import { SessionStore } from '../../../core/state/session.store';
import { ThemeService } from '../../../core/theme/theme.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ProfilePage } from './profile.page';

function buildMe(overrides: Partial<Me> = {}): Me {
  return {
    id: 'user-1',
    email: 'ana@familion.test',
    fullName: 'Ana García',
    role: 'ADULTO',
    familyVisibility: 'FAMILY_FULL',
    themePreference: 'LIGHT',
    accentColor: '#0F766E',
    twoFactorEnabled: false,
    emailVerified: true,
    family: {
      id: 'fam-1',
      name: 'Familia García',
      baseCurrency: 'ARS',
      referenceCurrency: 'USD',
      referenceRateSource: 'USD_MEP',
      timezone: 'America/Argentina/Buenos_Aires',
      enabledCurrencies: ['ARS', 'USD'],
    },
    ...overrides,
  };
}

describe('ProfilePage', () => {
  let httpMock: HttpTestingController;
  let session: SessionStore;
  let theme: ThemeService;
  let toast: ToastService;
  let authStore: AuthStore;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.resetTestingModule();
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });

    httpMock = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionStore);
    theme = TestBed.inject(ThemeService);
    toast = TestBed.inject(ToastService);
    authStore = TestBed.inject(AuthStore);

    const me = buildMe();
    session.setMe(me);
    theme.apply({ themePreference: me.themePreference, accentColor: me.accentColor });
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    vi.useRealTimers();
  });

  it('applies preset accent immediately and debounces PATCH preferences by 400ms', async () => {
    const applySpy = vi.spyOn(theme, 'apply');
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();

    const blueBtn = fixture.nativeElement.querySelector(
      '[data-color="#2563EB"]',
    ) as HTMLButtonElement;
    const violetBtn = fixture.nativeElement.querySelector(
      '[data-color="#7C3AED"]',
    ) as HTMLButtonElement;

    blueBtn.click();
    fixture.detectChanges();
    expect(applySpy).toHaveBeenCalledWith({ themePreference: 'LIGHT', accentColor: '#2563EB' });

    vi.advanceTimersByTime(200);
    httpMock.expectNone('/bff/api/auth/me/preferences');

    violetBtn.click();
    fixture.detectChanges();
    expect(applySpy).toHaveBeenCalledWith({ themePreference: 'LIGHT', accentColor: '#7C3AED' });

    vi.advanceTimersByTime(399);
    httpMock.expectNone('/bff/api/auth/me/preferences');

    vi.advanceTimersByTime(1);

    const req = httpMock.expectOne('/bff/api/auth/me/preferences');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      themePreference: 'LIGHT',
      accentColor: '#7C3AED',
    });

    const updated = buildMe({ accentColor: '#7C3AED' });
    req.flush(updated);
    await Promise.resolve();

    expect(session.me()?.accentColor).toBe('#7C3AED');
    expect(theme.accent()).toBe('#7C3AED');
  });

  it('emits custom color from native color picker and persists debounced PATCH', async () => {
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();

    const nativeInput = fixture.nativeElement.querySelector(
      '[data-testid="accent-native-input"]',
    ) as HTMLInputElement;
    nativeInput.value = '#ff3366';
    nativeInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(theme.accent()).toBe('#FF3366');

    vi.advanceTimersByTime(400);

    const req = httpMock.expectOne('/bff/api/auth/me/preferences');
    expect(req.request.body).toEqual({
      themePreference: 'LIGHT',
      accentColor: '#FF3366',
    });
    req.flush(buildMe({ accentColor: '#FF3366' }));
    await Promise.resolve();
  });

  it('reverts theme and accent and shows error toast when PATCH preferences fails', async () => {
    const toastSpy = vi.spyOn(toast, 'error');
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();

    const orangeBtn = fixture.nativeElement.querySelector(
      '[data-color="#EA580C"]',
    ) as HTMLButtonElement;
    orangeBtn.click();
    fixture.detectChanges();

    expect(theme.accent()).toBe('#EA580C');

    vi.advanceTimersByTime(400);

    const req = httpMock.expectOne('/bff/api/auth/me/preferences');
    req.flush(
      { status: 500, detail: 'Error al guardar preferencias' },
      { status: 500, statusText: 'Internal Server Error' },
    );
    await Promise.resolve();
    await Promise.resolve();

    expect(theme.accent()).toBe('#0F766E');
    expect(theme.theme()).toBe('LIGHT');
    expect(toastSpy).toHaveBeenCalledWith('Error al guardar preferencias');
  });

  it('calls AuthStore.logout when clicking Cerrar sesión', async () => {
    const logoutSpy = vi.spyOn(authStore, 'logout').mockResolvedValue();
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const logoutBtn = buttons.find((btn) => btn.textContent?.includes('Cerrar sesión'));
    expect(logoutBtn).toBeTruthy();

    logoutBtn?.click();
    await Promise.resolve();

    expect(logoutSpy).toHaveBeenCalledTimes(1);
  });
});
