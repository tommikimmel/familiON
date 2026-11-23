# Contratos Globales (fuente de verdad)

Las tarjetas copian el fragmento que necesitan. Si hay diferencia entre una tarjeta y este archivo, **gana este archivo** y se reporta en la PR.

---

## C1. Servicios, puertos y variables de entorno

| Servicio compose | Imagen / build | Puerto interno | Expuesto al host (dev) | Redes |
|---|---|---|---|---|
| `edge` | `nginx:1.27-alpine` + `edge/nginx.conf` | 80 | `8088:80` | `public_net`, `internal_net` |
| `frontend` | build `./frontend` (Nginx sirviendo SPA) | 80 | no | `internal_net` |
| `bff` | build `./bff` | 8080 | no (`8080` solo con override dev) | `internal_net` |
| `backend` | build `./backend` | 8081 | no | `internal_net` |
| `postgres` | `postgres:17-alpine` | 5432 | `5432` solo en `docker-compose.override.yml` | `internal_net` |
| `redis` | `redis:7-alpine` | 6379 | no | `internal_net` |
| `mailpit` | `axllent/mailpit:latest` | 1025 SMTP / 8025 UI | `8025:8025` | `internal_net` |

Ruteo del edge: `/bff/` → `bff:8080`; todo lo demás → `frontend:80`.

Desarrollo sin Docker: `ng serve` en `4200` con `proxy.conf.json` (`/bff` → `http://localhost:8080`); BFF en `8080`; Core en `8081`.

**Variables de entorno** (nombres fijos):

| Componente | Variable | Ejemplo dev |
|---|---|---|
| Postgres | `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `familion` / `familion` / `familion` |
| Core | `SPRING_PROFILES_ACTIVE` | `dev` |
| Core | `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://postgres:5432/familion` / `familion` / `familion` |
| Core + BFF | `REDIS_HOST` / `REDIS_PORT` | `redis` / `6379` |
| Core | `JWT_SECRET` | base64 de ≥ 32 bytes |
| Core | `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `PT15M` / `P30D` |
| Core | `MAIL_HOST` / `MAIL_PORT` / `MAIL_FROM` | `mailpit` / `1025` / `no-reply@familion.local` |
| Core | `APP_PUBLIC_URL` | `http://localhost:8088` |
| Core | `APP_VERSION` | `0.1.0` |
| Core | `ECON_DOLARAPI_URL` / `ECON_ARGDATOS_URL` | `https://dolarapi.com` / `https://api.argentinadatos.com` |
| Core | `ECON_SYNC_CRON` | `0 0 */6 * * *` |
| Core | `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | (vacío en dev) / `mailto:admin@familion.local` |
| Core | `RECEIPTS_STORAGE_DIR` | `/data/receipts` |
| BFF | `CORE_API_URL` | `http://backend:8081` |
| BFF | `COOKIE_SECURE` | `false` en dev, `true` en prod |
| BFF | `BFF_RATE_LIMIT_PER_MIN` | `120` |

---

## C2. Autenticación (Core ↔ BFF ↔ Frontend)

- **Core** emite JWT HS256 (`JWT_SECRET`) y valida con Spring Security OAuth2 Resource Server.
  - Claims: `sub` = userId (UUID), `fam` = familyId, `role` = `ADULTO|HIJO`, `vis` = familyVisibility, `jti` = UUID, `typ` = `access`, `exp` = 15 min.
  - Authorities: `ROLE_ADULTO` o `ROLE_HIJO` (a partir del claim `role`).
  - Revocación inmediata: clave Redis `auth:revoked:{jti}` con TTL = vida restante del token.
- **Respuesta de tokens del Core** (`/api/v1/auth/login`, `/auth/2fa/verify`, `/auth/refresh`, `/auth/register-invite`):
  ```json
  { "requires2fa": false, "challengeToken": null,
    "accessToken": "...", "accessTokenExpiresIn": 900,
    "refreshToken": "...", "refreshTokenExpiresIn": 2592000 }
  ```
  Si hay 2FA pendiente: `{ "requires2fa": true, "challengeToken": "...", resto null }`.
- **Core `/auth/refresh`** body: `{ "refreshToken": "..." }`. **Core `/auth/logout`** (Bearer) body: `{ "refreshToken": "..." }`.
- **BFF** nunca devuelve tokens al navegador. Cookies:

