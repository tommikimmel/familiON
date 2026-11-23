# T13 — feat(finance): cuentas con saldo y categorías jerárquicas (+ seed del sistema)

| Fase | Ola | Rama | Requiere mergeado | Paralela con | Migración reservada |
|---|---|---|---|---|---|
| 2 | 2.1 | `feat/backend-accounts-categories` | Fase 1 completa | T14, T15 | `V13` |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **121–150** (`accounts`, `categories`) y **248–280** (`transactions`, solo para calcular saldos).
- APIs existentes: `CurrentUserProvider`, `VisibilityPolicy` (T06), `FamilyQueryApi` (T07).

## Objetivo
Cuentas/medios de pago (efectivo, banco, billetera, débito, crédito, ahorro) con saldo calculado, y categorías del sistema + personalizadas por familia, exponiendo APIs para que otros módulos validen cuentas y categorías.

## Archivos permitidos
```
backend/src/main/java/com/familion/finance/accounts/**
backend/src/main/java/com/familion/finance/categories/**
backend/src/main/resources/db/migration/V13__categories_system_keys_and_seed.sql
backend/src/test/java/com/familion/finance/accounts/**
backend/src/test/java/com/familion/finance/categories/**
```

## Contrato

**APIs Java (firmas exactas):**
```java
package com.familion.finance.accounts.application.api;
public record AccountRef(UUID id, UUID familyId, UUID ownerUserId, String name, String accountType, String currencyCode,
                         Integer closingDay, Integer dueDay) {}
public interface AccountQueryApi {
  AccountRef requireUsable(CurrentUser user, UUID accountId);  // misma familia, activa, no borrada; HIJO solo cuentas propias → si no NotFound/Forbidden
  BigDecimal currentBalance(UUID accountId);
}
package com.familion.finance.categories.application.api;
public record CategoryRef(UUID id, String name, String kind, String icon, String colorHex) {}
public interface CategoryQueryApi {
  CategoryRef requireUsable(UUID familyId, UUID categoryId, String kind);   // sistema o de la familia, activa, kind coincide → si no 422
  CategoryRef requireSystemCategory(String systemKey);                     // ej. "VEHICLES_FUEL"
}
```

**Endpoints:**

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/accounts?view=PERSONAL\|FAMILY&includeInactive=false` | auth | — | `Account[]` |
| POST | `/api/v1/accounts` | auth | `AccountCreate` | `201 Account` |
| PATCH | `/api/v1/accounts/{id}` | dueño / ADULTO | parcial de `AccountCreate` (sin `currencyCode`) | `Account` |
| DELETE | `/api/v1/accounts/{id}` | dueño / ADULTO | — | `204` (baja lógica) |
| GET | `/api/v1/categories?kind=EXPENSE\|INCOME` | auth | — | `Category[]` |
| POST | `/api/v1/categories` | ADULTO | `{ name, kind, parentCategoryId?, icon?, colorHex? }` | `201 Category` |
| PATCH | `/api/v1/categories/{id}` | ADULTO | `{ name?, icon?, colorHex?, isActive? }` | `Category` |

```ts
type Account = { id; name; accountType: 'CASH'|'BANK'|'WALLET'|'DEBIT_CARD'|'CREDIT_CARD'|'SAVINGS'; currencyCode;
  initialBalance: Decimal; currentBalance: Decimal; ownerUserId: string|null; creditLimit: Decimal|null;
  closingDay: number|null; dueDay: number|null; isActive: boolean };
type AccountCreate = { name; accountType; currencyCode; initialBalance?: Decimal; ownerUserId?: string|null;
  creditLimit?: Decimal; closingDay?: number; dueDay?: number };
type Category = { id; parentCategoryId: string|null; name; kind: 'EXPENSE'|'INCOME'; icon: string|null;
  colorHex: string|null; isSystem: boolean; systemKey: string|null };
