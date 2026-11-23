# T25 — feat(finance): compras en cuotas con/sin interés, cronograma por tarjeta y precancelación

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.1 | `feat/backend-installments` | Fase 2 completa | T26, T27 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **174–193** (`installment_plans`) y **248–280** (`transactions`: `installment_plan_id`, `installment_number`, `status`).
- APIs: `TransactionCommandApi` (T16), `AccountQueryApi`, `CategoryQueryApi` (T13), `FamilyQueryApi` (T07), `EconomicsApi` (T09), `CurrentUserProvider`, `VisibilityPolicy` (T06).

## Objetivo
Registrar compras financiadas, proyectar las N cuotas en el flujo futuro según el cierre/vencimiento de la tarjeta, mostrar recargo real y peso real estimado de la deuda, y permitir cancelar o precancelar.

## Archivos permitidos
```
backend/src/main/java/com/familion/finance/installments/**
backend/src/test/java/com/familion/finance/installments/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/installment-plans` | auth | `InstallmentPlanCreate` | `201 InstallmentPlan` |
| GET | `/api/v1/installment-plans?status=ACTIVE\|COMPLETED\|CANCELLED&view=` | auth | — | `InstallmentPlan[]` |
| GET | `/api/v1/installment-plans/{id}` | auth | — | `InstallmentPlanDetail` |
| POST | `/api/v1/installment-plans/{id}/cancel-remaining` | autor / ADULTO | `{ mode: 'CANCEL'|'PREPAY', prepayDate? }` | `InstallmentPlan` |
| GET | `/api/v1/installment-plans/commitment?months=12&view=` | auth | — | `{ month: 'yyyy-MM', amount: Decimal, currencyCode }[]` |

```ts
type InstallmentPlanCreate = { description: string; categoryId: string; creditCardAccountId: string; currencyCode: string;
  totalInstallments: number /* 2..60 */; amountMode: 'TOTAL'|'PER_INSTALLMENT'; amount: Decimal; hasInterest: boolean;
  cashPrice?: Decimal; purchaseDate: string; attributedToUserId?: string };
type InstallmentPlan = { id; description; category: { id; name; icon; colorHex }; creditCardAccount: { id; name };
  currencyCode; totalInstallments; paidInstallments; hasInterest; cashPrice: Decimal|null; financedTotal: Decimal;
  installmentAmount: Decimal; surchargeAmount: Decimal|null; surchargePct: Decimal|null; purchaseDate; firstBillingDate;
  lastBillingDate; remainingAmount: Decimal; realRemainingAmount: Decimal|null;
  nextInstallment: { number; date; amount: Decimal } | null; status: 'ACTIVE'|'COMPLETED'|'CANCELLED';
  attributedTo: { id; fullName } };
type InstallmentPlanDetail = InstallmentPlan & { installments: { number; date; amount: Decimal;
  status: 'POSTED'|'PROJECTED'|'CANCELLED'; transactionId }[] };
```

## Reglas
1. **Montos:** `TOTAL` → `financedTotal = amount`, `installmentAmount = amount / N` (escala 4, HALF_EVEN); la **última** cuota absorbe la diferencia de redondeo para que Σ = total. `PER_INSTALLMENT` → `installmentAmount = amount`, `financedTotal = amount × N`. `hasInterest=false` con `cashPrice` → ignorar `cashPrice`. Con interés y `cashPrice`: `surchargeAmount = financedTotal − cashPrice`, `surchargePct = surcharge / cashPrice × 100`.
2. **Cronograma** (cuenta debe ser `CREDIT_CARD` → si no `422`):
   - `closingDate` = día `closingDay` del mes de compra (clamp a fin de mes); si `purchaseDate > closingDate` → +1 mes.
   - `firstBillingDate` = día `dueDay` (clamp) del mes **siguiente** a `closingDate`. Sin `closingDay/dueDay` → día 10 del mes siguiente a la compra.
   - Cuota `i` → `firstBillingDate + (i−1) meses` (clamp de día).
   - Crear N transacciones `EXPENSE` vía `TransactionCommandApi.record` con `installmentPlanId`, `installmentNumber`, `scope PERSONAL`, categoría del plan, `attributedTo` (reglas de Regla 5: solo ADULTO puede atribuir a otro) y `status` = `POSTED` si la fecha ≤ hoy, si no `PROJECTED`. Todo en una transacción.
3. **Scheduler diario** (`@Scheduled(cron = "0 15 3 * * *", zone = "America/Argentina/Buenos_Aires")`): cuotas `PROJECTED` con fecha ≤ hoy → `changeStatus(POSTED)`; planes con todas POSTED → `COMPLETED`. Idempotente.
4. **cancel-remaining:** `CANCEL` → cuotas PROJECTED → `CANCELLED`, plan `CANCELLED`. `PREPAY` → cuotas PROJECTED pasan a `POSTED` con fecha `prepayDate` (default hoy) vía `updateAmountAndDate` + `changeStatus`; plan `COMPLETED`. Plan ya cerrado → `409`.
5. **Cálculos de lectura:** `paidInstallments` = POSTED; `remainingAmount` = Σ PROJECTED; `realRemainingAmount` = Σ cuota_k / (1 + π)^k, con π = último `IPC_MENSUAL`/100 (`EconomicsApi.latestValue`) y k = meses desde hoy (null si no hay IPC) — muestra la "licuación". `commitment`: Σ PROJECTED agrupado por mes (próximos `months`, máx 24), por divisa.
6. **Vistas:** listado `PERSONAL` → planes con cuotas atribuidas a mí; `FAMILY` → `resolveForListing`. Aislamiento por familia.

## Tests
- Unit: cronograma con cierre 25 / vence 5: compra 20/03 → primera cuota 05/04; compra 26/03 → 05/05; meses de 30/31 y febrero con clamp; redondeo de 3 cuotas de 100,00 total → 33,3333 / 33,3333 / 33,3334.
- Unit: recargo y `realRemainingAmount`.
- IT: crear plan de 6 cuotas → 6 transacciones con estados correctos; cuenta no tarjeta → 422; HIJO atribuyendo a otro → 403; scheduler postea cuotas vencidas (usar `Clock` fijo); cancel/prepay; commitment agrupado.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Σ cuotas = `financedTotal` exacto.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(installments): add schedule calculator with card closing rules`
2. `feat(installments): add plan creation projecting installment transactions`
3. `feat(installments): add daily posting job, cancellation and commitment view`