| Cookie | Atributos | Contenido |
|---|---|---|
| `FAMILION_AT` | `HttpOnly; Secure(=COOKIE_SECURE); SameSite=Strict; Path=/bff; Max-Age=900` | access token |
| `FAMILION_RT` | `HttpOnly; Secure; SameSite=Strict; Path=/bff/auth; Max-Age=2592000` | refresh token |
| `XSRF-TOKEN` | `Secure; SameSite=Strict; Path=/` (legible por JS) | token CSRF (Spring `CookieCsrfTokenRepository`) |
| `FAMILION_DT` | `HttpOnly; Secure; SameSite=Strict; Path=/bff/auth; Max-Age=2592000` | token de dispositivo confiable (2FA) |

- **Dispositivo confiable:** si la respuesta del Core a `/auth/2fa/verify` incluye `"deviceToken": "..."`, el BFF lo guarda en `FAMILION_DT` (no lo devuelve). En cada `POST /bff/auth/login`, si existe la cookie, el BFF agrega `"deviceToken"` al body enviado al Core.
- **Tokens de challenge 2FA:** son JWT con claim `typ = "2fa"`; el Resource Server del Core **solo** acepta tokens con `typ = "access"`.

- Header CSRF: `X-XSRF-TOKEN` en todo método no-GET bajo `/bff/**`.

**Rutas del BFF** (todas bajo `/bff`):

| Ruta BFF | Hacia Core | Comportamiento |
|---|---|---|
| `POST /bff/auth/login`, `/bff/auth/2fa/verify`, `/bff/auth/register-invite` | `/api/v1/auth/...` | Si la respuesta trae tokens → setea cookies y responde `{ "requires2fa": false }`; si `requires2fa` → `{ "requires2fa": true, "challengeToken": "..." }`. |
| `POST /bff/auth/refresh` | `/api/v1/auth/refresh` | Lee `FAMILION_RT`, rota, re-setea cookies, `204`. Sin cookie → `401`. |
| `POST /bff/auth/logout` | `/api/v1/auth/logout` | Llama al Core con Bearer + RT, borra cookies, `204`. |
| `POST /bff/auth/{register,verify-email,2fa/resend,forgot-password,reset-password}` | `/api/v1/auth/...` | Passthrough (sin cookies). |
| `GET /bff/csrf` | — | `204`; fuerza emisión de `XSRF-TOKEN`. |
| `ANY /bff/api/**` | `/api/v1/**` | Proxy genérico: agrega `Authorization: Bearer <FAMILION_AT>`, propaga query, body, status y `Content-Type`. |
| `GET /bff/home` | varias | Agregación (T21). |
| `GET /bff/plan` | varias | Agregación (T29). |
| `GET /bff/vehicles/{id}/dashboard` | varias | Agregación (T35). |

Frontend: ante `401` de `/bff/api/**` o agregaciones → `POST /bff/auth/refresh` una sola vez y reintenta; si falla → `/auth/login`.

---

## C3. Convenciones de la API REST del Core

- Base: `/api/v1`. JSON camelCase.
- **Montos y tasas se serializan como string decimal plano** (`"36000.0000"`, `"1185.500000"`). Se aceptan como string o número en requests. (Jackson: `BigDecimal` → `ToStringSerializer`, `WRITE_BIGDECIMAL_AS_PLAIN`.)
- Fechas `yyyy-MM-dd`; timestamps ISO-8601 UTC (`2026-11-23T12:00:00Z`).
- Errores: `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y, en validación, `errors: [{ field, message }]`.
- Paginación: `?page=0&size=20&sort=transactionDate,desc` (size máx 50). Respuesta: `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`.
- Parámetros globales: `view=PERSONAL|FAMILY`, `lens=NOMINAL|CONSTANT_BASE|REFERENCE_CURRENCY|INCOME_PCT`, `granularity=DAY|WEEK|MONTH|YEAR`, `date=yyyy-MM-dd` (fecha ancla del período; default hoy en la zona horaria de la familia).
- Endpoints públicos (sin token): `/api/v1/auth/{register,register-invite,verify-email,login,2fa/verify,2fa/resend,forgot-password,reset-password,refresh}`, `/api/v1/system/version`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`.

---

## C4. Reglas de visibilidad y vistas

`VisibilityPolicy` (en `com.familion.common.security`, T06):

| Usuario | `view=PERSONAL` | `view=FAMILY` detalle (listados) | `view=FAMILY` totales (analytics) |
|---|---|---|---|
| `ADULTO` | propios | todo | todo |
| `HIJO` `OWN_ONLY` | propios | 403 | 403 |
| `HIJO` `FAMILY_SUMMARY` | propios | 403 | totales permitidos, sin desglose por persona ajena |
| `HIJO` `FAMILY_FULL` | propios | todo | todo |

