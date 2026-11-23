# T41 — feat(frontend): escaneo de tickets en la carga rápida y botones de exportación

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 6 | 6.1 | `feat/frontend-scan-exports` | Fase 5 completa | T39, T40 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `features/quick-add/**` (T17), `features/transactions/**` (T23), `features/analytics/**` (T22), `core/finance/*`, `shared/ui/*`.

## Objetivo
Activar el ícono de cámara de la carga rápida para escanear un ticket y prellenar el gasto, y agregar descargas CSV/XLSX en Movimientos y Analítica.

## Archivos permitidos
```
frontend/src/app/features/quick-add/**
frontend/src/app/features/transactions/**     (solo agregar el menú de exportación)
frontend/src/app/features/analytics/**        (solo agregar el menú de exportación)
frontend/src/app/core/finance/receipts.api.ts (nuevo)
frontend/src/app/core/finance/exports.api.ts  (nuevo)
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `POST /bff/api/receipts/scan` | `multipart/form-data` campo `file` (≤ 5 MB; jpeg/png/webp/pdf) | `202 Receipt` · `413` · `415` |
| `GET /bff/api/receipts/{id}` | — | `Receipt` |
| `GET /bff/api/exports/transactions?format=CSV|XLSX&from&to&view&lens` | — | archivo |
| `GET /bff/api/exports/monthly-summary?format=CSV|XLSX&year&view&lens` | — | archivo |

```ts
type Receipt = { id; status: 'UPLOADED'|'PROCESSING'|'PARSED'|'FAILED'; mimeType; fileSizeBytes: number; createdAt;
  draft: { merchant: string|null; date: string|null; totalAmount: string|null; currencyCode: string|null;
           suggestedCategoryId: string|null; suggestedCategoryKey: string|null; confidence: string; rawText: string|null } | null;
  fileUrl: string };
```
`POST /bff/api/transactions` ya acepta `receiptAttachmentId` (T16).

## Especificación
1. **Escaneo:** el ícono de cámara del sheet abre `<input type="file" accept="image/*,application/pdf" capture="environment">`. Antes de subir, si es imagen > 2 MB, redimensionar en el cliente (canvas, lado mayor 2000 px, JPEG 0,85). Subir con `FormData` (el header CSRF lo agrega Angular). Mostrar estado «Leyendo ticket…» con skeleton sobre los campos; poll `GET /receipts/{id}` cada 1,5 s hasta `PARSED|FAILED` o 30 s.
2. **Prellenado:** monto (`totalAmount`), divisa, fecha (en «Más opciones»), nombre (`merchant`), categoría (`suggestedCategoryId`); marcar visualmente los campos autocompletados (subrayado de acento) para que el usuario los revise; guardar envía `receiptAttachmentId`. `confidence < 0,5` → aviso «Revisa los datos: la lectura no fue precisa». `FAILED` → toast «No se pudo leer el ticket; el comprobante quedó adjunto» y igual se adjunta.
3. **Exportación:** botón «Exportar» (menú CSV / Excel) en `/transactions` (usa filtros de fecha y vista actuales) y en `/analytics` (resumen mensual del año del período). Descargar con `HttpClient` `responseType: 'blob'` + `URL.createObjectURL` + `<a download>` usando el nombre de `Content-Disposition`. Estado de carga en el botón.

## Tests
- Redimensionado (mock de canvas) y armado de `FormData`.
- Poll: termina en PARSED y prellena; timeout muestra aviso.
- Descarga: nombre de archivo tomado de `Content-Disposition`.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] El flujo de carga rápida sin cámara sigue funcionando igual (sin regresiones en tests de T17).

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(quick-add): add receipt scanning with draft prefill`
2. `feat(transactions-ui): add csv and xlsx export menu`
3. `feat(analytics-ui): add monthly summary export`
