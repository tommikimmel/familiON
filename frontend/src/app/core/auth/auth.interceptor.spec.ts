import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SessionStore } from '../state/session.store';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from './auth.store';

@Component({ selector: 'fo-test-login', standalone: true, template: '<p>Login</p>' })
class DummyLoginComponent {}

@Component({ selector: 'fo-test-transactions', standalone: true, template: '<p>Transactions</p>' })
class DummyTransactionsComponent {}

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;
  let authStore: AuthStore;
  let session: SessionStore;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'auth/login', component: DummyLoginComponent },
          { path: 'transactions', component: DummyTransactionsComponent },
        ]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    authStore = TestBed.inject(AuthStore);
    session = TestBed.inject(SessionStore);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('refreshes once on 401 for /bff/api/** and retries the original request', () => {
    let result: unknown;
    http.get('/bff/api/accounts').subscribe((data) => {
      result = data;
    });

    httpMock
      .expectOne('/bff/api/accounts')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne('/bff/auth/refresh');
    expect(refreshReq.request.method).toBe('POST');
    refreshReq.flush(null, { status: 204, statusText: 'No Content' });

    const retryReq = httpMock.expectOne('/bff/api/accounts');
    retryReq.flush({ items: ['acc-1'] });

    expect(result).toEqual({ items: ['acc-1'] });
  });

  it('shares a single refresh request across concurrent 401 responses (single-flight)', () => {
    let homeResult: unknown;
    let planResult: unknown;

    http.get('/bff/home?view=PERSONAL').subscribe((data) => {
      homeResult = data;
    });
    http.get('/bff/plan').subscribe((data) => {
      planResult = data;
    });

    httpMock
      .expectOne('/bff/home?view=PERSONAL')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    httpMock
      .expectOne('/bff/plan')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    const refreshReqs = httpMock.match('/bff/auth/refresh');
    expect(refreshReqs.length).toBe(1);
    refreshReqs[0].flush(null, { status: 204, statusText: 'No Content' });

    httpMock.expectOne('/bff/home?view=PERSONAL').flush({ netWorth: 1000 });
    httpMock.expectOne('/bff/plan').flush({ goals: [] });

    expect(homeResult).toEqual({ netWorth: 1000 });
    expect(planResult).toEqual({ goals: [] });
  });

  it('clears local session without calling /bff/auth/logout and redirects when refresh fails', async () => {
    await router.navigateByUrl('/transactions');
    const navSpy = vi.spyOn(router, 'navigate');

    let caughtError: HttpErrorResponse | undefined;
    http.get('/bff/vehicles').subscribe({
      error: (err: HttpErrorResponse) => {
        caughtError = err;
      },
    });

    httpMock
      .expectOne('/bff/vehicles')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    httpMock
      .expectOne('/bff/auth/refresh')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(caughtError?.status).toBe(401);
    expect(session.me()).toBeNull();
    expect(authStore.status()).toBe('anonymous');
    expect(httpMock.match('/bff/auth/logout').length).toBe(0);
    expect(navSpy).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/transactions' },
    });
  });

  it('does not intercept /bff/auth/** requests on 401', () => {
    let caughtError: HttpErrorResponse | undefined;
    http.post('/bff/auth/login', { email: 'a@b.com', password: 'bad' }).subscribe({
      error: (err: HttpErrorResponse) => {
        caughtError = err;
      },
    });

    httpMock
      .expectOne('/bff/auth/login')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(caughtError?.status).toBe(401);
    expect(httpMock.match('/bff/auth/refresh').length).toBe(0);
  });

  it('retries at most once when the retried request also returns 401', () => {
    let caughtError: HttpErrorResponse | undefined;
    http.get('/bff/api/transactions').subscribe({
      error: (err: HttpErrorResponse) => {
        caughtError = err;
      },
    });

    httpMock
      .expectOne('/bff/api/transactions')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    httpMock
      .expectOne('/bff/auth/refresh')
      .flush(null, { status: 204, statusText: 'No Content' });

    httpMock
      .expectOne('/bff/api/transactions')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(caughtError?.status).toBe(401);
    expect(httpMock.match('/bff/auth/refresh').length).toBe(0);
  });
});