- **"Propios"** en gastos/analítica = movimientos con `attributed_to_user_id = yo` (Regla 5). Un gasto que un adulto pagó y atribuyó al hijo **no** suma en el personal del adulto y **sí** en el del hijo.
- Saldos de cuenta se calculan por `account_id` (siempre reflejan la salida real de dinero).
- Totales de ingresos/gastos **excluyen** `TRANSFER_*`, `EXCHANGE_*` y, en la vista FAMILY, `ALLOWANCE_*` (Regla 1, cero doble conteo). En la vista PERSONAL del hijo, `ALLOWANCE_IN` cuenta como ingreso.
- Solo `status = 'POSTED'` y `deleted_at IS NULL` suman en totales; `PROJECTED` se usa en proyecciones/compromisos.

---

## C5. Lentes anti-inflación (cálculo)

Para cada transacción se usa su `economic_snapshot`:

| Lente | Valor | Unidad |
|---|---|---|
| `NOMINAL` | `base_currency_amount` | `families.base_currency` |
| `CONSTANT_BASE` | `base_currency_amount × IPC_ACUMULADO(último) / ipc_index_value` (si falta IPC → nominal y `lensApproximated=true`) | base currency "de hoy" |
| `REFERENCE_CURRENCY` | `amount_in_reference` | `families.reference_currency` |
| `INCOME_PCT` | `base_currency_amount / ingresos del período (base) × 100` | `%` |

`IPC_ACUMULADO` es un índice encadenado (base 100 en el primer mes disponible) calculado por el módulo `economics` a partir de `IPC_MENSUAL`.

---

## C6. APIs Java entre módulos (Core)

```java
// T06 — com.familion.common.security
public record CurrentUser(UUID userId, UUID familyId, Role role, FamilyVisibility visibility) {}
public interface CurrentUserProvider { CurrentUser get(); }               // lanza 401 si no hay auth
public enum Role { ADULTO, HIJO }
public enum FamilyVisibility { OWN_ONLY, FAMILY_SUMMARY, FAMILY_FULL }
public enum View { PERSONAL, FAMILY }
public enum ViewAccess { OWN, FAMILY_TOTALS_ONLY, FAMILY_FULL }
public interface VisibilityPolicy {
  ViewAccess resolveForListing(CurrentUser u, View requested);   // lanza ForbiddenException según C4
  ViewAccess resolveForTotals(CurrentUser u, View requested);
}
// T06 — com.familion.common.events
public record FamilyFinancialDataChangedEvent(UUID familyId) {}  // publicar dentro de la transacción; invalida caché AFTER_COMMIT

// T07 — com.familion.family.application.api
public record FamilySettings(UUID familyId, String name, String baseCurrency, String referenceCurrency,
                             String referenceRateSource, ZoneId timezone, List<String> enabledCurrencies) {}
public record MemberRef(UUID userId, UUID familyId, String fullName, Role role, String accentColor) {}
public interface FamilyQueryApi {
  FamilySettings getSettings(UUID familyId);
  MemberRef requireMember(UUID familyId, UUID userId);     // NotFound si no pertenece
  List<MemberRef> listMembers(UUID familyId);
}

// T09 — com.familion.economics.application.api
public record EconomicSnapshotData(String referenceCurrency, String referenceRateSource, BigDecimal referenceExchangeRate,
                                   BigDecimal amountInReference, BigDecimal baseCurrencyAmount,
                                   BigDecimal ipcIndexValue, BigDecimal uvaValue, LocalDate snapshotDate) {}
public record FamilyEconomicContext(String baseCurrency, String referenceCurrency, String referenceRateSource) {}
public interface EconomicsApi {
  EconomicSnapshotData snapshotFor(FamilyEconomicContext ctx, LocalDate date, BigDecimal amount, String currencyCode);
  BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date, String rateSource);
  Optional<BigDecimal> valueAt(String indexCode, LocalDate date);   // último valor con effective_date <= date
  Optional<BigDecimal> latestValue(String indexCode);
}

// T13 — com.familion.finance.accounts.application.api / categories.application.api
public record AccountRef(UUID id, UUID familyId, UUID ownerUserId, String name, String accountType, String currencyCode,
                         Integer closingDay, Integer dueDay) {}
public interface AccountQueryApi {
  AccountRef requireUsable(CurrentUser user, UUID accountId);   // misma familia, activa; HIJO solo cuentas propias
  BigDecimal currentBalance(UUID accountId);
}
public record CategoryRef(UUID id, String name, String kind, String icon, String colorHex) {}
public interface CategoryQueryApi {
  CategoryRef requireUsable(UUID familyId, UUID categoryId, String kind);  // sistema o de la familia
  CategoryRef requireSystemCategory(String systemKey);                    // ej. "VEHICLES_FUEL"
}

// T16 — com.familion.finance.transactions.application.api
public record NewTransactionCommand(UUID familyId, UUID userId, UUID attributedToUserId, UUID accountId, UUID categoryId,
    TransactionType type, Scope scope, TransactionStatus status, BigDecimal amount, String currencyCode,
    LocalDate transactionDate, String description, String notes, UUID installmentPlanId, Integer installmentNumber,
    UUID recurringExpenseId, UUID currencyExchangeId, UUID allowanceId, UUID receiptAttachmentId, UUID transferGroupId) {}
public interface TransactionCommandApi {
  UUID record(NewTransactionCommand cmd);                       // valida, guarda, crea snapshot, publica evento
  void updateAmountAndDate(UUID familyId, UUID transactionId, BigDecimal amount, LocalDate date);
  void changeStatus(UUID familyId, UUID transactionId, TransactionStatus status);
  void softDelete(UUID familyId, UUID transactionId);
}
```

