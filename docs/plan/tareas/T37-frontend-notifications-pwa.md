# T37 — feat(frontend): centro de notificaciones, push, aviso de nueva versión e instalación PWA

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 5 | 5.1 | `feat/frontend-notifications-pwa` | Fase 4 completa | T36, T38 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/layout/notification-badge.service.ts` (T08), `core/layout/shell.component.*`, `shared/ui/*`, `ngsw-config.json`, `app.config.ts`.

## Objetivo
La campana con su centro de notificaciones, suscripción opcional a push, banner «Nueva versión disponible» con modal «Qué cambió», y un prompt discreto para instalar la app en el inicio del celular.

## Archivos permitidos
```
frontend/src/app/features/notifications/**
frontend/src/app/core/pwa/**                       (nuevo)
frontend/src/app/core/layout/shell.component.ts    (solo montar banners de PWA)
frontend/src/app/core/layout/shell.component.html  (si existe)
frontend/src/app/app.config.ts                     (solo si hace falta registrar providers de PWA)
```

## Contrato BFF

| Llamada | Respuesta |
|---|---|
| `GET /bff/api/notifications?unreadOnly=&page=&size=` | `{ content: Notification[], page }` |
| `GET /bff/api/notifications/unread-count` | `{ count }` |
| `PATCH /bff/api/notifications/{id}/read` | `Notification` |
| `POST /bff/api/notifications/read-all` | `204` |
| `GET /bff/api/notifications/push-config` | `{ publicKey: string|null }` |
| `POST /bff/api/notifications/push-subscriptions` | body `{ endpoint, keys: { p256dh, auth }, userAgent }` → `201` |
| `DELETE /bff/api/notifications/push-subscriptions` | body `{ endpoint }` → `204` |
| `GET /bff/api/system/version` | `{ version, latestRelease: { version, title, releasedAt } | null }` |
| `GET /bff/api/system/changelog` | `{ version, title, changelogMarkdown, isBreaking, releasedAt }[]` |

```ts
type Notification = { id; category: 'APP_UPDATE'|'EXPENSE_ATTRIBUTED'|'ALLOWANCE'|'FIXED_EXPENSE_DUE'|'VEHICLE_ALERT'|'SYSTEM';
  title; body; metadata: { link?: string; [k: string]: unknown } | null; readAt: string|null; createdAt: string };
```

## Especificación
1. **Badge:** servicio que consulta `unread-count` al iniciar sesión, cada 60 s con la app visible (`document.visibilitychange`) y tras marcar leídas → `NotificationBadgeService.set(n)`.
2. **`/notifications`:** lista agrupada (Hoy / Esta semana / Anteriores), ícono por categoría, no leídas con punto de acento; tocar → marca leída y navega a `metadata.link`; «Marcar todas como leídas»; filtro «Solo no leídas»; scroll infinito.
3. **Push** (sección en la misma página): switch «Avisos en este dispositivo». Solo visible si `SwPush.isEnabled` y `publicKey != null`. Activar → `SwPush.requestSubscription({ serverPublicKey })` → POST; desactivar → `unsubscribe` + DELETE. Manejar permiso denegado con mensaje. Al tocar una push (`SwPush.notificationClicks`) navegar a `link`.
4. **Nueva versión** (`core/pwa/app-update.service.ts`): `SwUpdate.versionUpdates` (`VERSION_READY`) → banner mínimo en el shell: «FamiliON v{version} disponible — Actualizar ahora» (versión de `/system/version`) + link «Qué cambió» (modal que renderiza el `changelogMarkdown` de las versiones nuevas con un renderer Markdown mínimo propio o `marked` — si se agrega dependencia, justificar; preferir propio para títulos, listas y negritas). «Actualizar ahora» → `activateUpdate()` + `location.reload()`. Chequear updates cada 6 h y al volver a foreground. `unrecoverable` → recargar.
5. **Prompt de instalación** (`core/pwa/install-prompt.service.ts`): Android/Chrome: capturar `beforeinstallprompt` y mostrar banner discreto «Agrega FamiliON a tu inicio» con botón «Instalar». iOS Safari no standalone: mini-guía de 2 pasos («Toca Compartir» → «Agregar a inicio») con íconos. Mostrar solo una vez cada 30 días (`localStorage fo.installPromptDismissedAt`), nunca si ya está en `display-mode: standalone`.
6. Splash: al iniciar en modo standalone, animación del `fo-logo` OFF → ON (≤ 600 ms, respetar reduced-motion).

## Tests
- Store de notificaciones (paginación, marcar leída, read-all actualiza badge).
- `AppUpdateService` con `SwUpdate` mockeado emite banner en `VERSION_READY`.
- `InstallPromptService`: no muestra en standalone; respeta los 30 días.
- Renderer Markdown mínimo (títulos, listas, negrita, escape de HTML).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Sin `innerHTML` con contenido no sanitizado.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(notifications-ui): add notification center with unread badge`
2. `feat(notifications-ui): add web push opt-in`
3. `feat(pwa): add update banner with changelog and install prompt`
