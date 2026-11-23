# T35 — feat(bff): agregación `GET /bff/vehicles/{id}/dashboard`

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 4 | 4.1 | `feat/bff-vehicle-dashboard` | Fase 3 completa | T33, T34 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- BFF: `CoreClient`, `AccessTokenResolver`, `ParallelCalls`/`PartialResult` (T04); patrón de ejemplo en `aggregation/home` (T21) y `aggregation/plan` (T29).

## Objetivo
Un request para el dashboard del auto: ficha, métricas, últimas cargas y services, y alertas.

## Archivos permitidos
```
bff/src/main/java/com/familion/bff/aggregation/vehicles/**
bff/src/test/java/com/familion/bff/aggregation/vehicles/**
```

## Contrato

`GET /bff/vehicles/{id}/dashboard?lens=&from=&to=` → `200 VehicleDashboard`:

| Campo | Core |
|---|---|
| `vehicle` | `GET /vehicles/{id}` |
| `summary` | `GET /vehicles/{id}/summary?lens=&from=&to=` |
| `recentFuelLogs` | `GET /vehicles/{id}/fuel-logs?size=5` → `content` |
| `recentMaintenance` | `GET /vehicles/{id}/maintenance-logs?size=5` → `content` |
| `alerts` | derivado: `vehicle.alerts` (si `vehicle` es `null` → `null`) |

```ts
type VehicleDashboard = { vehicle: object|null; summary: object|null; recentFuelLogs: object[]|null;
  recentMaintenance: object[]|null; alerts: object[]|null };
```

## Reglas
- `id` debe ser UUID (`400` si no).
- `vehicle` del Core `404` → responder `404` (el auto no existe o es de otra familia).
- Resto igual a las otras agregaciones: sin cookie `401`; cualquier `401` → `401`; otros fallos/timeout 3 s → `null`; `Cache-Control: no-store`; paralelo con `ParallelCalls`.

## Tests (WireMock)
- Payload completo; `alerts` derivado de `vehicle`; vehicle 404 → 404; summary 500 → `summary: null`; 401 → 401; id inválido → 400.

## Criterios de aceptación
- [ ] `cd bff && ./mvnw -B verify` en verde.

## Verificación
```bash
cd bff && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(bff): add vehicle dashboard aggregation endpoint`
