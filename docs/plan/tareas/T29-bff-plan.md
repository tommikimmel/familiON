# T29 — feat(bff): agregación `GET /bff/plan`

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.2 | `feat/bff-plan-aggregation` | Ola 3.1 | T28, T30 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- BFF: `CoreClient`, `AccessTokenResolver`, `ParallelCalls`/`PartialResult` (T04). Como ejemplo de patrón: `com.familion.bff.aggregation.home` (T21).

## Objetivo
Un solo request para la pantalla "Plan": divisas con PPP, cuotas activas y compromiso futuro, gastos fijos del mes, metas y mensualidades.

## Archivos permitidos
```
bff/src/main/java/com/familion/bff/aggregation/plan/**
bff/src/test/java/com/familion/bff/aggregation/plan/**
```

## Contrato

`GET /bff/plan?view=PERSONAL|FAMILY&month=yyyy-MM` → `200 PlanPayload` (sub-objetos reenviados como `JsonNode`, sin transformar).

| Campo | Core | Notas |
|---|---|---|
| `positions` | `GET /currency-exchanges/positions?view=` | |
| `installments` | `GET /installment-plans?status=ACTIVE&view=` | |
| `commitment` | `GET /installment-plans/commitment?months=6&view=` | |
| `recurring` | `GET /recurring-expenses?month=` | `month` default = mes actual (zona `America/Argentina/Buenos_Aires`) |
| `goals` | `GET /savings-goals?view=&status=ACTIVE` | |
| `allowances` | `GET /family/allowances` | `404` (aún no desplegado) → `null` |

```ts
type PlanPayload = { positions: object[]|null; installments: object[]|null; commitment: object[]|null;
  recurring: object[]|null; goals: object[]|null; allowances: object[]|null };
```

## Reglas
Iguales a `/bff/home`: sin cookie → `401`; cualquier `401` del Core → `401`; otros errores/timeout (3 s) → campo `null`; `Cache-Control: no-store`; llamadas en paralelo con `ParallelCalls`.

## Tests (WireMock)
- Payload completo con params correctos y Bearer.
- `month` default calculado.
- Fallo parcial → `null`; 401 → 401; timeout → `null` en < 3,5 s.

## Criterios de aceptación
- [ ] `cd bff && ./mvnw -B verify` en verde.

## Verificación
```bash
cd bff && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(bff): add plan aggregation endpoint`