```

## Especificación

1. **Migración `V13`:** `ALTER TABLE categories ADD COLUMN system_key VARCHAR(40);` + `CREATE UNIQUE INDEX uq_categories_system_key ON categories(system_key) WHERE system_key IS NOT NULL;` + seed (`family_id NULL`, `is_system TRUE`, ícono = nombre lucide):
   - **EXPENSE:** `SUPERMARKET` Supermercado (shopping-cart), `HOUSING` Alquiler y expensas (house), `UTILITIES` Servicios (plug-zap), `HEALTH` Prepaga y salud (heart-pulse), `EDUCATION` Educación (graduation-cap), `VEHICLES` Vehículos (car) con hijos `VEHICLES_FUEL` Combustible (fuel), `VEHICLES_MAINTENANCE` Mantenimiento (wrench), `VEHICLES_INSURANCE` Seguro (shield), `VEHICLES_TAXES` Patente (file-text), `VEHICLES_TOLLS` Peajes y estacionamiento (ticket), `VEHICLES_FINES` Multas (siren); `OUTINGS` Salidas (party-popper), `TRANSPORT` Transporte (bus), `CLOTHING` Indumentaria (shirt), `HOME` Hogar (sofa), `PETS` Mascotas (paw-print), `KIDS` Hijos (baby), `TECH` Tecnología (laptop), `SUBSCRIPTIONS` Suscripciones (repeat), `GIFTS` Regalos (gift), `TAXES` Impuestos (landmark), `FINANCIAL_COSTS` Costos financieros (percent), `OTHER_EXPENSE` Otros gastos (circle-ellipsis).
   - **INCOME:** `SALARY` Sueldo (briefcase), `BONUS` Aguinaldo (sparkles), `FREELANCE` Trabajos freelance (laptop), `RENT_INCOME` Alquileres (key-round), `INVESTMENTS` Rendimientos (trending-up), `ALLOWANCE_INCOME` Mensualidad (piggy-bank), `OTHER_INCOME` Otros ingresos (circle-ellipsis).
   - Los hijos de `VEHICLES` referencian al padre por subquery sobre `system_key`.
2. **Saldo** (`currentBalance`): `initial_balance + Σ(INCOME, TRANSFER_IN, EXCHANGE_IN, ALLOWANCE_IN) − Σ(EXPENSE, TRANSFER_OUT, EXCHANGE_OUT, ALLOWANCE_OUT)` sobre `transactions` con `status = 'POSTED' AND deleted_at IS NULL` y `account_id = ?`. Para `CREDIT_CARD` el resultado negativo representa deuda (no invertir signo). Listado: una sola query agregada (sin N+1).
3. **Visibilidad del listado:** `PERSONAL` → cuentas con `owner_user_id = yo` + compartidas (`owner_user_id IS NULL`) si soy ADULTO; HIJO solo propias. `FAMILY` → `VisibilityPolicy.resolveForListing`: todas las de la familia.
4. **Crear cuenta:** `currencyCode` debe estar habilitada en la familia (`FamilyQueryApi.getSettings(...).enabledCurrencies()`), si no `422`. ADULTO puede poner `ownerUserId` = null (compartida) u otro miembro (validar con `FamilyQueryApi.requireMember`); HIJO siempre él mismo (ignorar lo enviado). `CREDIT_CARD` requiere `closingDay` y `dueDay` (1–31) → si no `400`; en otros tipos se ignoran (null). Nombre único por familia entre cuentas activas → `409`.
5. **Baja lógica:** `deleted_at = now`, `is_active = false`; sigue apareciendo en transacciones históricas.
6. **Categorías:** GET devuelve sistema + familia (activas), ordenadas por `kind`, padre, nombre; caché Redis `family:{familyId}:categories` TTL 1 h (JSON), invalidada al crear/editar; fail-open. POST: `parentCategoryId` debe ser del mismo `kind` y de primer nivel (profundidad máx 2) → si no `422`. Las de sistema no se editan → `403`.

## Tests
- Seed: existen las 31 categorías de sistema y `VEHICLES_FUEL` tiene padre `VEHICLES`.
- Saldo con mezcla de tipos y estados (POSTED/PROJECTED/borradas) — insertar transacciones con SQL.
- Visibilidad: HIJO no ve cuentas compartidas en PERSONAL; HIJO `OWN_ONLY` en FAMILY → 403.
- Crear tarjeta sin `closingDay` → 400; divisa no habilitada → 422; HIJO crea cuenta con `ownerUserId` ajeno → queda propia.
- `requireUsable`: otra familia → 404; HIJO sobre cuenta compartida → 403.
- Categoría hija de una hija → 422; editar de sistema → 403; caché invalidada tras crear.

## Fuera de alcance
Transacciones (T16), pantallas (T15).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Contratos exactos; sin N+1 en el listado de cuentas.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(finance): seed system categories with stable keys`
2. `feat(finance): add categories endpoints with redis cache`
3. `feat(finance): add accounts with computed balances and visibility rules`
