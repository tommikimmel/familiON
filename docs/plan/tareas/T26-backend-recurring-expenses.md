# T26 — feat(finance): gastos fijos mensuales con reglas de ajuste y confirmación de pago

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.1 | `feat/backend-recurring-expenses` | Fase 2 completa | T25, T27 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **195–213** (`recurring_expenses`) y **248–280** (`transactions.recurring_expense_id`).
- APIs: `TransactionCommandApi` (T16), `AccountQueryApi`, `CategoryQueryApi` (T13), `FamilyQueryApi` (T07), `EconomicsApi` (T09), `CurrentUserProvider` (T06).

## Objetivo
Definir gastos fijos (alquiler, expensas, prepaga, colegio, seguro) con día de vencimiento y regla de actualización; cada mes el sistema muestra los pendientes con monto estimado y el usuario solo confirma el pago (ajustando el monto real).

## Archivos permitidos
```
backend/src/main/java/com/familion/finance/recurring/**
backend/src/test/java/com/familion/finance/recurring/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/recurring-expenses?month=yyyy-MM&includeInactive=false` | auth | — | `RecurringExpense[]` |
| POST | `/api/v1/recurring-expenses` | ADULTO | `RecurringExpenseCreate` | `201 RecurringExpense` |
| PATCH | `/api/v1/recurring-expenses/{id}` | ADULTO | parcial | `RecurringExpense` |
| DELETE | `/api/v1/recurring-expenses/{id}` | ADULTO | — | `204` (`is_active=false`) |
| POST | `/api/v1/recurring-expenses/{id}/confirm-payment` | auth | `{ month: 'yyyy-MM', amount: Decimal, accountId?, paymentDate? }` | `201 RecurringExpense` (mes actualizado) |

```ts
type RecurringExpenseCreate = { name; categoryId; accountId?; scope?: 'PERSONAL'|'FAMILY'; baseAmount: Decimal; currencyCode;
  dueDay: number; adjustmentRule: 'MANUAL'|'IPC'|'REFERENCE_CURRENCY'|'LAST_PAID'; startDate: string; endDate?: string };
type RecurringExpense = { id; name; category: { id; name; icon; colorHex }; account: { id; name } | null; scope;
  baseAmount: Decimal; currencyCode; dueDay; adjustmentRule; startDate; endDate: string|null; isActive: boolean;
  month: { month: 'yyyy-MM'; dueDate: string; estimatedAmount: Decimal; status: 'PENDING'|'PAID'|'OVERDUE'|'NOT_APPLICABLE';
           paidTransactionId: string|null; paidAmount: Decimal|null; paidDate: string|null } };
```

**API Java para notificaciones (T36):**
```java
package com.familion.finance.recurring.application.api;
public record RecurringDue(UUID recurringExpenseId, UUID familyId, String name, LocalDate dueDate, BigDecimal estimatedAmount, String currencyCode) {}
public interface RecurringExpenseQueryApi { List<RecurringDue> pendingDueBetween(LocalDate from, LocalDate to); }   // todas las familias
```

## Reglas
1. `dueDate` del mes = `dueDay` con clamp al último día. `NOT_APPLICABLE` si el mes está fuera de `[startDate, endDate]`. `PAID` si existe transacción no borrada con `recurring_expense_id` y fecha dentro del mes; `OVERDUE` si no pagado y hoy > `dueDate`; si no `PENDING`.
2. **Monto estimado** según regla:
   - `MANUAL` → `baseAmount`.
   - `IPC` → `baseAmount × IPC_ACUMULADO(mes objetivo, o último) / IPC_ACUMULADO(startDate)`; sin datos → `baseAmount`.
   - `REFERENCE_CURRENCY` → `baseAmount` está expresado en la moneda de referencia de la familia: convertir a `currencyCode` con `EconomicsApi.convert(..., hoy)`.
   - `LAST_PAID` → último monto pagado × (1 + último `IPC_MENSUAL`/100); sin pagos previos → `baseAmount`.
   Escala 4; el front redondea para mostrar.
3. **confirm-payment:** mes ya pagado → `409`; mes `NOT_APPLICABLE` → `422`; crea `EXPENSE POSTED` con `TransactionCommandApi.record` (`recurringExpenseId`, categoría, cuenta = la enviada o la sugerida —si ninguna → `422`—, `scope` del gasto fijo, `transactionDate` = `paymentDate` o hoy, `description` = nombre, atribuido al usuario).
4. Solo ADULTO crea/edita; cualquier miembro puede confirmar pagos de gastos `FAMILY` y el dueño los `PERSONAL`. Listado: `FAMILY` + mis `PERSONAL` (HIJO ve solo sus `PERSONAL`).
5. `pendingDueBetween`: para cada gasto activo de todas las familias, `dueDate` en el rango y no pagado.

## Tests
- Unit de estimación por cada regla (con índices de prueba) y clamp de `dueDay=31` en febrero.
- IT: crear (HIJO → 403), listar mes con estados PENDING/OVERDUE/PAID (usar `Clock` fijo), confirmar crea transacción vinculada, doble confirmación → 409, `pendingDueBetween`.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(recurring): add recurring expense model and monthly status resolution`
2. `feat(recurring): add adjustment rules for estimated amounts`
3. `feat(recurring): add payment confirmation and due query api`