---

## C7. Catálogo de DTOs JSON compartidos (Core → BFF → Frontend)

`Decimal` = string decimal. `Date` = `yyyy-MM-dd`.

```ts
type Me = { id: string; email: string; fullName: string; role: 'ADULTO'|'HIJO';
  familyVisibility: 'OWN_ONLY'|'FAMILY_SUMMARY'|'FAMILY_FULL';
  themePreference: 'LIGHT'|'DARK'|'SYSTEM'; accentColor: string; twoFactorEnabled: boolean; emailVerified: boolean;
  family: { id: string; name: string; baseCurrency: string; referenceCurrency: string;
            referenceRateSource: string; timezone: string; enabledCurrencies: string[] } };

type Account = { id: string; name: string; accountType: 'CASH'|'BANK'|'WALLET'|'DEBIT_CARD'|'CREDIT_CARD'|'SAVINGS';
  currencyCode: string; initialBalance: Decimal; currentBalance: Decimal; ownerUserId: string|null;
  creditLimit: Decimal|null; closingDay: number|null; dueDay: number|null; isActive: boolean };

type Category = { id: string; parentCategoryId: string|null; name: string; kind: 'EXPENSE'|'INCOME';
  icon: string|null; colorHex: string|null; isSystem: boolean; systemKey: string|null };

type TransactionItem = { id: string; type: 'EXPENSE'|'INCOME'|'TRANSFER_OUT'|'TRANSFER_IN'|'EXCHANGE_OUT'|'EXCHANGE_IN'|'ALLOWANCE_OUT'|'ALLOWANCE_IN';
  scope: 'PERSONAL'|'FAMILY'; status: 'POSTED'|'PROJECTED'|'CANCELLED';
  amount: Decimal; currencyCode: string; transactionDate: Date; description: string|null; notes: string|null;
  category: { id: string; name: string; icon: string|null; colorHex: string|null } | null;
  account: { id: string; name: string; accountType: string };
  paidBy: { id: string; fullName: string }; attributedTo: { id: string; fullName: string };
  installment: { planId: string; number: number; total: number } | null;
  lens: string; lensAmount: Decimal|null; lensUnit: string; lensApproximated: boolean };

type IndexValue = { indexCode: string; effectiveDate: Date; value: Decimal; source: 'API'|'MANUAL' };

type Period = { granularity: 'DAY'|'WEEK'|'MONTH'|'YEAR'; from: Date; to: Date; label: string };

type AnalyticsSummary = { period: Period; view: 'PERSONAL'|'FAMILY'; lens: string; unit: string;
  income: Decimal; expense: Decimal; balance: Decimal; savingsRatePct: Decimal|null;
  installmentCommitmentPct: Decimal|null; previous: { income: Decimal; expense: Decimal };
  realVariationPct: Decimal|null; lensApproximated: boolean };

type CategoryBreakdown = { period: Period; lens: string; unit: string; total: Decimal;
  slices: { categoryId: string|null; name: string; colorHex: string|null; icon: string|null;
            amount: Decimal; pct: Decimal; variationPct: Decimal|null }[] };

type PersonBreakdown = { period: Period; lens: string; unit: string; total: Decimal;
  slices: { userId: string; fullName: string; accentColor: string; amount: Decimal; pct: Decimal }[] };

type Series = { lens: string; unit: string; granularity: string;
  points: { periodStart: Date; label: string; income: Decimal; expense: Decimal; balance: Decimal }[] };

type CalendarView = { from: Date; to: Date; days: { date: Date; income: Decimal; expense: Decimal;
  items: { kind: 'TRANSACTION'|'INSTALLMENT'|'RECURRING_DUE'|'VEHICLE_DUE'|'ALLOWANCE'; refId: string;
           title: string; amount: Decimal|null; currencyCode: string|null }[] }[] };

type HomePayload = { summary: AnalyticsSummary|null; byCategory: CategoryBreakdown|null; byPerson: PersonBreakdown|null;
  recentTransactions: TransactionItem[]|null; latestRates: IndexValue[]|null; unreadNotifications: number|null };
```

