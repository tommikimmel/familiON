# T32 — feat(frontend): pantalla resumen «Plan»

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.3 | `feat/frontend-plan-overview` | Ola 3.2 (T29) | T31 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/state/*`, `core/finance/finance-events.ts`, `shared/ui/*`, `shared/pipes/money.pipe.ts`.

## Objetivo
`/plan` (tab «Plan» de la bottom nav): vista única de la planificación mensual del hogar con accesos a cada sección.

## Archivos permitidos
```
frontend/src/app/features/plan/overview/**
```

## Contrato BFF

`GET /bff/plan?view=&month=yyyy-MM` → `PlanPayload` (cada campo puede ser `null`):
```ts
type PlanPayload = {
  positions: { currencyCode; units: Decimal; weightedAvgRate: Decimal|null; currentRate: Decimal|null; unrealizedResultPct: Decimal|null }[] | null;
  installments: { id; description; totalInstallments; paidInstallments; remainingAmount: Decimal; currencyCode;
                  nextInstallment: { number; date; amount: Decimal } | null }[] | null;
  commitment: { month: string; amount: Decimal; currencyCode }[] | null;
  recurring: { id; name; month: { dueDate; estimatedAmount: Decimal; status: 'PENDING'|'PAID'|'OVERDUE'|'NOT_APPLICABLE' }; currencyCode }[] | null;
  goals: { id; name; progressPct: Decimal; targetUnit; accumulatedInUnit: Decimal; targetAmount: Decimal }[] | null;
  allowances: { id; child: { id; fullName; accentColor }; currentAmount: Decimal; currencyCode; nextDisbursementDate }[] | null };
```
(Se usan solo los campos listados; el resto se ignora.)

## Especificación
1. `PlanOverviewStore` recarga con `ViewContextStore.view` y `FinanceEvents.last()`.
2. Secciones (cards con «Ver todo →» a su subruta):
   - **Este mes** (gastos fijos): «3 pendientes · $X», próximo vencimiento destacado, vencidos en rojo → `/plan/recurring`.
   - **Cuotas**: total pendiente, mini-barras de los próximos 6 meses (`commitment`), próxima cuota → `/plan/installments`.
   - **Divisas**: chips por divisa con tenencia y resultado % → `/plan/exchanges`; botón «Comprar/Vender».
   - **Metas**: 3 primeras con anillos de progreso → `/plan/goals`.
   - **Mensualidades** (si hay): por hijo, monto y próxima fecha → `/plan/allowances`.
3. Mobile: columna; desktop: grilla de 2–3 columnas. Secciones `null` → card con «No se pudo cargar» + reintentar; vacías → CTA para crear.

## Tests
- Store y render con fixtures (todas las secciones, y con campos `null`).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(plan-ui): add plan overview backed by bff aggregation`
