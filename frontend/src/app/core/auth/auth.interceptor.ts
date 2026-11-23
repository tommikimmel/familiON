import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { BFF_BASE } from '../api/api-config';
import { AUTH_RETRIED, SKIP_AUTH_REDIRECT } from './auth.context';
import { AuthStore } from './auth.store';

const PROTECTED_BFF_PATTERN = /^\/bff\/(?:api(?:\/|$)|(?:home|plan|vehicles)(?:\/|$))/;

export function isProtectedBffUrl(url: string): boolean {
  const withoutHash = url.split('#')[0] ?? url;
  const withoutQuery = withoutHash.split('?')[0] ?? withoutHash;
  let path = withoutQuery;

  if (withoutQuery.startsWith('http://') || withoutQuery.startsWith('https://')) {
    try {
      path = new URL(withoutQuery).pathname;
    } catch {
      path = withoutQuery;
    }
  }

  if (path === '/bff/auth' || path.startsWith('/bff/auth/')) {
    return false;
  }

  return PROTECTED_BFF_PATTERN.test(path);
}

@Injectable({ providedIn: 'root' })
export class AuthRefreshService {
  private readonly http = inject(HttpClient);
  private inFlight$: Observable<void> | null = null;

  refresh(): Observable<void> {
    if (!this.inFlight$) {
      this.inFlight$ = this.http.post<void>(`${BFF_BASE}/auth/refresh`, null).pipe(
        finalize(() => {
          this.inFlight$ = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.inFlight$;
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isProtectedBffUrl(req.url) || req.context.get(AUTH_RETRIED)) {
    return next(req);
  }

  const refreshService = inject(AuthRefreshService);
  const authStore = inject(AuthStore);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }

      return refreshService.refresh().pipe(
        switchMap(() => {
          const retriedRequest = req.clone({
            context: req.context.set(AUTH_RETRIED, true),
          });
          return next(retriedRequest);
        }),
        catchError((refreshError: unknown) => {
          if (req.context.get(SKIP_AUTH_REDIRECT)) {
            authStore.clearLocalSession();
          } else {
            void authStore.logoutLocal(router.url);
          }
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
