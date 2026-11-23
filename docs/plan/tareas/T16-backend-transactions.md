# T16 — feat(finance): movimientos (gastos, ingresos, transferencias), atribución, vistas y snapshot económico

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.2 | `feat/backend-transactions` | Ola 2.1 (T13) | T17, T18 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **248–293** (`transactions`, `economic_snapshots`).
- APIs existentes: `CurrentUserProvider`, `VisibilityPolicy`, `FamilyFinancialDataChangedEvent` (T06); `FamilyQueryApi` (T07); `EconomicsApi` + `FamilyEconomicContext` (T09); `AccountQueryApi`, `CategoryQueryApi` (T13).

## Objetivo
El corazón contable: registrar, listar, editar y borrar movimientos respetando atribución (Regla 5), vistas Personal/Familia, transferencias en pares, y congelar el contexto económico de cada movimiento. Exponer `TransactionCommandApi` para cuotas, fijos, divisas, mensualidades y vehículos.

## Archivos permitidos
```
backend/src/main/java/com/familion/finance/transactions/**
backend/src/test/java/com/familion/finance/transactions/**
```

## Contrato

**API Java (firmas exactas):**
```java
package com.familion.finance.transactions.application.api;
public enum TransactionType { EXPENSE, INCOME, TRANSFER_OUT, TRANSFER_IN, EXCHANGE_OUT, EXCHANGE_IN, ALLOWANCE_OUT, ALLOWANCE_IN }
public enum Scope { PERSONAL, FAMILY }
public enum TransactionStatus { POSTED, PROJECTED, CANCELLED }
public record NewTransactionCommand(UUID familyId, UUID userId, UUID attributedToUserId, UUID accountId, UUID categoryId,
    TransactionType type, Scope scope, TransactionStatus status, BigDecimal amount, String currencyCode,
    LocalDate transactionDate, String description, String notes, UUID installmentPlanId, Integer installmentNumber,
    UUID recurringExpenseId, UUID currencyExchangeId, UUID allowanceId, UUID receiptAttachmentId, UUID transferGroupId) {}
public interface TransactionCommandApi {
  UUID record(NewTransactionCommand cmd);   // valida membresía de attributedTo, guarda, crea snapshot, publica eventos
  void updateAmountAndDate(UUID familyId, UUID transactionId, BigDecimal amount, LocalDate date);   // recalcula snapshot
  void changeStatus(UUID familyId, UUID transactionId, TransactionStatus status);
  void softDelete(UUID familyId, UUID transactionId);
}
// Eventos de dominio (publicados con ApplicationEventPublisher dentro de la transacción):
public record ExpenseAttributedEvent(UUID familyId, UUID transactionId, UUID paidByUserId, UUID attributedToUserId,
                                     BigDecimal amount, String currencyCode) {}            // lo consume T36
public record TransactionChangedEvent(UUID familyId, UUID transactionId, BigDecimal amount, LocalDate date,
                                      boolean deleted) {}                                  // lo consume T33 (vehículos)
```
`record`, `updateAmountAndDate`, `changeStatus` y `softDelete` publican además `FamilyFinancialDataChangedEvent(familyId)`.
`record` **no** valida permisos del usuario (lo hacen los casos de uso que lo llaman); sí valida que `userId` y `attributedToUserId` sean miembros de `familyId` y que `amount > 0`.

**Endpoints** (`/api/v1/transactions`):

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/transactions` | auth | query: `view, lens, from, to, type, categoryId, accountId, attributedToUserId, currency, status, q, page, size, sort` | `Page<TransactionItem>` |
| GET | `/transactions/{id}` | auth (visible) | — | `TransactionItem` |
| POST | `/transactions` | auth | `TransactionCreate` | `201 TransactionItem` |
| PATCH | `/transactions/{id}` | autor / ADULTO | `TransactionPatch` | `TransactionItem` |
| DELETE | `/transactions/{id}` | autor / ADULTO | — | `204` |

```ts
type TransactionCreate = { type: 'EXPENSE'|'INCOME'|'TRANSFER'; amount: Decimal; currencyCode: string;
  transactionDate?: string; accountId: string; targetAccountId?: string; categoryId?: string; description?: string;
  notes?: string; scope?: 'PERSONAL'|'FAMILY'; attributedToUserId?: string; receiptAttachmentId?: string };
type TransactionPatch = { amount?; transactionDate?; categoryId?; description?; notes?; accountId?; attributedToUserId?; scope? };
type TransactionItem = { id; type; scope; status; amount: Decimal; currencyCode; transactionDate; description; notes;
  category: { id; name; icon; colorHex } | null; account: { id; name; accountType };
  paidBy: { id; fullName }; attributedTo: { id; fullName };
  installment: { planId; number; total } | null;
  lens: 'NOMINAL'|'CONSTANT_BASE'|'REFERENCE_CURRENCY'|'INCOME_PCT'; lensAmount: Decimal|null; lensUnit: string; lensApproximated: boolean };
