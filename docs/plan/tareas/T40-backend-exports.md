# T40 — feat(analytics): exportación de movimientos y resúmenes a CSV / XLSX

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 6 | 6.1 | `feat/backend-exports` | Fase 5 completa | T39, T41 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **248–293** (`transactions`, `economic_snapshots`).
- APIs: `CurrentUserProvider`, `VisibilityPolicy` (T06), `FamilyQueryApi` (T07). Como referencia de vistas y lentes: `com.familion.analytics` (T19) — se pueden reutilizar sus clases públicas de `application` del mismo módulo.

## Objetivo
Descargar los movimientos y un resumen mensual para respaldo o análisis en planilla, respetando vista y lente.

## Archivos permitidos
```
backend/pom.xml                                  (solo agregar org.apache.poi:poi-ooxml)
backend/src/main/java/com/familion/analytics/exports/**
backend/src/test/java/com/familion/analytics/exports/**
```

## Contrato

| Método | Ruta | Acceso | Respuesta |
|---|---|---|---|
| GET | `/api/v1/exports/transactions?format=CSV|XLSX&from=&to=&view=&lens=` | auth | archivo |
| GET | `/api/v1/exports/monthly-summary?format=CSV|XLSX&year=&view=&lens=` | auth | archivo |

Headers: `Content-Type` (`text/csv; charset=UTF-8` o `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`), `Content-Disposition: attachment; filename="familion-movimientos-2026-01-01_2026-11-30.csv"`.

## Especificación
1. **Movimientos:** columnas `Fecha, Tipo, Descripción, Categoría, Cuenta, Pagado por, Atribuido a, Moneda, Monto, Monto ({unidad de la lente}), Cuota, Estado`. Rango máx 3 años (`400`). Misma visibilidad que `GET /transactions` (`PERSONAL` = atribuidos a mí; `FAMILY` según `resolveForListing`). Tipos en español («Gasto», «Ingreso», «Transferencia», …).
2. **Resumen mensual:** 12 filas (ene–dic) con `Mes, Ingresos, Gastos, Balance, Tasa de ahorro %` en la lente pedida + fila de totales; visibilidad de `resolveForTotals`.
3. **CSV:** UTF-8 **con BOM**, separador `;` y decimales con coma (Excel en español), fechas `dd/MM/yyyy`; escapar comillas y prevenir *CSV injection* (prefijar `'` a celdas que empiezan con `= + - @`).
4. **XLSX:** `SXSSFWorkbook` (streaming), encabezado en negrita y fijo, formatos numéricos `#,##0.00` y de fecha, anchos razonables; hoja «Movimientos» o «Resumen».
5. Streaming (`StreamingResponseBody`) leyendo en páginas de 1000 filas con `JdbcTemplate` (no cargar todo en memoria).

## Tests
- CSV: BOM, separador, decimal con coma, escape de `;` y comillas, protección `=cmd`.
- XLSX: abrir con POI y verificar encabezados, tipos numéricos y cantidad de filas.
- Visibilidad: HIJO `OWN_ONLY` con FAMILY → 403; rango > 3 años → 400.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Archivo abre correctamente en Excel/LibreOffice (verificación manual opcional; indicar en la PR).

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(exports): add csv export with excel-friendly formatting`
2. `feat(exports): add streaming xlsx export and monthly summary`
