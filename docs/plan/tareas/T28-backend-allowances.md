# T28 — feat(family): mensualidades para hijos sin doble conteo

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.2 | `feat/backend-allowances` | Ola 3.1 | T29, T30 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **152–168** (`allowances`) y **248–280** (`transactions`: `allowance_id`, `transfer_group_id`).
- APIs: `TransactionCommandApi` (T16), `AccountQueryApi`, `CategoryQueryApi` (T13), `FamilyQueryApi` (T07), `EconomicsApi` (T09).

## Objetivo
Un adulto asigna una mensualidad (mensual o semanal) a un hijo; al acreditarla se generan `ALLOWANCE_OUT`/`ALLOWANCE_IN` que **no** cuentan como gasto del hogar (Regla 1: lo que cuenta es lo que el hijo efectivamente gasta).

## Archivos permitidos
```
backend/src/main/java/com/familion/family/allowances/**
backend/src/test/java/com/familion/family/allowances/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/family/allowances` | auth (HIJO solo las propias) | — | `Allowance[]` |
| POST | `/api/v1/family/allowances` | ADULTO | `AllowanceCreate` | `201 Allowance` |
| PATCH | `/api/v1/family/allowances/{id}` | ADULTO | parcial | `Allowance` |
| DELETE | `/api/v1/family/allowances/{id}` | ADULTO | — | `204` (`is_active=false`) |
| POST | `/api/v1/family/allowances/{id}/disburse` | ADULTO | `{ date?, amount? }` | `201 { allowance: Allowance, outTransactionId: string|null, inTransactionId: string }` |

```ts
type AllowanceCreate = { childUserId; sourceAccountId?; targetAccountId; amount: Decimal; currencyCode;
  frequency: 'WEEKLY'|'MONTHLY'; dayOfPeriod: number; adjustmentRule: 'MANUAL'|'IPC'|'REFERENCE_CURRENCY' };
type Allowance = { id; child: { id; fullName; accentColor }; assignedBy: { id; fullName };
  sourceAccount: { id; name } | null; targetAccount: { id; name }; amount: Decimal; currencyCode; frequency; dayOfPeriod;
  adjustmentRule; isActive: boolean; currentAmount: Decimal; nextDisbursementDate: string; lastDisbursementDate: string|null;
  childBalance: Decimal };
```

**Evento para notificaciones (T36):**
```java
package com.familion.family.allowances.application.api;
public record AllowanceDisbursedEvent(UUID familyId, UUID allowanceId, UUID childUserId, BigDecimal amount, String currencyCode) {}
```

## Reglas
1. `childUserId` debe ser miembro `HIJO` de la familia (`422` si no). `targetAccountId` debe ser cuenta del hijo (`owner_user_id = child`) → si no `422`. `sourceAccountId` opcional, de la familia. `dayOfPeriod`: MONTHLY 1–31 (clamp al fin de mes), WEEKLY 1–7 (lunes = 1).
2. **Monto vigente (`currentAmount`):** `MANUAL` → `amount`; `IPC` → `amount × IPC_ACUM(último) / IPC_ACUM(created_at)`; `REFERENCE_CURRENCY` → `amount` está en moneda de referencia → convertir a `currencyCode` con la cotización de hoy.
3. **disburse:** monto = `amount` del request o `currentAmount`; fecha = `date` o hoy. Un `transferGroupId` nuevo. Si hay `sourceAccountId`: `ALLOWANCE_OUT` (cuenta origen, `userId` = adulto, `attributedTo` = adulto). Siempre `ALLOWANCE_IN` (cuenta del hijo, `userId` = adulto, `attributedTo` = hijo, categoría `ALLOWANCE_INCOME`). Ambas con `allowanceId`, `scope PERSONAL`. Publicar `AllowanceDisbursedEvent`. Todo atómico.
4. `nextDisbursementDate`: próxima ocurrencia de `dayOfPeriod` posterior a la última acreditación (o desde hoy). `lastDisbursementDate` = última `ALLOWANCE_IN` no borrada. `childBalance` = `AccountQueryApi.currentBalance(targetAccountId)`.

## Tests
- Crear con hijo/adulto inválido → 422; cuenta destino ajena al hijo → 422; HIJO crea → 403.
- disburse genera par con mismo grupo; el saldo del hijo sube; **un test de integración que llame a `GET /api/v1/analytics/summary?view=FAMILY`** (T19 ya está mergeado) verificando que la mensualidad no suma como gasto y que un gasto posterior del hijo de $30.000 sí suma (Regla 1: $30.000, no $80.000).
- `currentAmount` con IPC; `nextDisbursementDate` semanal y mensual (clamp 31 → 30/28).
- HIJO lista solo las propias.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(allowances): add allowance configuration for children`
2. `feat(allowances): add disbursement with mirrored allowance transactions`
