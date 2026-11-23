# T12 — feat(frontend): autenticación (pantallas, guards, refresh automático y sesión)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 1 | 1.2 | `feat/frontend-auth` | Ola 1.1 (T08) | T10, T11 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `frontend/src/app/core/state/session.store.ts`, `core/theme/theme.service.ts`, `core/api/models/me.model.ts`, `shared/ui/*` (T08).
- `frontend/src/app/features/auth/**` y `core/auth/*.guard.ts` (placeholders de T02).

## Objetivo
Flujos completos de acceso contra el BFF: login (con 2FA), registro + verificación de email, olvido/reset de contraseña y aceptación de invitaciones. Guards reales, refresh transparente ante `401` y carga de sesión al iniciar la app.

## Archivos permitidos
```
frontend/src/app/features/auth/**
frontend/src/app/core/auth/**
frontend/src/app/app.config.ts           (registrar interceptor e initializer)
```

## Contrato BFF (todas las llamadas con `withCredentials` implícito: mismo origen)

| Llamada | Request | Respuesta |
|---|---|---|
| `GET /bff/csrf` | — | `204` (setea `XSRF-TOKEN`) |
| `POST /bff/auth/login` | `{ email, password }` | `{ requires2fa: false }` o `{ requires2fa: true, challengeToken }` |
| `POST /bff/auth/2fa/verify` | `{ challengeToken, code, trustDevice, deviceName? }` | `{ requires2fa: false }` |
| `POST /bff/auth/2fa/resend` | `{ challengeToken }` | `202` · `429` con `Retry-After` |
| `POST /bff/auth/register` | `{ email, password, fullName, familyName }` | `201 { userId, email }` |
| `POST /bff/auth/verify-email` | `{ email, code }` | `204` |
| `POST /bff/auth/forgot-password` | `{ email }` | `202` |
| `POST /bff/auth/reset-password` | `{ token, newPassword }` | `204` |
| `GET /bff/api/auth/invitations/{token}` | — | `{ email, role, familyName, expiresAt }` · `404` |
| `POST /bff/auth/register-invite` | `{ token, fullName, password }` | `{ requires2fa: false }` |
| `POST /bff/auth/refresh` | — | `204` · `401` |
| `POST /bff/auth/logout` | — | `204` |
| `GET /bff/api/auth/me` | — | `Me` (modelo en `core/api/models/me.model.ts`) |

Errores: `application/problem+json` `{ status, title, detail, errors?: [{field, message}] }`.

## Especificación

1. **`AuthStore`** (`core/auth/auth.store.ts`, signals): `status: 'unknown'|'authenticated'|'anonymous'`, `pendingChallenge: string|null`.
   - `init(): Promise<void>` → `GET /bff/csrf` y luego `GET /bff/api/auth/me`; OK → `SessionStore.setMe(me)`, `ThemeService.apply(me)`, `authenticated`; `401` → `anonymous`. Registrar con `provideAppInitializer(() => inject(AuthStore).init())`.
   - `login(email, password)`, `verify2fa(code, trustDevice)`, `register(...)`, `verifyEmail(email, code)`, `forgotPassword(email)`, `resetPassword(token, pwd)`, `acceptInvite(token, fullName, pwd)`, `logout()` (llama BFF, `SessionStore.setMe(null)`, navega a `/auth/login`), `reloadMe()`.
2. **`authInterceptor`** (`core/auth/auth.interceptor.ts`, funcional): ante `401` en `/bff/api/**` o `/bff/home|plan|vehicles` → `POST /bff/auth/refresh` **single-flight** (requests concurrentes esperan el mismo refresh) → reintenta una vez; si el refresh falla → `logout()` local sin llamada extra y redirección a `/auth/login?returnUrl=...`. No interceptar `/bff/auth/**`.
3. **Guards:** `authGuard` espera `init()` y permite si `authenticated`, si no redirige a `/auth/login?returnUrl`. `guestGuard` redirige a `/home` si ya está autenticado, **excepto** `/auth/invite` y `/auth/reset-password` (siempre accesibles).
4. **Pantallas** (UI en español, mobile-first, `AuthLayoutComponent` de T02, componentes de `shared/ui`):
   - `login`: email, contraseña (mostrar/ocultar), «Ingresar», links «¿Olvidaste tu contraseña?» y «Crear cuenta familiar». Si `requires2fa` → navegar a `/auth/2fa`. `429` → mostrar «Demasiados intentos. Intenta de nuevo en N minutos.».
   - `register`: nombre completo, nombre de la familia, email, contraseña + confirmación (validación ≥10, letra y número, coinciden). OK → `/auth/verify-email?email=...`.
   - `verify-email`: 6 cajas de dígito (pegado de 6 dígitos completa todas; autoavance; teclado numérico `inputmode="numeric"`), «Verificar». OK → toast «Correo verificado» y `/auth/login`.
   - `two-factor`: 6 cajas, checkbox «Confiar en este dispositivo por 30 días» (activo por defecto), «Reenviar código» con cuenta regresiva de 60 s (usar `Retry-After` si llega `429`). Sin `pendingChallenge` → volver a login.
   - `forgot-password`: email → mensaje neutro «Si el correo está registrado, recibirás un enlace para restablecer la contraseña.».
   - `reset-password`: lee `token` de query; nueva contraseña + confirmación; OK → login con toast.
   - `accept-invite`: lee `token`; muestra «Te invitaron a la familia {familyName} como {Adulto|Hijo/a}» con el email; nombre completo + contraseña → logueado → `/home`. Token inválido → estado de error con link a login.
5. Mostrar errores de validación del backend (`errors[]`) bajo cada campo; errores generales en un banner.
6. `returnUrl` respetado tras login.

## Tests
- `auth.store.spec.ts` con `HttpTestingController`: init autenticado/anónimo, login con y sin 2FA, logout limpia sesión.
- `auth.interceptor.spec.ts`: 401 → refresh → reintento OK; dos 401 concurrentes → **un** solo refresh; refresh falla → redirección.
- Guards: redirecciones esperadas.
- Componente de código OTP: pegado de 6 dígitos y autoavance.

## Fuera de alcance
Perfil/seguridad (T14). Backend.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Ningún token en `localStorage`/`sessionStorage`.
- [ ] Formularios accesibles (labels, `autocomplete="email|current-password|new-password|one-time-code"`).

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(auth-ui): add auth store, app initializer and guards`
2. `feat(auth-ui): add refresh-on-401 interceptor with single-flight`
3. `feat(auth-ui): add login, two-factor and registration screens`
4. `feat(auth-ui): add password recovery and invitation acceptance screens`
