# T09 — feat(economics): motor anti-inflación (índices, sincronización, snapshot y conversión)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 1 | 1.1 | `feat/backend-economics` | Ola 0.2 (T06) | T07, T08 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **107–115** (`economic_indices`) y **282–293** (`economic_snapshots`, solo para entender los campos que produce `snapshotFor`).

## Objetivo
Módulo `economics`: guardar cotizaciones e índices (USD oficial/MEP/blue, EUR, UVA, IPC), sincronizarlos desde APIs públicas argentinas sin bloquear nunca a los usuarios, permitir carga manual, y exponer `EconomicsApi` para congelar el contexto económico de cada transacción.

## Archivos permitidos
```
backend/src/main/java/com/familion/economics/**
backend/src/test/java/com/familion/economics/**
backend/src/test/resources/econ/**
```
> No editar `pom.xml` ni `application.yml`: leer propiedades con `@Value("${ECON_DOLARAPI_URL:https://dolarapi.com}")`, etc. Para HTTP usar `RestClient` (ya incluido en `spring-boot-starter-web`); para tests `MockRestServiceServer`.

## Contrato (firmas exactas)

```java
package com.familion.economics.application.api;
public record EconomicSnapshotData(String referenceCurrency, String referenceRateSource, BigDecimal referenceExchangeRate,
                                   BigDecimal amountInReference, BigDecimal baseCurrencyAmount,
                                   BigDecimal ipcIndexValue, BigDecimal uvaValue, LocalDate snapshotDate) {}
public interface EconomicsApi {
  EconomicSnapshotData snapshotFor(FamilySettings family, LocalDate date, BigDecimal amount, String currencyCode);
  BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date, String rateSource);
  Optional<BigDecimal> valueAt(String indexCode, LocalDate date);   // último valor con effective_date <= date
  Optional<BigDecimal> latestValue(String indexCode);
}
```
`FamilySettings` está en `com.familion.family.application.api` (lo crea T07 en paralelo). **Para no depender de T07**, crear en tu módulo un record local `com.familion.economics.application.api.FamilyEconomicContext(String baseCurrency, String referenceCurrency, String referenceRateSource)` y definir `snapshotFor(FamilyEconomicContext ctx, ...)`. T16 adaptará `FamilySettings` → `FamilyEconomicContext` (una línea). Esta es la firma final:
```java
EconomicSnapshotData snapshotFor(FamilyEconomicContext ctx, LocalDate date, BigDecimal amount, String currencyCode);
```

**Endpoints** (`/api/v1/economics`):

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/indices/latest` | auth | `IndexValue[]` (un ítem por código con dato) |
| GET | `/indices/history?indexCode=&from=&to=` | auth | `IndexValue[]` ordenado por fecha asc; rango máx 2 años (`400` si excede) |
| POST | `/indices/manual` | ADULTO | body `{ indexCode, effectiveDate, value }` → `201 IndexValue` |

```ts
type IndexValue = { indexCode: string; effectiveDate: string; value: string /* decimal */; source: 'API'|'MANUAL' };
```
Códigos (`IndexCode` enum): `USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA, IPC_MENSUAL, IPC_ACUMULADO`.

## Especificación

1. **Entidad** `EconomicIndex` (`economic_indices`), repositorio con `findTopByIndexCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc`, búsqueda por rango y upsert.
2. **Clientes externos** (`infrastructure.external`), timeouts connect 5s / read 10s:
   - `DolarApiClient` (`${ECON_DOLARAPI_URL}`): `GET /v1/dolares` → mapear `casa`: `oficial→USD_OFICIAL`, `blue→USD_BLUE`, `bolsa→USD_MEP`; usar el valor **`venta`**; fecha = `fechaActualizacion` (en zona AR). EUR: `GET /v1/cotizaciones/eur` → `EUR_OFICIAL` (si existe un endpoint blue de EUR, mapear a `EUR_BLUE`).
   - `ArgentinaDatosClient` (`${ECON_ARGDATOS_URL}`): históricos de dólar `GET /v1/cotizaciones/dolares/{casa}` (backfill), inflación mensual `GET /v1/finanzas/indices/inflacion` (`{fecha, valor}` = % mensual) → `IPC_MENSUAL` con `effective_date` = primer día del mes, UVA `GET /v1/finanzas/indices/uva` → `UVA` diario.
   - **Antes de codificar, verificar con `curl` las URLs y la forma real de los JSON** y guardar respuestas reales recortadas como fixtures en `src/test/resources/econ/*.json`. Si alguna ruta difiere, adaptar el cliente y documentarlo en la PR.
3. **`EconomicsSyncService`:**
   - `@Scheduled(cron = "${ECON_SYNC_CRON:0 0 */6 * * *}", zone = "America/Argentina/Buenos_Aires")` → sync de últimos valores (DolarApi) + IPC + UVA de los últimos 60 días.
   - Al arrancar (`ApplicationReadyEvent`, asíncrono): si la tabla está vacía, backfill de 2 años (dólares, UVA, IPC). Controlado por `@Value("${familion.economics.sync-on-startup:true}")`; los tests lo apagan con `@TestPropertySource`.
   - Upsert por `(index_code, effective_date)`: **nunca** pisar un valor `MANUAL` con uno `API`.
   - Tras guardar IPC: recalcular la serie `IPC_ACUMULADO` (el mes **anterior** al primer IPC disponible vale implícitamente `100`; `acum[m] = acum[m-1] × (1 + ipc[m]/100)`, escala 6, `effective_date` = primer día del mes).
   - Cualquier excepción externa: log WARN y continuar (RNF-06). El scheduler nunca propaga excepciones. Agregar `@EnableScheduling` en una config del módulo.
