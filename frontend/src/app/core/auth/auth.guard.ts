import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { sanitizeReturnUrl } from './auth.models';
import { AuthStore } from './auth.store';

export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  await auth.init();

  if (auth.status() === 'authenticated') {
    return true;
  }

  const returnUrl = sanitizeReturnUrl(state.url);
  return router.createUrlTree(['/auth/login'], {
    queryParams: returnUrl ? { returnUrl } : undefined,
  });
};
