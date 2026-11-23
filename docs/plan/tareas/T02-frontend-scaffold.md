# T02 — feat(frontend): scaffold Angular PWA + Tailwind + mapa de rutas

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 0 | 0.1 | `feat/frontend-scaffold` | — | T01, T03 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/brand/*.svg` (4 archivos, se copian).

## Objetivo
Crear `frontend/` con Angular (última versión estable, ≥19), Tailwind CSS v4, PWA, tokens de diseño claro/oscuro y **todas las rutas de la app con páginas placeholder**, para que cada tarea futura solo reemplace el contenido de su feature sin tocar archivos compartidos.

## Archivos permitidos
```
frontend/**
```

## Especificación

1. **Generar:** `npx @angular/cli@latest new frontend --style=css --routing --ssr=false --skip-git --package-manager=npm` (prefijo de componentes `fo`). Mantener el test runner por defecto del CLI.
2. **Tailwind v4:** `tailwindcss`, `@tailwindcss/postcss`, `postcss`; `.postcssrc.json` con `{"plugins":{"@tailwindcss/postcss":{}}}`; `src/styles.css` empieza con `@import "tailwindcss";`.
3. **Fuente:** `@fontsource-variable/inter` importada en `styles.css`; `font-family: 'Inter Variable', system-ui, sans-serif`.
4. **Tokens de diseño** en `src/styles.css`:
   ```css
   :root {
     --fo-bg:#FAFAF9; --fo-surface:#FFFFFF; --fo-surface-2:#F4F4F5; --fo-border:#E4E4E7;
     --fo-text:#18181B; --fo-text-muted:#71717A;
     --fo-accent:#0F766E; --fo-accent-contrast:#FFFFFF; --fo-accent-soft:color-mix(in srgb, var(--fo-accent) 12%, transparent);
     --fo-success:#16A34A; --fo-danger:#DC2626; --fo-warning:#D97706;
     --fo-radius:16px; --fo-ease-spring:cubic-bezier(0.34,1.35,0.64,1);
   }
   html.dark {
     --fo-bg:#0A0A0B; --fo-surface:#141416; --fo-surface-2:#1C1C1F; --fo-border:#27272A;
     --fo-text:#F4F4F5; --fo-text-muted:#A1A1AA;
   }
   @theme inline {
     --color-bg: var(--fo-bg); --color-surface: var(--fo-surface); --color-surface-2: var(--fo-surface-2);
     --color-border: var(--fo-border); --color-text: var(--fo-text); --color-muted: var(--fo-text-muted);
     --color-accent: var(--fo-accent); --color-accent-contrast: var(--fo-accent-contrast); --color-accent-soft: var(--fo-accent-soft);
     --color-success: var(--fo-success); --color-danger: var(--fo-danger); --color-warning: var(--fo-warning);
   }
   @media (prefers-reduced-motion: reduce) { *,*::before,*::after { animation-duration:.01ms!important; transition-duration:.01ms!important; } }
   ```
   `body` usa `bg-bg text-text` y `font-variant-numeric` normal (las cifras usan la clase `tabular-nums`).
5. **Brand:** copiar `docs/brand/*.svg` a `frontend/public/brand/`. Favicon = `familion-icon.svg`.
6. **PWA:** `ng add @angular/pwa`. Luego:
   - `public/manifest.webmanifest`: `name` y `short_name` `"FamiliON"`, `display:"standalone"`, `start_url:"/"`, `scope:"/"`, `theme_color:"#0F766E"`, `background_color:"#FAFAF9"`, íconos 72/96/128/144/152/192/384/512 + uno `"purpose":"maskable"` 512.
   - Generar PNGs desde `familion-icon.svg` con un script `frontend/scripts/generate-icons.mjs` (devDependency `sharp`) y commitear los PNG en `public/icons/`. Script npm `icons`.
   - `index.html`: `lang="es"`, `<meta name="theme-color">`, `apple-mobile-web-app-capable=yes`, `apple-mobile-web-app-status-bar-style=black-translucent`, `apple-mobile-web-app-title=FamiliON`, `<link rel="apple-touch-icon" href="icons/icon-192x192.png">`.
   - `ngsw-config.json`: assetGroup `app` (prefetch) y `assets` (lazy); dataGroup `bff-freshness` con `urls:["/bff/**"]`, `strategy:"freshness"`, `maxSize:50`, `maxAge:"1h"`, `timeout:"8s"`.
7. **app.config.ts:** `provideRouter(routes, withComponentInputBinding())`, `provideHttpClient(withFetch(), withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }))`, service worker (`registerWhenStable:30000`, deshabilitado en dev).
8. **Estructura y placeholders** (cada página: componente standalone OnPush con `<section class="p-4"><h1 class="text-xl font-semibold">{{Título}}</h1></section>`):
   ```
   src/app/core/api/api-config.ts            export const BFF_BASE = '/bff'; export const API = `${BFF_BASE}/api`;
   src/app/core/api/problem-detail.ts        interface ProblemDetail { type?; title?; status; detail?; errors?: {field:string;message:string}[] }
   src/app/core/auth/auth.guard.ts           export const authGuard: CanActivateFn = () => true;   // T12 lo implementa
   src/app/core/auth/guest.guard.ts          export const guestGuard: CanActivateFn = () => true;  // T12
   src/app/core/layout/shell.component.ts    <router-outlet/> (T08 lo implementa)
   src/app/core/layout/auth-layout.component.ts  contenedor centrado max-w-sm + logo + <router-outlet/>
   features/auth/auth.routes.ts      login, register, verify-email, 2fa, forgot-password, reset-password, invite
     pages/login.page.ts register.page.ts verify-email.page.ts two-factor.page.ts forgot-password.page.ts reset-password.page.ts accept-invite.page.ts
   features/home/home.routes.ts                pages/home.page.ts
   features/transactions/transactions.routes.ts pages/transactions.page.ts
   features/analytics/analytics.routes.ts      pages/analytics.page.ts
   features/accounts/accounts.routes.ts        pages/accounts.page.ts
   features/plan/plan.routes.ts                '' → overview/plan-overview.page.ts, installments → installments/installments.page.ts,
                                               recurring → recurring/recurring.page.ts, goals → goals/goals.page.ts,
                                               exchanges → exchanges/exchanges.page.ts, allowances → allowances/allowances.page.ts
   features/vehicles/vehicles.routes.ts        '' → pages/vehicles-list.page.ts, new → pages/vehicle-form.page.ts, ':id' → pages/vehicle-detail.page.ts,
                                               ':id/edit' → pages/vehicle-form.page.ts, ':id/fuel/new' → pages/fuel-log-form.page.ts,
                                               ':id/maintenance/new' → pages/maintenance-log-form.page.ts
   features/profile/profile.routes.ts          '' → pages/profile.page.ts, family → pages/family.page.ts, security → pages/security.page.ts
   features/notifications/notifications.routes.ts pages/notifications.page.ts
   ```
   `app.routes.ts`:
   ```ts
   [
     { path: 'auth', component: AuthLayoutComponent, canActivate: [guestGuard], loadChildren: () => import('./features/auth/auth.routes') },
     { path: '', component: ShellComponent, canActivate: [authGuard], children: [
       { path: '', pathMatch: 'full', redirectTo: 'home' },
       { path: 'home', loadChildren: ..., data: { title: 'Inicio' } },
       { path: 'transactions', ..., data: { title: 'Movimientos' } },
       { path: 'analytics', ..., data: { title: 'Analítica' } },
       { path: 'accounts', ..., data: { title: 'Cuentas' } },
       { path: 'plan', ..., data: { title: 'Planificación' } },
       { path: 'vehicles', ..., data: { title: 'Vehículos' } },
       { path: 'profile', ..., data: { title: 'Familia y Perfil' } },
       { path: 'notifications', ..., data: { title: 'Notificaciones' } },
     ]},
     { path: '**', redirectTo: 'home' }
   ]
   ```
   Cada `*.routes.ts` usa `export default` y `loadComponent` por página. Nota: `/auth/invite` debe ser accesible aun logueado → en `auth.routes.ts` no hay guard propio (el `guestGuard` del padre lo maneja T12).
9. **Proxy dev:** `proxy.conf.json` `{ "/bff": { "target": "http://localhost:8080", "secure": false } }` referenciado en `angular.json` (`serve.options.proxyConfig`).
10. **Calidad:** `ng add angular-eslint` (o `@angular-eslint/schematics`), Prettier (`.prettierrc` con `singleQuote`, `printWidth: 120`). Scripts npm: `start`, `build`, `test` (modo CI no-watch: `ng test --watch=false`), `lint`, `format`.
11. **Docker:** `frontend/Dockerfile` multi-stage `node:24-alpine` (`npm ci`, `npm run build -- --configuration production`) → `nginx:1.27-alpine` sirviendo `dist/frontend/browser` con `frontend/nginx.conf`: `try_files $uri $uri/ /index.html`, `index.html` y `ngsw.json` con `Cache-Control: no-cache`, assets con hash `public, max-age=31536000, immutable`, `server_tokens off`, escucha en `80`. Más `.dockerignore`.
12. Test: `app.routes.spec.ts` verificando que existen las rutas raíz (`auth`, `home`, `plan`, `vehicles`, `profile`, etc.) y que cada `loadChildren` resuelve.

## Fuera de alcance
Lógica de auth (T12), shell/navegación real (T08), componentes de UI (T08), cualquier llamada HTTP real.

## Criterios de aceptación
- [ ] `npm ci && npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Navegar a cada ruta de la tabla muestra su placeholder (verificado con `ng serve` o test).
- [ ] `manifest.webmanifest` + íconos PNG presentes; Lighthouse PWA "installable" (si se puede verificar localmente).
- [ ] Tokens CSS y clase `dark` funcionando (agregar `class="dark"` a `<html>` cambia superficies).
- [ ] `docker build -t familion-frontend ./frontend` OK.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
cd .. && docker build -t familion-frontend ./frontend
```

## Commits sugeridos
1. `chore(frontend): scaffold angular workspace with tailwind and eslint`
2. `feat(frontend): add design tokens, brand assets and pwa manifest`
3. `feat(frontend): add application route map with placeholder pages`
4. `chore(frontend): add production dockerfile and nginx config`
