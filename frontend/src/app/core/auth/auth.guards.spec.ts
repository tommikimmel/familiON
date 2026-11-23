import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthStatus } from './auth.models';
import { AuthStore } from './auth.store';
import { guestGuard } from './guest.guard';

function createMockAuthStore(initialStatus: AuthStatus, initResult?: AuthStatus) {
  const statusSignal = signal<AuthStatus>(initialStatus);
  const init = vi.fn(async () => {
    if (initResult) {
      statusSignal.set(initResult);
    }
  });
  return {
    status: statusSignal.asReadonly(),
    init,
  };
}

function stateFor(url: string): RouterStateSnapshot {
  return { url } as RouterStateSnapshot;
}

describe('authGuard & guestGuard', () => {
  const emptyRoute = {} as ActivatedRouteSnapshot;

  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  describe('authGuard', () => {
    it('waits for init() and allows access when authenticated', async () => {
      const mockAuth = createMockAuthStore('unknown', 'authenticated');

      TestBed.configureTestingModule({
        providers: [provideRouter([]), { provide: AuthStore, useValue: mockAuth }],
      });

      const result = await TestBed.runInInjectionContext(() =>
        authGuard(emptyRoute, stateFor('/transactions?page=2')),
      );

      expect(mockAuth.init).toHaveBeenCalledTimes(1);
      expect(result).toBe(true);
    });

    it('redirects anonymous users to /auth/login preserving returnUrl', async () => {
      const mockAuth = createMockAuthStore('unknown', 'anonymous');

      TestBed.configureTestingModule({
        providers: [provideRouter([]), { provide: AuthStore, useValue: mockAuth }],
      });

      const router = TestBed.inject(Router);
      const result = await TestBed.runInInjectionContext(() =>
        authGuard(emptyRoute, stateFor('/vehicles/42?tab=fuel')),
      );

      expect(mockAuth.init).toHaveBeenCalledTimes(1);
      expect(result).toBeInstanceOf(UrlTree);
      expect(router.serializeUrl(result as UrlTree)).toBe(
        '/auth/login?returnUrl=%2Fvehicles%2F42%3Ftab%3Dfuel',
      );
    });
  });

  describe('guestGuard', () => {
    it('allows anonymous users on /auth/login', async () => {
      const mockAuth = createMockAuthStore('anonymous');

      TestBed.configureTestingModule({
        providers: [provideRouter([]), { provide: AuthStore, useValue: mockAuth }],
      });

      const result = await TestBed.runInInjectionContext(() =>
        guestGuard(emptyRoute, stateFor('/auth/login')),
      );

      expect(result).toBe(true);
    });

    it('redirects authenticated users from /auth/login to /home', async () => {
      const mockAuth = createMockAuthStore('authenticated');

      TestBed.configureTestingModule({
        providers: [provideRouter([]), { provide: AuthStore, useValue: mockAuth }],
      });

      const router = TestBed.inject(Router);
      const result = await TestBed.runInInjectionContext(() =>
        guestGuard(emptyRoute, stateFor('/auth/login')),
      );

      expect(result).toBeInstanceOf(UrlTree);
      expect(router.serializeUrl(result as UrlTree)).toBe('/home');
    });

    it('always allows /auth/invite even when authenticated', async () => {
      const mockAuth = createMockAuthStore('authenticated');

      TestBed.configureTestingModule({
        providers: [provideRouter([]), { provide: AuthStore, useValue: mockAuth }],
      });

      const result = await TestBed.runInInjectionContext(() =>
        guestGuard(emptyRoute, stateFor('/auth/invite?token=abc123')),
      );

      expect(result).toBe(true);
      expect(mockAuth.init).not.toHaveBeenCalled();
    });

    it('always allows /auth/reset-password even when authenticated', async () => {
      const mockAuth = createMockAuthStore('authenticated');

      TestBed.configureTestingModule({
        providers: [provideRouter([]), { provide: AuthStore, useValue: mockAuth }],
      });

      const result = await TestBed.runInInjectionContext(() =>
        guestGuard(emptyRoute, stateFor('/auth/reset-password?token=reset123')),
      );

      expect(result).toBe(true);
      expect(mockAuth.init).not.toHaveBeenCalled();
    });
  });
});
