# T24 — feat(frontend): compra/venta de divisas e historial con precio promedio

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.4 | `feat/frontend-exchanges` | Ola 2.3 (T20) | T22, T23 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/finance/accounts.store.ts`, `finance-events.ts`, `core/state/*`, `shared/ui/*`, `shared/pipes/money.pipe.ts`.

## Objetivo
Pantalla `/plan/exchanges`: registrar compras/ventas de divisas con el tipo de cambio real y ver tenencias con precio promedio ponderado y resultado contra la cotización actual.

## Archivos permitidos
```
frontend/src/app/features/plan/exchanges/**
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `POST /bff/api/currency-exchanges` | `ExchangeCreate` | `Exchange` |
| `GET /bff/api/currency-exchanges?view=&page=&size=` | — | `{ content: Exchange[], page }` |
| `GET /bff/api/currency-exchanges/positions?view=` | — | `CurrencyPosition[]` |
| `DELETE /bff/api/currency-exchanges/{id}` | — | `204` |
| `GET /bff/api/economics/indices/latest` | — | `IndexValue[]` |

```ts
type ExchangeCreate = { sourceAccountId; targetAccountId; sourceAmount: Decimal; targetAmount: Decimal;
  appliedExchangeRate?: Decimal; feeAmount?: Decimal; feeCurrency?: string; exchangeDate?: string; notes?: string };
type Exchange = { id; exchangeDate; sourceAccount: { id; name }; targetAccount: { id; name }; sourceAmount: Decimal; sourceCurrency;
  targetAmount: Decimal; targetCurrency; appliedExchangeRate: Decimal; feeAmount: Decimal; feeCurrency: string|null; notes: string|null;
  operation: 'BUY'|'SELL'|'SWAP'; createdBy: { id; fullName } };
type CurrencyPosition = { currencyCode; units: Decimal; weightedAvgRate: Decimal|null; totalCostBase: Decimal; currentRate: Decimal|null;
  currentValueBase: Decimal|null; unrealizedResultBase: Decimal|null; unrealizedResultPct: Decimal|null };
type IndexValue = { indexCode; effectiveDate; value: Decimal; source };
```
Tasa = unidades de moneda base por 1 unidad de la otra (BUY: origen/destino; SELL: destino/origen).

## Especificación
1. **Posiciones** (arriba): tarjeta por divisa: tenencia («USD 800»), PPP («Compraste a $1.185 promedio»), cotización actual, valor actual en base y resultado (verde/rojo con %).
2. **Nueva operación** (botón → `fo-bottom-sheet`): toggle «Comprar | Vender»; cuenta origen y destino filtradas por divisa (`AccountsStore`); «Pago» y «Recibo» montos; la tasa se calcula en vivo y se muestra «1 USD = $1.185,00»; alternativa: ingresar la tasa y calcular el otro monto (el último campo editado manda). Comisión opcional (monto + divisa). Fecha (hoy) y nota. Mostrar la cotización de referencia del día para comparar («MEP hoy: $1.190 · pagaste 0,4% menos»).
3. **Historial**: lista paginada (fecha, operación, montos, tasa, comisión, quién) con eliminar (autor/ADULTO) con confirmación.
4. Tras crear/borrar: recargar, `FinanceEvents.emit('plan-changed')`, toast.

## Tests
- Cálculo bidireccional monto/tasa y redondeos (función pura `exchange-math.ts`).
- Store: crear → recarga posiciones; delete.
- Render de posición con resultado positivo/negativo.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(exchanges-ui): add exchange math helpers`
2. `feat(exchanges-ui): add positions, new exchange sheet and history`
