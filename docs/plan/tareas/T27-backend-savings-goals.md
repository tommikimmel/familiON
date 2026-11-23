# T27 — feat(finance): metas de ahorro en valor real (USD, UVA, ARS ajustado por IPC)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.1 | `feat/backend-savings-goals` | Fase 2 completa | T25, T26 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **299–326** (`savings_goals`, `goal_contributions`).
- APIs: `EconomicsApi` (T09), `FamilyQueryApi` (T07), `CurrentUserProvider`, `VisibilityPolicy` (T06).

## Objetivo
Metas personales y familiares definidas en una unidad estable, con aportes convertidos a esa unidad al momento de aportar, progreso en poder adquisitivo real y ahorro mensual sugerido para llegar a tiempo.

## Archivos permitidos
```
backend/src/main/java/com/familion/finance/goals/**
backend/src/test/java/com/familion/finance/goals/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/savings-goals?view=&status=` | auth | — | `SavingsGoal[]` |
| POST | `/api/v1/savings-goals` | auth (FAMILY solo ADULTO) | `{ name, scope, targetAmount, targetUnit, targetDate? }` | `201 SavingsGoal` |
| PATCH | `/api/v1/savings-goals/{id}` | dueño / ADULTO | `{ name?, targetAmount?, targetDate?, status? }` | `SavingsGoal` |
| GET | `/api/v1/savings-goals/{id}/contributions` | auth | — | `Contribution[]` |
| POST | `/api/v1/savings-goals/{id}/contributions` | auth | `{ amount, currencyCode, contributionDate?, notes?, transactionId? }` | `201 Contribution` |
| DELETE | `/api/v1/savings-goals/{id}/contributions/{cid}` | autor / ADULTO | — | `204` |

```ts
type SavingsGoal = { id; name; scope: 'PERSONAL'|'FAMILY'; owner: { id; fullName } | null; targetAmount: Decimal;
  targetUnit: 'ARS'|'USD'|'EUR'|'UVA'|'ARS_IPC'; targetDate: string|null; status: 'ACTIVE'|'COMPLETED'|'PAUSED'|'CANCELLED';
  accumulatedInUnit: Decimal; progressPct: Decimal; remainingInUnit: Decimal; monthsLeft: number|null;
  suggestedMonthlyInUnit: Decimal|null; contributionsCount: number; lastContributionDate: string|null;
  accumulatedInBaseToday: Decimal|null };
type Contribution = { id; user: { id; fullName }; amount: Decimal; currencyCode; amountInGoalUnit: Decimal;
  contributionDate; notes: string|null; transactionId: string|null };
```

## Reglas
1. **Conversión del aporte a la unidad** (en `contributionDate`): `ARS`/`USD`/`EUR` → `EconomicsApi.convert(amount, currency, unit, date, fuente)`; `UVA` → convertir a base y dividir por `valueAt(UVA, date)`; `ARS_IPC` → guardar el monto en ARS **deflactado a la fecha de creación de la meta**: `amountARS × IPC_ACUM(createdAt) / IPC_ACUM(contributionDate)` (sin IPC → sin ajuste). Resultado > 0 (si no `422`).
2. **Progreso:** `accumulatedInUnit = Σ amount_in_goal_unit`; `progressPct = min(100, acc/target × 100)`; `remainingInUnit = max(0, target − acc)`; `monthsLeft` = meses enteros hasta `targetDate` (mín 1; null sin fecha); `suggestedMonthlyInUnit = remaining / monthsLeft`. Al llegar al 100% → `status COMPLETED` automáticamente. `accumulatedInBaseToday` = acumulado expresado en moneda base con la cotización/índice de hoy (para `ARS_IPC`: `acc × IPC_hoy / IPC_creación`).
3. **Alcance:** `FAMILY` → `owner_user_id NULL`, crear solo ADULTO; `PERSONAL` → dueño = yo. Listado `PERSONAL` → mis metas; `FAMILY` → metas familiares + (ADULTO) personales de todos según `resolveForListing`. HIJO ve metas familiares si su visibilidad ≠ `OWN_ONLY`.
4. `transactionId` opcional: debe existir en la familia (validar con una query simple por id + family_id); no se crea transacción automáticamente.

## Tests
- Unit de conversiones (USD, UVA, ARS_IPC con índices de prueba) y de sugerido mensual.
- IT: crear (HIJO FAMILY → 403), aportar convierte y actualiza progreso, completar automático, borrar aporte recalcula, aislamiento por familia.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(goals): add savings goals with stable unit conversion`
2. `feat(goals): add contributions, progress and suggested monthly saving`
