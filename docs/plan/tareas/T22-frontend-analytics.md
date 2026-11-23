# T22 — feat(frontend): pantalla Analítica (períodos, lentes, series, tortas y calendario)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.4 | `feat/frontend-analytics` | Ola 2.3 (T19) | T23, T24 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/state/view-context.store.ts`, `core/finance/finance-events.ts`, `shared/ui/*` (`fo-donut-chart`, `fo-segmented-toggle`, `fo-card`), `shared/pipes/money.pipe.ts`.
- Opcional como referencia visual: `features/home/components/evolution-chart*` (T18) — se puede reutilizar importándolo.

## Objetivo
Pantalla `/analytics` para entender el gasto real: navegación Día/Semana/Mes/Año, selector explícito de las 4 lentes, comparación con el período anterior, evolución, tortas por categoría y persona, y calendario de movimientos y vencimientos.

## Archivos permitidos
```
frontend/src/app/features/analytics/**
```

## Contrato BFF (proxy genérico al Core)

| Llamada | Respuesta |
|---|---|
| `GET /bff/api/analytics/summary?view&lens&granularity&date` | `AnalyticsSummary` |
| `GET /bff/api/analytics/series?view&lens&granularity&date&periods=12` | `Series` |
| `GET /bff/api/analytics/by-category?view&lens&granularity&date` | `CategoryBreakdown` |
| `GET /bff/api/analytics/by-person?...` (solo FAMILY; `403` posible) | `PersonBreakdown` |
| `GET /bff/api/analytics/calendar?view&from&to` (≤ 62 días) | `CalendarView` |

```ts
type Decimal = string;
type Period = { granularity; from: string; to: string; label: string };
type AnalyticsSummary = { period: Period; view; lens; unit: string; income: Decimal; expense: Decimal; balance: Decimal;
  savingsRatePct: Decimal|null; installmentCommitmentPct: Decimal|null; previous: { income: Decimal; expense: Decimal };
  realVariationPct: Decimal|null; lensApproximated: boolean };
type Series = { lens; unit; granularity; points: { periodStart: string; label: string; income: Decimal; expense: Decimal; balance: Decimal }[] };
type CategoryBreakdown = { period: Period; lens; unit; total: Decimal;
  slices: { categoryId: string|null; name; colorHex: string|null; icon: string|null; amount: Decimal; pct: Decimal; variationPct: Decimal|null }[] };
type PersonBreakdown = { period: Period; lens; unit; total: Decimal; slices: { userId; fullName; accentColor; amount: Decimal; pct: Decimal }[] };
type CalendarView = { from; to; days: { date; income: Decimal; expense: Decimal;
  items: { kind: 'TRANSACTION'|'INSTALLMENT'|'RECURRING_DUE'|'VEHICLE_DUE'|'ALLOWANCE'; refId; title; amount: Decimal|null; currencyCode: string|null }[] }[] };
```

## Especificación
1. **`AnalyticsStore`** (`data-access/`): carga en paralelo summary, series, by-category y (si FAMILY) by-person al cambiar `ViewContextStore.queryParams()` o `FinanceEvents.last()`; `403` en by-person → ocultar sección.
2. **Cabecera:** selector `Día | Semana | Mes | Año` + flechas `‹ ›` + «Hoy» (usa `ViewContextStore`); label del período; selector de lente con etiquetas humanas: «Pesos del día» (NOMINAL), «A plata de hoy» (CONSTANT_BASE), «En {referencia}» (REFERENCE_CURRENCY, ej. «En dólares»), «% del ingreso» (INCOME_PCT). Explicación corta debajo de la lente activa (1 línea).
3. **Resumen:** ingresos, gastos, balance, tasa de ahorro; comparación con período anterior («Gastos −5% vs. octubre»); `realVariationPct` como semáforo («Este mes gastaron +8% real respecto al promedio de los últimos 3 meses»: verde ≤ 0, ámbar ≤ 10, rojo > 10); `installmentCommitmentPct` («Ya comprometido en cuotas: 18% del ingreso»).
4. **Evolución:** gráfico de 12 períodos (barras ingreso/gasto + línea balance), tooltip/selección por punto; se re-expresa al cambiar lente.
5. **Tortas:** `fo-donut-chart` por categoría (con variación real por ítem) y por persona.
6. **Calendario** (tab «Calendario»): grilla mensual (lunes primero) con totales por día (punto de color si hay vencimientos); al tocar un día, lista de ítems con ícono por `kind` («Cuota», «Gasto fijo», «Vencimiento auto», «Mensualidad»). Navegación de mes independiente del período.
7. Si `lensApproximated` → banner discreto.

## Tests
- `analytics.store.spec.ts`: requests correctos por vista, oculta by-person en 403, recarga por eventos.
- Lógica de semáforo y textos de comparación (funciones puras).
- Grilla de calendario: semanas correctas para noviembre 2026 (empieza domingo 1 → primera fila con 6 celdas vacías), marca vencimientos.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Cambiar lente actualiza todos los bloques; mobile y desktop usables.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(analytics-ui): add analytics store with period and lens navigation`
2. `feat(analytics-ui): add summary, evolution and breakdown sections`
3. `feat(analytics-ui): add monthly calendar with due items`
