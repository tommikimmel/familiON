import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth.store';

const ALWAYS_ACCESSIBLE_AUTH_PATHS = new Set(['/auth/invite', '/auth/reset-password']);

function extractPathname(url: string): string {
  const withoutQuery = (url.split('?')[0] ?? url).split('#')[0] ?? url;
  return withoutQuery.replace(/\/+$/, '');
}

export const guestGuard: CanActivateFn = async (_route, state) => {
  if (ALWAYS_ACCESSIBLE_AUTH_PATHS.has(extractPathname(state.url))) {
    return true;
  }

  const auth = inject(AuthStore);
  const router = inject(Router);

  await auth.init();

  if (auth.status() === 'authenticated') {
    return router.createUrlTree(['/home']);
  }

  return true;
};