4. **Caché Redis** (`StringRedisTemplate` + JSON): `econ:latest:{indexCode}` TTL 30 min; `econ:history:{indexCode}:{yyyy-MM}` TTL 24 h. Invalidar ambos al sync y al cargar manual. Redis caído → leer de PostgreSQL.
5. **`EconomicsApi` — reglas de cálculo** (escala 4 en montos, 6 en tasas, `HALF_EVEN`):
   - Índice de cotización para una divisa: `USD` → `ctx.referenceRateSource` si empieza con `USD_`, si no `USD_MEP`; `USDT` → `USD_MEP`; `EUR` → `EUR_OFICIAL`; `UVA` → `UVA`; divisa = base → tasa `1`.
   - `baseCurrencyAmount` = `amount` si `currency == base`; si no `amount × rate(currency, date)`.
   - `referenceExchangeRate` = `1` si `referenceCurrency == base`; `USD` → `valueAt(ctx.referenceRateSource)`; `EUR` → `EUR_OFICIAL`; `UVA` → `UVA`.
   - `amountInReference = baseCurrencyAmount / referenceExchangeRate`.
   - `ipcIndexValue = valueAt(IPC_ACUMULADO, date)` (nullable); `uvaValue = valueAt(UVA, date)` (nullable); `snapshotDate = date`.
   - Falta de cotización en la fecha → usar `latestValue`; si no hay ningún dato → tasa `1` y log WARN `"No economic data for {code}; using 1"` (documentar en Javadoc).
   - `convert(amount, from, to, date, rateSource)`: pasa por la moneda base (`from → base → to`).
6. **Manual:** solo `ADULTO`; `value > 0`; upsert con `source = MANUAL`; si es `IPC_MENSUAL` recalcula `IPC_ACUMULADO`. (Los índices son globales del despliegue; documentarlo.)

## Tests
- `EconomicsCalculatorTest` (unit): tx en ARS con referencia USD_MEP; tx en USD con base ARS; referencia UVA; referencia = base; falta de datos → fallback a último y luego a 1; precisión/redondeo.
- `IpcChainTest`: 3 meses de IPC (2.5%, 3.0%, 2.0%) → acumulado `102.500000`, `105.575000`, `107.686500` (escala 6, HALF_EVEN).
- Clientes con `MockRestServiceServer` + fixtures: mapeo correcto; error 500 → no rompe el sync.
- `EconomicsControllerIT`: latest/history/manual; HIJO en manual → 403; history > 2 años → 400; MANUAL no es pisado por sync.

## Fuera de alcance
Crear snapshots en transacciones (T16), lentes en reportes (T19).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] El sistema arranca y opera sin internet (sync falla con WARN, endpoints responden con lo que haya).
- [ ] Ningún uso de `double`/`float` en cálculos.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(economics): add economic index model and repository`
2. `feat(economics): add dolarapi and argentinadatos clients with scheduled sync`
3. `feat(economics): add snapshot and currency conversion engine`
4. `feat(economics): add indices endpoints with redis cache and manual override`
