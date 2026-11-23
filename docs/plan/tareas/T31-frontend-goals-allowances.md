# T31 — feat(frontend): pantallas de metas de ahorro y mensualidades

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.3 | `feat/frontend-goals-allowances` | Ola 3.2 (T28) | T32 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/finance/*`, `core/state/*`, `shared/ui/*`, `shared/pipes/money.pipe.ts`.

## Objetivo
`/plan/goals`: metas en unidad estable con progreso real y aporte rápido. `/plan/allowances`: el adulto configura y acredita mensualidades; el hijo ve su mensualidad, gastado y saldo.

## Archivos permitidos
```
frontend/src/app/features/plan/goals/**
frontend/src/app/features/plan/allowances/**
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `GET /bff/api/savings-goals?view=&status=` | — | `SavingsGoal[]` |
| `POST /bff/api/savings-goals` | `{ name, scope, targetAmount, targetUnit, targetDate? }` | `SavingsGoal` |
| `PATCH /bff/api/savings-goals/{id}` | `{ name?, targetAmount?, targetDate?, status? }` | `SavingsGoal` |
| `GET /bff/api/savings-goals/{id}/contributions` | — | `Contribution[]` |
| `POST /bff/api/savings-goals/{id}/contributions` | `{ amount, currencyCode, contributionDate?, notes? }` | `Contribution` |
| `DELETE /bff/api/savings-goals/{id}/contributions/{cid}` | — | `204` |
| `GET /bff/api/family/allowances` | — | `Allowance[]` |
| `POST /bff/api/family/allowances` (ADULTO) | `AllowanceCreate` | `Allowance` |
| `PATCH /bff/api/family/allowances/{id}` (ADULTO) | parcial | `Allowance` |
| `DELETE /bff/api/family/allowances/{id}` (ADULTO) | — | `204` |
| `POST /bff/api/family/allowances/{id}/disburse` (ADULTO) | `{ date?, amount? }` | `{ allowance, outTransactionId, inTransactionId }` |
| `GET /bff/api/family/members` | — | `[{ id, fullName, role, accentColor }]` |
| `GET /bff/api/accounts?view=FAMILY` | — | `Account[]` (para elegir cuenta del hijo) |

```ts
type SavingsGoal = { id; name; scope: 'PERSONAL'|'FAMILY'; owner: { id; fullName } | null; targetAmount: Decimal;
  targetUnit: 'ARS'|'USD'|'EUR'|'UVA'|'ARS_IPC'; targetDate: string|null; status: 'ACTIVE'|'COMPLETED'|'PAUSED'|'CANCELLED';
  accumulatedInUnit: Decimal; progressPct: Decimal; remainingInUnit: Decimal; monthsLeft: number|null;
  suggestedMonthlyInUnit: Decimal|null; contributionsCount: number; lastContributionDate: string|null; accumulatedInBaseToday: Decimal|null };
type Contribution = { id; user: { id; fullName }; amount: Decimal; currencyCode; amountInGoalUnit: Decimal; contributionDate; notes; transactionId };
type AllowanceCreate = { childUserId; sourceAccountId?; targetAccountId; amount: Decimal; currencyCode;
  frequency: 'WEEKLY'|'MONTHLY'; dayOfPeriod: number; adjustmentRule: 'MANUAL'|'IPC'|'REFERENCE_CURRENCY' };
type Allowance = { id; child: { id; fullName; accentColor }; assignedBy: { id; fullName }; sourceAccount: { id; name } | null;
  targetAccount: { id; name }; amount: Decimal; currencyCode; frequency; dayOfPeriod; adjustmentRule; isActive: boolean;
  currentAmount: Decimal; nextDisbursementDate: string; lastDisbursementDate: string|null; childBalance: Decimal };
```

## Especificación
**Metas:** tarjetas con anillo de progreso (SVG, color de acento), nombre, «Personal/Familiar», `accumulated / target` en su unidad (`ARS_IPC` se muestra «$ de hoy»), equivalente en base hoy, «Faltan N meses · Ahorro sugerido: X por mes». Crear meta (sheet; unidad con explicación: «USD: protege contra la devaluación», «UVA: sigue la inflación», «Pesos ajustados: se actualiza por IPC»). «Aportar» rápido (monto + divisa + fecha). Historial de aportes con borrar. Metas completadas con estado celebratorio sutil (respetar reduced-motion).

**Mensualidades:**
- ADULTO: lista por hijo (avatar con su color): monto vigente, frecuencia y próximo día, saldo actual del hijo, botón «Acreditar ahora» (confirma monto editable) → toast y `FinanceEvents.emit('transaction-created')`. Crear/editar (hijo, cuenta del hijo, cuenta origen opcional, monto, divisa, frecuencia y día, regla de ajuste).
- HIJO: tarjeta «Tu mensualidad»: monto, próxima acreditación, saldo disponible (`childBalance`) y link a sus movimientos.

## Tests
- Stores de metas y mensualidades.
- Formateo de unidad (`ARS_IPC`, `UVA`) y textos de sugerido.
- Vista HIJO vs ADULTO en mensualidades.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(plan-ui): add savings goals with progress and contributions`
2. `feat(plan-ui): add allowances management for adults and children`
