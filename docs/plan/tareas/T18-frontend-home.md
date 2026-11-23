# T18 — feat(frontend): pantalla Inicio (balance, donut Categorías/Personas, últimos movimientos)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.2 | `feat/frontend-home` | Ola 2.1 (T15) | T16, T17 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/state/view-context.store.ts`, `core/finance/finance-events.ts`, `shared/ui/*` (`fo-donut-chart`, `fo-card`, `foCountUp`, `fo-skeleton`), `shared/pipes/money.pipe.ts`, `shared/utils/decimal.ts`.

## Objetivo
La pantalla principal: balance del período con la lente activa, ingresos vs gastos, gráfico de torta por categorías o personas y últimos 10 movimientos, todo en **un** request (`/bff/home`). En desktop agrega KPIs y evolución de 6 meses.

## Archivos permitidos
```
frontend/src/app/features/home/**
```

## Contrato BFF

`GET /bff/home?view=&lens=&granularity=&date=` (params = `ViewContextStore.queryParams()`) → `HomePayload`. Cualquier campo puede venir `null` (falló esa parte); `401` lo maneja el interceptor.

`GET /bff/api/analytics/series?view=&lens=&granularity=MONTH&periods=6&date=` → `Series` (solo desktop).

```ts
type Decimal = string;
type Period = { granularity: 'DAY'|'WEEK'|'MONTH'|'YEAR'; from: string; to: string; label: string };
type AnalyticsSummary = { period: Period; view; lens; unit: string; income: Decimal; expense: Decimal; balance: Decimal;
  savingsRatePct: Decimal|null; installmentCommitmentPct: Decimal|null; previous: { income: Decimal; expense: Decimal };
  realVariationPct: Decimal|null; lensApproximated: boolean };
type CategoryBreakdown = { period: Period; lens; unit; total: Decimal;
  slices: { categoryId: string|null; name; colorHex: string|null; icon: string|null; amount: Decimal; pct: Decimal; variationPct: Decimal|null }[] };
type PersonBreakdown = { period: Period; lens; unit; total: Decimal;
  slices: { userId; fullName; accentColor; amount: Decimal; pct: Decimal }[] };
type IndexValue = { indexCode; effectiveDate; value: Decimal; source: 'API'|'MANUAL' };
type TransactionItem = { id; type; scope; status; amount: Decimal; currencyCode; transactionDate; description; notes;
  category: { id; name; icon; colorHex } | null; account: { id; name; accountType }; paidBy: { id; fullName };
  attributedTo: { id; fullName }; installment: { planId; number; total } | null; lens; lensAmount: Decimal|null; lensUnit; lensApproximated };
type HomePayload = { summary: AnalyticsSummary|null; byCategory: CategoryBreakdown|null; byPerson: PersonBreakdown|null;
  recentTransactions: TransactionItem[]|null; latestRates: IndexValue[]|null; unreadNotifications: number|null };
type Series = { lens; unit; granularity; points: { periodStart; label; income: Decimal; expense: Decimal; balance: Decimal }[] };
```
> Los endpoints los implementan T19/T21 (ola 2.3). Hasta que estén, el request falla: la pantalla debe mostrar estado de error con «Reintentar» sin romperse. Para desarrollar, crear `features/home/data-access/home.fixtures.ts` con un `HomePayload` realista usado en tests y en un modo demo (`?demo=1`).

## Especificación

1. **`HomeStore`** (`data-access/home.store.ts`): `payload`, `series`, `loading`, `error`; `effect()` que recarga cuando cambian `ViewContextStore.queryParams()` o `FinanceEvents.last()`. Cancelar el request anterior (switchMap). `NotificationBadgeService.set(unreadNotifications ?? 0)` si viene dato.
2. **Mobile:**
   - **Hero card** (ambient glow sutil del acento: `radial-gradient` con `--fo-accent-soft`): label del período (`summary.period.label`), balance grande con `foCountUp` y `money` en `summary.unit` (si lente `INCOME_PCT`, mostrar `%`), debajo «Ingresos» y «Gastos» con flechas, y chip «Ahorro 23%» (`savingsRatePct`). Si `lensApproximated` → nota pequeña «Valores aproximados: falta el índice de inflación más reciente».
   - **Donut card:** `fo-segmented-toggle` «Categorías | Personas» (Personas visible solo con `view=FAMILY` y `byPerson != null`). `fo-donut-chart` con slices (colores: `colorHex` si existe, si no escala tonal; personas usan `accentColor`). Leyenda tocable con monto y %; en categorías mostrar `variationPct` como «+8% real» en verde/rojo.
   - **Cotización:** pill con la referencia de la familia (p. ej. «Dólar MEP $1.185» desde `latestRates`).
   - **Últimos movimientos:** lista de 10 (ícono de categoría, descripción/categoría, «Pagado por X» si `paidBy.id !== attributedTo.id`, «Cuota 2/6» si aplica, monto con signo y color por tipo, `lensAmount` como subtítulo cuando la lente no es NOMINAL). «Ver todos» → `/transactions`.
3. **Desktop (≥1024px)** — grilla:
   1. 3 KPI cards: Balance (con glow), Gastos vs Ingresos (barra comparativa), Divisas + Cuotas (`latestRates` principal y `installmentCommitmentPct` «Comprometido en cuotas: 18% del ingreso»).
   2. 2 gráficos lado a lado: evolución 6 meses (barras ingreso/gasto + línea balance en SVG propio, se re-expresa al cambiar lente) y donut.
   3. Tabla de movimientos (columnas: Movimiento, Categoría, Atribuido a / pagó, Cuenta/Cuota, Fecha, Monto) con buscador instantáneo (filtro en cliente sobre los 10).
4. Skeletons durante la carga; `fo-empty-state` «Todavía no hay movimientos en este período. Toca + para cargar el primero.».
5. Componentes presentacionales en `components/` (`hero-balance`, `breakdown-card`, `recent-list`, `kpi-card`, `evolution-chart`, `movements-table`); `pages/home.page.ts` es el contenedor.

## Tests
- `home.store.spec.ts`: arma query params correctos, recarga ante evento de finanzas, maneja `null` parciales y error.
- Componentes: hero con `INCOME_PCT` muestra `%`; recent-list muestra «Pagado por» y «Cuota 2/6»; toggle Personas oculto en `PERSONAL`.
- `evolution-chart`: escala y alturas correctas con fixtures.

## Fuera de alcance
Backend/BFF (T19, T21), analítica completa (T22).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] `?demo=1` renderiza la pantalla completa con fixtures en mobile y desktop.
- [ ] Cambiar Personal/Familia, lente o período recarga los datos.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(home): add home store backed by bff aggregation`
2. `feat(home): add hero balance, breakdown donut and recent movements`
3. `feat(home): add desktop dashboard grid with kpis and evolution chart`
