# T39 — feat(receipts): escaneo de tickets con `ReceiptParserPort`, almacenamiento y OCR asíncrono

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 6 | 6.1 | `feat/backend-receipts-ocr` | Fase 5 completa | T40, T41 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **235–246** (`receipt_attachments`).
- APIs: `CurrentUserProvider` (T06), `CategoryQueryApi` (T13).

## Objetivo
Subir una foto o PDF de ticket, guardarla como comprobante y extraer en segundo plano un borrador (comercio, fecha, total, moneda, categoría sugerida) para prellenar la carga rápida. Arquitectura hexagonal: el motor de OCR es un adaptador intercambiable.

## Archivos permitidos
```
backend/src/main/java/com/familion/receipts/**
backend/src/test/java/com/familion/receipts/**
backend/src/test/resources/receipts/**
backend/Dockerfile                    (solo instalar tesseract-ocr + idioma spa en la imagen runtime)
```
> **No editar `pom.xml`** (lo edita T40 en paralelo). Usar solo dependencias existentes: el adaptador de Tesseract invoca el binario `tesseract` con `ProcessBuilder`; JSONB con `@JdbcTypeCode(SqlTypes.JSON)` de Hibernate.

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/receipts/scan` | auth | `multipart/form-data` campo `file` (jpeg/png/webp/pdf, ≤ 5 MB) | `202 Receipt` |
| GET | `/api/v1/receipts/{id}` | dueño / ADULTO | — | `Receipt` |
| GET | `/api/v1/receipts/{id}/file` | dueño / ADULTO | — | bytes con `Content-Type` original |

```ts
type Receipt = { id; status: 'UPLOADED'|'PROCESSING'|'PARSED'|'FAILED'; mimeType; fileSizeBytes: number; createdAt;
  draft: { merchant: string|null; date: string|null; totalAmount: Decimal|null; currencyCode: string|null;
           suggestedCategoryId: string|null; suggestedCategoryKey: string|null; confidence: Decimal /*0..1*/; rawText: string|null } | null;
  fileUrl: string /* "/api/v1/receipts/{id}/file" */ };
```

**Puerto (dominio):**
```java
package com.familion.receipts.application.port;
public record ReceiptDraft(String merchant, LocalDate date, BigDecimal totalAmount, String currencyCode,
                           String suggestedCategoryKey, BigDecimal confidence, String rawText) {}
public interface ReceiptParserPort { ReceiptDraft parse(byte[] content, String mimeType); }
```

## Especificación
1. **Validación:** tipo por *magic bytes* (no por extensión/Content-Type del cliente); tamaño > 5 MB → `413`; tipo no soportado → `415`.
2. **Almacenamiento:** `${RECEIPTS_STORAGE_DIR:/data/receipts}/{familyId}/{uuid}.{ext}`; `storage_key` relativo; nunca usar el nombre de archivo del cliente.
3. **Proceso asíncrono** (`@Async` con executor propio de 2 hilos): `UPLOADED → PROCESSING → PARSED|FAILED`; guardar `ocr_raw_payload` (JSON del draft + texto). Timeout de OCR 30 s.
4. **Adaptadores:**
   - `TesseractCliReceiptParser` (activo si `tesseract --version` responde al arrancar): imagen → `tesseract stdin stdout -l spa+eng`; PDF → no soportado por este adaptador (`FAILED` con motivo) salvo que exista `pdftoppm` (opcional).
   - `HeuristicTicketTextParser` (reglas puras sobre el texto, testeable sin binario): total = línea con `TOTAL` (último match, formatos `1.234,56` y `1234.56`); fecha `dd/MM/yyyy` o `dd-MM-yy`; comercio = primera línea con letras no vacía y que no sea "TICKET/FACTURA"; moneda `ARS` default, `USD` si aparece `U$S|USD`; categoría sugerida por palabras clave (`YPF|SHELL|AXION|PUMA` → `VEHICLES_FUEL`; `COTO|DIA|CARREFOUR|JUMBO|DISCO|CHANGOMAS` → `SUPERMARKET`; `FARMACIA` → `HEALTH`; si no `OTHER_EXPENSE`); `confidence` = fracción de campos detectados.
   - `NoopReceiptParser` (fallback si no hay Tesseract): devuelve draft vacío con `confidence 0` → `PARSED` (el usuario completa a mano).
   - Bean seleccionado por disponibilidad; preparado para agregar un adaptador LLM Vision en el futuro (comentario en el puerto).
5. `suggestedCategoryId` = `CategoryQueryApi.requireSystemCategory(key).id()`.
6. **Dockerfile:** en la etapa runtime `apk add --no-cache tesseract-ocr tesseract-ocr-data-spa`.

## Tests
- `HeuristicTicketTextParserTest` con 4 textos de ticket de ejemplo en `src/test/resources/receipts/*.txt` (súper, estación de servicio, farmacia, ticket ilegible).
- IT: upload png pequeño → `202` y archivo en disco (directorio temporal vía `@DynamicPropertySource`); poll hasta `PARSED` con `NoopReceiptParser`/parser mockeado; magic bytes inválidos → 415; > 5 MB → 413; otro usuario HIJO → 404; descarga devuelve los mismos bytes.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] `docker build -t familion-backend ./backend` OK con Tesseract instalado.

## Verificación
```bash
cd backend && ./mvnw -B verify
cd .. && docker build -t familion-backend ./backend
```

## Commits sugeridos
1. `feat(receipts): add receipt upload with magic byte validation and storage`
2. `feat(receipts): add heuristic ticket text parser`
3. `feat(receipts): add async ocr pipeline with tesseract adapter`
