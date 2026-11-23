# T23 — feat(frontend): listado de movimientos con filtros, edición y baja

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.4 | `feat/frontend-transactions` | Ola 2.3 | T22, T24 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/finance/*` (`transactions.api.ts` y `transactions.models.ts` de T17, stores de T15, `finance-events.ts`), `core/state/*`, `shared/ui/*`.

## Objetivo
Pantalla `/transactions`: todos los movimientos de la vista activa, con filtros, búsqueda, scroll infinito, detalle, edición y baja, mostrando atribución, cuotas y monto en la lente elegida.

## Archivos permitidos
```
frontend/src/app/features/transactions/**
frontend/src/app/core/finance/transactions.api.ts      (agregar métodos list/get/update/remove)
frontend/src/app/core/finance/transactions.models.ts   (agregar tipos de filtro/patch/page)
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `GET /bff/api/transactions` | query `view, lens, from, to, type, categoryId, accountId, attributedToUserId, currency, status, q, page, size (≤50), sort` | `{ content: TransactionItem[], page: { size, number, totalElements, totalPages } }` |
| `GET /bff/api/transactions/{id}` | — | `TransactionItem` |
| `PATCH /bff/api/transactions/{id}` | `{ amount?, transactionDate?, categoryId?, description?, notes?, accountId?, attributedToUserId?, scope? }` | `TransactionItem` · `409` si el tipo no permite el cambio |
| `DELETE /bff/api/transactions/{id}` | — | `204` · `409` si es cuota de un plan |
| `GET /bff/api/family/members` | — | `[{ id, fullName, role, accentColor, isActive }]` |

`TransactionItem` ya está en `transactions.models.ts` (T17).

## Especificación
1. **`TransactionsStore`** (`features/transactions/data-access/`): filtros en signals sincronizados con query params de la URL (compartible), página actual, `items` acumulados, `hasMore`; reset y recarga al cambiar filtros, `ViewContextStore.view/lens` o `FinanceEvents.last()`. Debounce 300 ms en `q`.
2. **Filtros** (barra en desktop, sheet «Filtros» en mobile): rango de fechas (default: período de `ViewContextStore`), tipo (Gastos / Ingresos / Transferencias / Divisas / Mensualidades), categoría, cuenta, persona atribuida (solo FAMILY), divisa, estado (incluir «Proyectadas»). Chips de filtros activos con «×».
3. **Lista** agrupada por día («Hoy», «Ayer», «Lunes 21 nov»), con total del día. Cada fila: ícono de categoría, descripción o categoría, subtítulo con cuenta · «Pagado por X» (si difiere de atribuido) · «Cuota 2/6» · «Proyectada», monto con signo/color, y `lensAmount lensUnit` cuando la lente no es NOMINAL. Scroll infinito (`IntersectionObserver`), `size=30`.
4. **Desktop:** tabla con columnas Movimiento, Categoría, Atribuido a / pagó, Cuenta/Cuota, Fecha, Monto; ordenar por fecha o monto.
5. **Detalle** (sheet al tocar): todos los campos + snapshot implícito («Equivalía a USD 25,30 ese día» usando `lens=REFERENCE_CURRENCY` → pedir `GET /transactions/{id}?lens=REFERENCE_CURRENCY`). Acciones: Editar (formulario con los campos del PATCH; «Para:» solo ADULTO; deshabilitar monto/fecha si es cuota o divisa con explicación) y Eliminar (confirmación; para cuotas mostrar «Las cuotas se gestionan desde Plan → Cuotas» con link). Tras cambios: `FinanceEvents.emit('transaction-updated'|'transaction-deleted')` y toast.
6. Estados vacío/carga/error.

## Tests
- Store: sincroniza filtros ↔ URL, paginación acumulativa, reset al cambiar vista.
- Agrupación por día con totales (función pura).
- Editar envía PATCH solo con campos cambiados; `409` muestra mensaje del problem detail.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] La URL con filtros es compartible y restaura el estado.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(transactions-ui): add transactions store with url-synced filters`
2. `feat(transactions-ui): add grouped list, desktop table and infinite scroll`
3. `feat(transactions-ui): add detail sheet with edit and delete`
