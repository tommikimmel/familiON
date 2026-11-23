# T21 — feat(bff): agregación `GET /bff/home` en un solo request

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.3 | `feat/bff-home-aggregation` | Ola 2.2 | T19, T20 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- BFF existente (T04): `com.familion.bff.core.CoreClient`, `com.familion.bff.auth.AccessTokenResolver`, `com.familion.bff.aggregation.ParallelCalls` / `PartialResult`.

## Objetivo
Reducir la latencia móvil: el Inicio pide **una** URL y el BFF hace en paralelo (virtual threads) las llamadas al Core, tolerando fallos parciales.

## Archivos permitidos
```
bff/src/main/java/com/familion/bff/aggregation/home/**
bff/src/test/java/com/familion/bff/aggregation/home/**
```

## Contrato

`GET /bff/home?view=PERSONAL|FAMILY&lens=...&granularity=...&date=yyyy-MM-dd` (todos opcionales; se reenvían tal cual) → `200 HomePayload`.

Llamadas al Core (en paralelo, timeout global 3 s):

| Campo | Core | Notas |
|---|---|---|
| `summary` | `GET /analytics/summary?{params}` | |
| `byCategory` | `GET /analytics/by-category?{params}` | |
| `byPerson` | `GET /analytics/by-person?{params}` | **solo** si `view=FAMILY`; `403` → `null` |
| `recentTransactions` | `GET /transactions?view=&lens=&size=10&sort=transactionDate,desc` | tomar `content` del page |
| `latestRates` | `GET /economics/indices/latest` | |
| `unreadNotifications` | `GET /notifications/unread-count` → `{ "count": n }` | endpoint de T36; hasta entonces `404` → `null` |

```ts
type HomePayload = { summary: object|null; byCategory: object|null; byPerson: object|null;
  recentTransactions: object[]|null; latestRates: object[]|null; unreadNotifications: number|null };
```
Los sub-objetos se reenvían **sin transformar** (`JsonNode`), así el BFF no se acopla a los DTOs del Core.

## Reglas
1. Sin cookie `FAMILION_AT` → `401` problem+json sin llamar al Core.
2. Si **cualquier** llamada devuelve `401` → responder `401` (el frontend refresca y reintenta).
3. Cualquier otro error o timeout → ese campo `null` (log DEBUG con el status).
4. `Cache-Control: no-store`.
5. Implementar con `ParallelCalls.call(...)` + `awaitAll(Duration.ofSeconds(3), ...)`.

## Tests (WireMock como Core)
- Todas OK → payload completo y cada request llevó el `Authorization: Bearer` correcto y los params.
- `view=PERSONAL` → no se llama a `by-person`.
- `by-person` 403 → `byPerson: null`, resto OK.
- Una llamada con delay de 5 s → ese campo `null` y la respuesta tarda < 3,5 s.
- Una llamada 401 → respuesta 401.
- Sin cookie → 401 y WireMock sin requests.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde (en `bff/`).
- [ ] Respuesta en tiempo ≈ máx(llamadas), no la suma.

## Verificación
```bash
cd bff && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(bff): add home aggregation endpoint with partial failure tolerance`
