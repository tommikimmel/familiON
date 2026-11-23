# T36 — feat(notifications): notificaciones in-app, Web Push, versión/changelog y alertas programadas

| Fase | Ola | Rama | Requiere mergeado | Paralela con | Migración reservada |
|---|---|---|---|---|---|
| 5 | 5.1 | `feat/backend-notifications` | Fase 4 completa | T37, T38 | `V36` |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **405–438** (`notifications`, `push_subscriptions`, `app_releases`).
- Eventos/APIs a consumir: `ExpenseAttributedEvent` (T16, `finance.transactions.application.api`), `AllowanceDisbursedEvent` (T28, `family.allowances.application.api`), `RecurringExpenseQueryApi` (T26), `VehicleAlertQueryApi` (T33), `FamilyQueryApi` (T07).

## Objetivo
Centro de notificaciones por usuario (campana), push opcional al celular con la PWA instalada, endpoints de versión y changelog para el aviso «Qué cambió», y generación automática de avisos del hogar.

## Archivos permitidos
```
backend/pom.xml                                   (solo agregar nl.martijndwars:web-push y org.bouncycastle:bcprov-jdk18on)
backend/src/main/java/com/familion/notifications/**
backend/src/main/resources/db/migration/V36__seed_app_releases.sql
backend/src/test/java/com/familion/notifications/**
```
> `GET /api/v1/system/version` ya es público en `SecurityConfig` (T06).

## Contrato

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/api/v1/notifications?unreadOnly=false&page=&size=` | auth | `Page<Notification>` (desc) |
| GET | `/api/v1/notifications/unread-count` | auth | `{ count: number }` |
| PATCH | `/api/v1/notifications/{id}/read` | dueño | `Notification` |
| POST | `/api/v1/notifications/read-all` | auth | `204` |
| GET | `/api/v1/notifications/push-config` | auth | `{ publicKey: string|null }` |
| POST | `/api/v1/notifications/push-subscriptions` | auth | body `{ endpoint, keys: { p256dh, auth }, userAgent? }` → `201` |
| DELETE | `/api/v1/notifications/push-subscriptions` | auth | body `{ endpoint }` → `204` |
| GET | `/api/v1/system/version` | público | `{ version, latestRelease: { version, title, releasedAt } | null }` |
| GET | `/api/v1/system/changelog` | auth | `{ version, title, changelogMarkdown, isBreaking, releasedAt }[]` (desc) |

```ts
type Notification = { id; category: 'APP_UPDATE'|'EXPENSE_ATTRIBUTED'|'ALLOWANCE'|'FIXED_EXPENSE_DUE'|'VEHICLE_ALERT'|'SYSTEM';
  title: string; body: string; metadata: Record<string, unknown>|null; readAt: string|null; createdAt: string };
```
`metadata` incluye siempre `link` (ruta del front, ej. `/transactions?id=...`, `/plan/recurring`, `/vehicles/{id}`) y `dedupeKey`.

## Especificación
1. **`NotificationService.notify(userId, familyId, category, title, body, metadata)`**: deduplica por `metadata.dedupeKey` (no crear si ya existe una con el mismo `dedupeKey` para ese usuario) y, si hay suscripciones push activas y VAPID configurado, envía push (`web-push` lib, payload JSON `{ title, body, link }`); respuesta `404/410` del push service → desactivar la suscripción. Envío push asíncrono, nunca rompe la transacción.
2. **Listeners:**
   - `ExpenseAttributedEvent` (AFTER_COMMIT) → al atribuido: «{Pagador} registró {monto} a tu nombre» (ej. «Tomás registró $20.000 a tu nombre»), link al movimiento.
   - `AllowanceDisbursedEvent` → al hijo: «Recibiste tu mensualidad de {monto}».
3. **Schedulers** (`zone = "America/Argentina/Buenos_Aires"`):
   - 08:00 diario: `RecurringExpenseQueryApi.pendingDueBetween(hoy, hoy+3)` → a todos los ADULTO de la familia: «Vence {nombre} el {dd/MM}: {monto estimado}» (`dedupeKey = fixed:{id}:{yyyy-MM}`).
   - 08:05 diario: `VehicleAlertQueryApi.activeAlerts()` → a ADULTO: título de la alerta (`dedupeKey = vehicle:{vehicleId}:{kind}:{dueDate|dueKm}`).
4. **APP_UPDATE:** en `ApplicationReadyEvent`, si existe `app_releases` con `version = familion.app.version` y aún no se notificó (`dedupeKey = release:{version}`), crear `APP_UPDATE` para todos los usuarios activos: «FamiliON v{version}: {title}».
5. **`V36`:** seed de `app_releases` para `0.1.0` («Base del proyecto»), `0.2.0` («Finanzas y motor anti-inflación»), `0.3.0` («Cuotas, gastos fijos, metas y mensualidades»), `0.4.0` («Vehículos»), `0.5.0` («Notificaciones y PWA»), con changelog en Markdown escrito para la familia (3–5 bullets simples cada uno).
6. **VAPID:** `@Value("${VAPID_PUBLIC_KEY:}")`, `${VAPID_PRIVATE_KEY:}`, `${VAPID_SUBJECT:mailto:admin@familion.local}`; vacíos → push deshabilitado (`push-config.publicKey = null`), resto funciona. Documentar en `notifications/README.md` cómo generar claves (`npx web-push generate-vapid-keys`).

## Tests
- Dedupe; marcar leída (de otro usuario → 404); read-all; unread-count.
- Listener de atribución crea notificación al hijo tras commit (y no si hay rollback).
- Schedulers con `Clock` fijo y APIs mockeadas (`@MockitoBean`) generan notificaciones una sola vez.
- APP_UPDATE al arrancar con versión sembrada.
- Push: con VAPID vacío no intenta enviar; cliente push mockeado recibe payload correcto; 410 desactiva suscripción.
- `/system/version` sin token → 200.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Ninguna notificación duplicada por re-ejecución de schedulers.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(notifications): add in-app notifications with dedupe and read state`
2. `feat(notifications): add web push subscriptions with vapid`
3. `feat(notifications): add system version and changelog endpoints`
4. `feat(notifications): add domain event listeners and daily alert jobs`
