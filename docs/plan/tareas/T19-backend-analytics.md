# T19 — feat(analytics): resumen, series, torta por categoría/persona y calendario con lentes anti-inflación

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.3 | `feat/backend-analytics` | Ola 2.2 (T16) | T20, T21 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **152–213** (allowances, installment_plans, recurring_expenses), **248–293** (transactions, economic_snapshots), **332–399** (vehicles, maintenance_logs — solo columnas de vencimiento).
- APIs: `CurrentUserProvider`, `VisibilityPolicy`, `CacheKeys` (T06), `FamilyQueryApi` (T07), `EconomicsApi` (T09).

## Objetivo
Módulo de lectura (read model) que agrega movimientos por día/semana/mes/año en las 4 lentes, respeta visibilidad, cachea en Redis y alimenta Inicio, Analítica y el calendario.

## Archivos permitidos
```
backend/src/main/java/com/familion/analytics/**     (excepto subpaquete exports, que es de T40)
backend/src/test/java/com/familion/analytics/**
```
Lectura directa de tablas con `NamedParameterJdbcTemplate` (es el lado de lectura; no importar `domain` de otros módulos).

## Contrato

Params comunes: `view=PERSONAL|FAMILY` (default PERSONAL), `lens=NOMINAL|CONSTANT_BASE|REFERENCE_CURRENCY|INCOME_PCT` (default NOMINAL), `granularity=DAY|WEEK|MONTH|YEAR` (default MONTH), `date=yyyy-MM-dd` (default hoy en la zona de la familia).

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/v1/analytics/summary` | `AnalyticsSummary` |
| GET | `/api/v1/analytics/series?periods=12` (1–24) | `Series` |
| GET | `/api/v1/analytics/by-category` | `CategoryBreakdown` |
| GET | `/api/v1/analytics/by-person` | `PersonBreakdown` (solo `view=FAMILY`) |
| GET | `/api/v1/analytics/calendar?from=&to=` (máx 62 días) | `CalendarView` |

```ts
type Period = { granularity; from: string; to: string; label: string };
type AnalyticsSummary = { period: Period; view; lens; unit: string; income: Decimal; expense: Decimal; balance: Decimal;
  savingsRatePct: Decimal|null; installmentCommitmentPct: Decimal|null; previous: { income: Decimal; expense: Decimal };
  realVariationPct: Decimal|null; lensApproximated: boolean };
type Series = { lens; unit; granularity; points: { periodStart: string; label: string; income: Decimal; expense: Decimal; balance: Decimal }[] };
type CategoryBreakdown = { period: Period; lens; unit; total: Decimal;
  slices: { categoryId: string|null; name; colorHex: string|null; icon: string|null; amount: Decimal; pct: Decimal; variationPct: Decimal|null }[] };
type PersonBreakdown = { period: Period; lens; unit; total: Decimal;
  slices: { userId; fullName; accentColor; amount: Decimal; pct: Decimal }[] };
type CalendarView = { from; to; days: { date; income: Decimal; expense: Decimal;
  items: { kind: 'TRANSACTION'|'INSTALLMENT'|'RECURRING_DUE'|'VEHICLE_DUE'|'ALLOWANCE'; refId: string; title: string;
           amount: Decimal|null; currencyCode: string|null }[] }[] };
