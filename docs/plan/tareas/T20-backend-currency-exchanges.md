# T20 — feat(finance): compra/venta de divisas con tipo de cambio real y precio promedio ponderado

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.3 | `feat/backend-currency-exchanges` | Ola 2.2 (T16) | T19, T21 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **215–233** (`currency_exchanges`).
- APIs: `TransactionCommandApi` (T16), `AccountQueryApi`, `CategoryQueryApi` (T13), `FamilyQueryApi` (T07), `EconomicsApi` (T09), `VisibilityPolicy` (T06).

## Objetivo
Registrar operaciones cambiarias (ej. comprar USD MEP con ARS) guardando el tipo de cambio **real** pagado, generar las dos patas contables, y calcular la tenencia y el **precio promedio ponderado (PPP)** de cada divisa con su resultado contra la cotización actual.

## Archivos permitidos
```
backend/src/main/java/com/familion/finance/exchanges/**
backend/src/test/java/com/familion/finance/exchanges/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/currency-exchanges` | auth | `ExchangeCreate` | `201 Exchange` |
| GET | `/api/v1/currency-exchanges?view=&page=&size=` | auth | — | `Page<Exchange>` (fecha desc) |
| GET | `/api/v1/currency-exchanges/positions?view=` | auth | — | `CurrencyPosition[]` |
| DELETE | `/api/v1/currency-exchanges/{id}` | autor / ADULTO | — | `204` |

```ts
type ExchangeCreate = { sourceAccountId: string; targetAccountId: string; sourceAmount: Decimal; targetAmount: Decimal;
  appliedExchangeRate?: Decimal; feeAmount?: Decimal; feeCurrency?: string; exchangeDate?: string; notes?: string };
type Exchange = { id; exchangeDate; sourceAccount: { id; name }; targetAccount: { id; name };
  sourceAmount: Decimal; sourceCurrency: string; targetAmount: Decimal; targetCurrency: string;
  appliedExchangeRate: Decimal; feeAmount: Decimal; feeCurrency: string|null; notes: string|null;
  operation: 'BUY'|'SELL'|'SWAP'; createdBy: { id; fullName } };
type CurrencyPosition = { currencyCode: string; units: Decimal; weightedAvgRate: Decimal|null; totalCostBase: Decimal;
  currentRate: Decimal|null; currentValueBase: Decimal|null; unrealizedResultBase: Decimal|null; unrealizedResultPct: Decimal|null };
```

## Reglas

1. **Validaciones:** ambas cuentas `requireUsable`; divisas = divisas de cada cuenta y distintas entre sí (`422`); cuentas distintas; montos > 0; `exchangeDate` default hoy (zona familia).
2. **Operación y tasa** (siempre "unidades de moneda base por 1 unidad de la otra"):
   - `BUY` (origen = base, destino = otra): `rate = sourceAmount / targetAmount`.
   - `SELL` (origen = otra, destino = base): `rate = targetAmount / sourceAmount`.
   - `SWAP` (ninguna es base, ej. USD→EUR): `rate = sourceAmount / targetAmount` (unidades de origen por 1 de destino).
   - Si llega `appliedExchangeRate` y difiere > 0,5% del calculado → `422`. Guardar con escala 6.
3. **Persistencia ACID** (un solo `@Transactional`): fila `currency_exchanges` + `EXCHANGE_OUT` (cuenta origen, `sourceAmount`/`sourceCurrency`) + `EXCHANGE_IN` (cuenta destino) vía `TransactionCommandApi.record` con `currencyExchangeId` y un `transferGroupId` compartido, `attributedTo = user`, scope `PERSONAL`, sin categoría. Si `feeAmount > 0`: `EXPENSE` adicional desde la cuenta origen en `feeCurrency` (default = moneda origen) con categoría `FINANCIAL_COSTS` (`requireSystemCategory`), mismo `currencyExchangeId`.
4. **Borrar:** `softDelete` de todas las transacciones con ese `currency_exchange_id`; la fila queda (histórico) pero se excluye de listados y posiciones (filtrar por existencia de su `EXCHANGE_IN` no borrado).
5. **Posiciones (método de costo promedio):** por divisa ≠ base, recorrer operaciones no borradas en orden cronológico (vista aplicada: PERSONAL = `user_id = yo`; FAMILY = toda la familia según `resolveForListing`):
   - BUY: `units += target`, `cost += source` (en base).
   - SELL: `avg = cost/units`; `units -= source`; `cost -= source × avg` (si units queda ≤ 0 → reset a 0).
   - SWAP: se trata como SELL de la moneda origen (valuada a su PPP) y BUY de la destino con ese costo.
   - `weightedAvgRate = cost / units` (null si units = 0); `currentRate` = `EconomicsApi.latestValue` de la fuente de la divisa (USD → `referenceRateSource` si es USD_*, si no `USD_MEP`; EUR → `EUR_OFICIAL`); `currentValueBase = units × currentRate`; resultado = valor actual − costo; pct = resultado / costo × 100.

## Tests
- Compra 1000 USD por 1.185.000 ARS → rate 1185.000000, dos transacciones con mismo grupo, saldos de cuentas correctos (usar `AccountQueryApi.currentBalance`).
- Venta posterior de 200 USD por 250.000 ARS → posición 800 USD con PPP 1185 y costo 948.000.
- Comisión crea EXPENSE en `FINANCIAL_COSTS`.
- `appliedExchangeRate` inconsistente → 422; misma divisa → 422.
- Delete excluye de posiciones; otra familia → 404; HIJO `OWN_ONLY` con view FAMILY → 403.

## Fuera de alcance
Pantalla (T24), agregación del plan (T29).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Toda operación es atómica (si falla una pata, no queda nada).

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(exchanges): add currency exchange registration with mirrored transactions`
2. `feat(exchanges): add weighted average cost positions`