Los DTOs de cuotas, fijos, metas, mensualidades, divisas, vehículos y notificaciones están definidos en sus tarjetas (T20, T25–T28, T33, T36) y copiados en las tarjetas de frontend/BFF que los consumen.

---

## C8. Frontend: estructura y rutas (creadas por T02)

```
src/app/
  app.config.ts, app.routes.ts, app.ts
  core/
    api/        api-config.ts (BFF_BASE = '/bff'), problem-detail.ts
    auth/       auth.guard.ts (placeholder → T12), auth.store.ts (T12), auth.interceptor.ts (T12)
    layout/     shell.component.ts (placeholder → T08), auth-layout.component.ts
    theme/      theme.service.ts (T08)
    state/      view-context.store.ts (T08)
    finance/    (T15/T17) servicios compartidos: accounts, categories, transactions
  shared/
    ui/         (T08) kit de componentes
    pipes/      (T08) money.pipe.ts, decimal helpers
  features/<feature>/<feature>.routes.ts  + pages/ + components/ + data-access/
```

| Ruta | Feature / archivo de rutas | Tarea dueña |
|---|---|---|
| `/auth/login`, `/auth/register`, `/auth/verify-email`, `/auth/2fa`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/invite` | `features/auth/auth.routes.ts` | T12 |
| `/home` | `features/home/home.routes.ts` | T18 |
| `/transactions` | `features/transactions/transactions.routes.ts` | T23 |
| `/analytics` | `features/analytics/analytics.routes.ts` | T22 |
| `/accounts` | `features/accounts/accounts.routes.ts` | T15 |
| `/plan` (`''`, `installments`, `recurring`, `goals`, `exchanges`, `allowances`) | `features/plan/plan.routes.ts` (cada hijo en su subcarpeta) | T32, T30, T30, T31, T24, T31 |
| `/vehicles` (`''`, `:id`, `:id/fuel/new`, `:id/maintenance/new`, `new`) | `features/vehicles/vehicles.routes.ts` | T34 |
| `/profile` (`''`, `family`, `security`) | `features/profile/profile.routes.ts` | T14 |
| `/notifications` | `features/notifications/notifications.routes.ts` | T37 |

Tokens CSS (T02, `src/styles.css`): `--fo-bg`, `--fo-surface`, `--fo-surface-2`, `--fo-border`, `--fo-text`, `--fo-text-muted`, `--fo-accent`, `--fo-accent-contrast`, `--fo-accent-soft`, `--fo-success`, `--fo-danger`, `--fo-warning`. Tema oscuro con clase `dark` en `<html>`.

---

## C9. Claves Redis

| Clave | TTL | Dueño |
|---|---|---|
| `econ:latest:{indexCode}` | 30 min | T09 |
| `econ:history:{indexCode}:{yyyy-MM}` | 24 h | T09 |
| `family:{familyId}:analytics:{hash}` | 5 min (invalidación AFTER_COMMIT) | T06 (invalidación) / T19 (uso) |
| `family:{familyId}:categories` | 1 h | T13 |
| `auth:revoked:{jti}` | vida restante del token | T06 / T07 |
| `auth:login-fail:{email}` | backoff exponencial | T07 |
| `auth:otp-cooldown:{userId}` | 60 s | T11 |
| `rl:bff:user:{userId}` / `rl:bff:ip:{ip}` | 1 min (Bucket4j) | T04 |
