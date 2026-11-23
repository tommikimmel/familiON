import { HttpContextToken } from '@angular/common/http';

/** Marks a request that has already been retried after a token refresh. */
export const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);

/** Prevents the auth interceptor from forcing navigation to /auth/login on refresh failure. */
export const SKIP_AUTH_REDIRECT = new HttpContextToken<boolean>(() => false);