```

## Reglas de negocio

1. **Crear:**
   - `transactionDate` default = hoy en la zona horaria de la familia (`FamilySettings.timezone`).
   - `accountId` → `AccountQueryApi.requireUsable(user, id)`. La divisa debe coincidir con la de la cuenta, **salvo** `CREDIT_CARD` (acepta cualquier divisa habilitada) → si no `422`.
   - `EXPENSE`/`INCOME`: `categoryId` opcional pero si viene → `CategoryQueryApi.requireUsable(familyId, id, kind)`; si no hay categoría, `description` es obligatoria (`400`).
   - `TRANSFER`: requiere `targetAccountId` distinto, misma divisa en ambas cuentas (si difieren → `422` «Use currency exchange»); crea `TRANSFER_OUT` (cuenta origen) + `TRANSFER_IN` (destino) con el mismo `transfer_group_id`, sin categoría; responde con el ítem OUT.
   - **Atribución (Regla 5):** `attributedToUserId` default = usuario actual. Solo `ADULTO` puede poner otro (debe ser miembro, `FamilyQueryApi.requireMember`); `HIJO` con otro → `403`. Si `attributedTo != user` y tipo `EXPENSE` → publicar `ExpenseAttributedEvent`.
   - `scope`: default `PERSONAL`; `FAMILY` solo ADULTO (HIJO → forzar `PERSONAL`).
   - **Snapshot:** `EconomicsApi.snapshotFor(new FamilyEconomicContext(base, reference, rateSource), date, amount, currency)` → guardar en `economic_snapshots` (1:1).
2. **Listar (vistas, ver tabla):** `view=PERSONAL` → `attributed_to_user_id = yo`. `view=FAMILY` → `VisibilityPolicy.resolveForListing` (HIJO `OWN_ONLY`/`FAMILY_SUMMARY` → `403`) → toda la familia. Siempre `deleted_at IS NULL`; por defecto `status IN (POSTED, PROJECTED)`. `q` busca `ILIKE` en `description`. Orden default `transactionDate desc, createdAt desc`. `size` ≤ 50. Implementar con `JdbcTemplate`/`NamedParameterJdbcTemplate` (una query con JOINs a cuentas, categorías, usuarios, snapshot e `installment_plans` para `total`) — sin N+1.
3. **Lente por ítem** (`lens` default `NOMINAL`):
   - `NOMINAL` → `base_currency_amount`, unidad = base.
   - `CONSTANT_BASE` → `base_currency_amount × IPC_ACUMULADO(último) / ipc_index_value`; si falta alguno → nominal y `lensApproximated = true`.
   - `REFERENCE_CURRENCY` → `amount_in_reference`, unidad = referencia.
   - `INCOME_PCT` → `base_currency_amount / ingresos del mes calendario de esa transacción (misma vista, POSTED, tipo INCOME, en base) × 100`; sin ingresos → `null`. Calcular los ingresos por mes en una query auxiliar agrupada.
4. **Editar:** autor (`user_id = yo`) o ADULTO; HIJO no puede cambiar `attributedToUserId`. En `TRANSFER_*` solo `description/notes/amount/transactionDate` y se aplican a **ambas** patas. `EXCHANGE_*`, `ALLOWANCE_*` → solo `description/notes` (resto `409`). Movimiento de un plan de cuotas → solo `description/notes/categoryId` (`409` para monto/fecha). Si cambia monto, fecha o cuenta → recalcular snapshot y publicar `TransactionChangedEvent`.
5. **Borrar:** baja lógica; transferencias borran ambas patas; cuotas de un plan → `409` («Use cancel-remaining»); publica `TransactionChangedEvent(deleted=true)`.
6. **Aislamiento:** toda query filtra por `familyId` del token; un id de otra familia → `404`.

## Tests (prioridad alta: es el módulo más crítico)
- Unit: cálculo de lentes (incluye `lensApproximated`), reglas de atribución, validación de divisas por tipo de cuenta.
- IT: crear gasto → snapshot guardado con valores de `EconomicsApi` (insertar `economic_indices` de prueba); transferencia crea par con mismo grupo y no cambia totales de gasto; ADULTO atribuye a HIJO → aparece en `view=PERSONAL` del hijo y **no** en la del adulto, y se publica `ExpenseAttributedEvent`; HIJO intenta atribuir → 403; HIJO `OWN_ONLY` lista FAMILY → 403; filtros y paginación (size 51 → 50); editar monto recalcula snapshot y publica `TransactionChangedEvent`; borrar transferencia borra ambas; otra familia → 404; `FamilyFinancialDataChangedEvent` publicado (verificar con `@RecordApplicationEvents`).

## Fuera de alcance
Cuotas (T25), divisas (T20), analítica agregada (T19), pantallas.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Contratos Java y JSON exactos.
- [ ] Ninguna query sin filtro de `family_id`.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(transactions): add transaction model with economic snapshot`
2. `feat(transactions): add create flow with attribution and transfers`
3. `feat(transactions): add filtered listing with views and lenses`
4. `feat(transactions): add edit and soft delete with domain events`
