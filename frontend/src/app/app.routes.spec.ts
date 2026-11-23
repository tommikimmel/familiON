import { TestBed } from '@angular/core/testing';
import { DefaultExport, provideRouter, Route, Routes } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';
import { AuthLayoutComponent } from './core/layout/auth-layout.component';
import { ShellComponent } from './core/layout/shell.component';

async function resolveLazyRoutes(route: Route): Promise<Routes> {
  if (!route.loadChildren) {
    return [];
  }
  const loaded = await route.loadChildren();
  if (Array.isArray(loaded)) {
    return loaded;
  }
  const exported = (loaded as DefaultExport<Routes>).default;
  return Array.isArray(exported) ? exported : [];
}

describe('Application routes', () => {
  it('defines auth, shell, and wildcard root routes with expected guards and titles', () => {
    const authRoute = routes.find((r) => r.path === 'auth');
    expect(authRoute).toBeDefined();
    expect(authRoute?.component).toBe(AuthLayoutComponent);
    expect(authRoute?.canActivate).toContain(guestGuard);
    expect(authRoute?.loadChildren).toBeTypeOf('function');

    const shellRoute = routes.find((r) => r.path === '');
    expect(shellRoute).toBeDefined();
    expect(shellRoute?.component).toBe(ShellComponent);
    expect(shellRoute?.canActivate).toContain(authGuard);

    const expectedShellChildren: Record<string, string> = {
      home: 'Inicio',
      transactions: 'Movimientos',
      analytics: 'Analítica',
      accounts: 'Cuentas',
      plan: 'Planificación',
      vehicles: 'Vehículos',
      profile: 'Familia y Perfil',
      notifications: 'Notificaciones',
    };

    for (const [path, title] of Object.entries(expectedShellChildren)) {
      const child = shellRoute?.children?.find((r) => r.path === path);
      expect(child, `Missing shell child route '${path}'`).toBeDefined();
      expect(child?.data?.['title']).toBe(title);
      expect(child?.loadChildren).toBeTypeOf('function');
    }

    const wildcard = routes.find((r) => r.path === '**');
    expect(wildcard?.redirectTo).toBe('home');
  });

  it('resolves every loadChildren and loadComponent entry cleanly', async () => {
    const authRoute = routes.find((r) => r.path === 'auth');
    const shellRoute = routes.find((r) => r.path === '');
    const lazyParents: Route[] = [
      ...(authRoute ? [authRoute] : []),
      ...(shellRoute?.children?.filter((r) => Boolean(r.loadChildren)) ?? []),
    ];

    expect(lazyParents.length).toBe(9);

    for (const parent of lazyParents) {
      const childRoutes = await resolveLazyRoutes(parent);
      expect(childRoutes.length, `Route '${parent.path}' resolved to empty children`).toBeGreaterThan(0);

      for (const child of childRoutes) {
        if (child.loadComponent) {
          const component = await child.loadComponent();
          expect(component, `Route '${parent.path}/${child.path}' failed to load component`).toBeTruthy();
        }
      }
    }
  });

  it('keeps /auth/invite free of child guards', async () => {
    const authRoute = routes.find((r) => r.path === 'auth');
    const authChildren = await resolveLazyRoutes(authRoute!);
    const inviteRoute = authChildren.find((r) => r.path === 'invite');
    expect(inviteRoute).toBeDefined();
    expect(inviteRoute?.canActivate).toBeUndefined();
  });

  describe('navigation to every route renders its placeholder heading', () => {
    let harness: RouterTestingHarness;

    beforeEach(async () => {
      TestBed.configureTestingModule({
        providers: [provideRouter(routes)],
      });
      harness = await RouterTestingHarness.create();
    });

    const cases: { url: string; heading: string }[] = [
      { url: '/', heading: 'Inicio' },
      { url: '/home', heading: 'Inicio' },
      { url: '/transactions', heading: 'Movimientos' },
      { url: '/analytics', heading: 'Analítica' },
      { url: '/accounts', heading: 'Cuentas' },
      { url: '/plan', heading: 'Planificación' },
      { url: '/plan/installments', heading: 'Cuotas' },
      { url: '/plan/recurring', heading: 'Recurrentes' },
      { url: '/plan/goals', heading: 'Metas' },
      { url: '/plan/exchanges', heading: 'Cambio de moneda' },
      { url: '/plan/allowances', heading: 'Mensualidades' },
      { url: '/vehicles', heading: 'Vehículos' },
      { url: '/vehicles/new', heading: 'Vehículo' },
      { url: '/vehicles/42', heading: 'Detalle del vehículo' },
      { url: '/vehicles/42/edit', heading: 'Vehículo' },
      { url: '/vehicles/42/fuel/new', heading: 'Cargar combustible' },
      { url: '/vehicles/42/maintenance/new', heading: 'Registrar mantenimiento' },
      { url: '/profile', heading: 'Familia y Perfil' },
      { url: '/profile/family', heading: 'Familia' },
      { url: '/profile/security', heading: 'Seguridad' },
      { url: '/notifications', heading: 'Notificaciones' },
      { url: '/auth/login', heading: 'Iniciar sesión' },
      { url: '/auth/register', heading: 'Crear cuenta' },
      { url: '/auth/verify-email', heading: 'Verificar correo' },
      { url: '/auth/2fa', heading: 'Verificación en dos pasos' },
      { url: '/auth/forgot-password', heading: 'Recuperar contraseña' },
      { url: '/auth/reset-password', heading: 'Restablecer contraseña' },
      { url: '/auth/invite', heading: 'Aceptar invitación' },
      { url: '/non-existent-route', heading: 'Inicio' },
    ];

    for (const { url, heading } of cases) {
      it(`renders '${heading}' at ${url}`, async () => {
        await harness.navigateByUrl(url);
        const h1 = harness.routeNativeElement?.querySelector('h1');
        expect(h1?.textContent?.trim()).toBe(heading);
      });
    }
  });
});