```

## Reglas

1. **Períodos** en la zona horaria de la familia: `DAY` = el día; `WEEK` = lunes–domingo ISO; `MONTH`; `YEAR`. Labels en español: `23 nov 2026`, `Semana 47 · 2026`, `Noviembre 2026`, `2026`.
2. **Qué suma:** `status='POSTED' AND deleted_at IS NULL`. Ingresos = `INCOME` (+ `ALLOWANCE_IN` solo en `view=PERSONAL`). Gastos = `EXPENSE`. Nunca `TRANSFER_*`/`EXCHANGE_*`; `ALLOWANCE_OUT` nunca es gasto (Regla 1).
3. **Vistas:** `PERSONAL` → `attributed_to_user_id = yo`. `FAMILY` → `VisibilityPolicy.resolveForTotals`: `FAMILY_FULL` o `FAMILY_TOTALS_ONLY` → toda la familia; `by-person` con `FAMILY_TOTALS_ONLY` → `403`.
4. **Lentes (por transacción, vía `economic_snapshots`):** `NOMINAL` → `base_currency_amount`; `CONSTANT_BASE` → `base_currency_amount × IPC_ACUMULADO(último disponible) / ipc_index_value` (si falta → nominal y `lensApproximated=true`); `REFERENCE_CURRENCY` → `amount_in_reference`; `INCOME_PCT` → montos nominales divididos por los ingresos nominales del período × 100 (`unit = "%"`; ingreso 0 → valores `null`). `unit`: base currency, `"<base> (hoy)"` para constante, referencia, o `%`.
5. **Summary:** `balance = income − expense`; `savingsRatePct = balance / income × 100` (null si income 0); `previous` = período inmediatamente anterior; `installmentCommitmentPct` = Σ cuotas `PROJECTED` (`installment_plan_id IS NOT NULL`) con fecha en el **mes calendario siguiente** al período / ingresos del período × 100; `realVariationPct` = gasto del período en lente `CONSTANT_BASE` vs promedio de los 3 períodos anteriores (null si no hay historia).
6. **by-category:** agrupar por categoría **de primer nivel** (`COALESCE(parent_category_id, id)`), sin categoría → `categoryId:null`, `name:"Sin categoría"`; ordenar desc; `variationPct` vs período anterior en lente `CONSTANT_BASE`.
7. **by-person:** agrupar gastos por `attributed_to_user_id` (Regla 5) con `full_name` y `accent_color`.
8. **calendar:** por día: totales + ítems: `TRANSACTION` (POSTED), `INSTALLMENT` (PROJECTED de planes), `RECURRING_DUE` (gastos fijos activos cuyo `due_day` cae en el día —clamp al último día del mes— y que no tienen transacción con `recurring_expense_id` en ese mes), `VEHICLE_DUE` (`vtv_expiration_date`, `insurance_expiration`, y `maintenance_logs.next_due_date` del último registro por tipo), `ALLOWANCE` (mensualidades activas según `frequency`/`day_of_period`). Respetar vista (en PERSONAL, solo lo propio/atribuido; vencimientos familiares solo en FAMILY).
9. **Caché:** clave `CacheKeys.analytics(familyId, sha256(endpoint + params ordenados + userId + view))`, TTL 5 min, JSON; invalidación ya la hace T06 al commit. Redis caído → calcular directo.
10. Queries agregadas en SQL (`date_trunc` con `AT TIME ZONE` no aplica porque `transaction_date` es `DATE`; usar `date_trunc('week'|'month'|'year', transaction_date)`). Nada de cargar todas las filas en memoria para series largas.

## Tests
- Dataset de prueba (SQL) con: ingresos, gastos en ARS y USD, transferencias, mensualidad (OUT/IN), gasto atribuido de adulto a hijo, cuotas PROJECTED, IPC de 3 meses.
- Summary: totales correctos por vista (el gasto atribuido cuenta para el hijo, no el adulto; mensualidad no duplica en FAMILY); savingsRate; previous; commitment.
- Lentes: CONSTANT_BASE con IPC; aproximado sin IPC; REFERENCE; INCOME_PCT.
- by-category con rollup al padre; by-person; `FAMILY_SUMMARY` → summary OK y by-person 403; `OWN_ONLY` FAMILY → 403.
- Series de 12 meses con meses vacíos en 0; WEEK labels ISO.
- Calendar incluye RECURRING_DUE no pagado y omite el ya pagado; rango > 62 días → 400.
- Caché: segunda llamada no consulta DB (spy) y se invalida tras crear transacción.

## Fuera de alcance
Exportaciones (T40), BFF (T21).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Contratos JSON exactos; reglas anti doble conteo verificadas por tests.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(analytics): add period resolution and lens calculator`
2. `feat(analytics): add summary and series endpoints`
3. `feat(analytics): add category and person breakdowns`
4. `feat(analytics): add calendar view and redis caching`
