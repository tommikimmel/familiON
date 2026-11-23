# T30 — feat(frontend): pantallas de cuotas y gastos fijos

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 3 | 3.2 | `feat/frontend-installments-recurring` | Ola 3.1 (T25, T26) | T28, T29 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/finance/*` (stores, `finance-events.ts`, `transactions.models.ts`), `core/state/*`, `shared/ui/*`, `shared/pipes/money.pipe.ts`.

## Objetivo
`/plan/installments`: ver compras en cuotas, cronograma, recargo real, licuación y precancelar. `/plan/recurring`: gastos fijos del mes con estado y confirmación de pago en 2 toques.

## Archivos permitidos
```
frontend/src/app/features/plan/installments/**
frontend/src/app/features/plan/recurring/**
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `GET /bff/api/installment-plans?status=&view=` | — | `InstallmentPlan[]` |
| `GET /bff/api/installment-plans/{id}` | — | `InstallmentPlanDetail` |
| `POST /bff/api/installment-plans/{id}/cancel-remaining` | `{ mode: 'CANCEL'|'PREPAY', prepayDate? }` | `InstallmentPlan` |
| `GET /bff/api/installment-plans/commitment?months=12&view=` | — | `{ month, amount, currencyCode }[]` |
| `GET /bff/api/recurring-expenses?month=yyyy-MM` | — | `RecurringExpense[]` |
| `POST /bff/api/recurring-expenses` (ADULTO) | `RecurringExpenseCreate` | `RecurringExpense` |
| `PATCH /bff/api/recurring-expenses/{id}` (ADULTO) | parcial | `RecurringExpense` |
| `DELETE /bff/api/recurring-expenses/{id}` (ADULTO) | — | `204` |
| `POST /bff/api/recurring-expenses/{id}/confirm-payment` | `{ month, amount, accountId?, paymentDate? }` | `RecurringExpense` |

```ts
type InstallmentPlan = { id; description; category: { id; name; icon; colorHex }; creditCardAccount: { id; name };
  currencyCode; totalInstallments; paidInstallments; hasInterest; cashPrice: Decimal|null; financedTotal: Decimal;
  installmentAmount: Decimal; surchargeAmount: Decimal|null; surchargePct: Decimal|null; purchaseDate; firstBillingDate;
  lastBillingDate; remainingAmount: Decimal; realRemainingAmount: Decimal|null;
  nextInstallment: { number; date; amount: Decimal } | null; status: 'ACTIVE'|'COMPLETED'|'CANCELLED'; attributedTo: { id; fullName } };
type InstallmentPlanDetail = InstallmentPlan & { installments: { number; date; amount: Decimal; status: 'POSTED'|'PROJECTED'|'CANCELLED'; transactionId }[] };
type RecurringExpenseCreate = { name; categoryId; accountId?; scope?: 'PERSONAL'|'FAMILY'; baseAmount: Decimal; currencyCode;
  dueDay: number; adjustmentRule: 'MANUAL'|'IPC'|'REFERENCE_CURRENCY'|'LAST_PAID'; startDate: string; endDate?: string };
type RecurringExpense = { id; name; category: { id; name; icon; colorHex }; account: { id; name } | null; scope; baseAmount: Decimal;
  currencyCode; dueDay; adjustmentRule; startDate; endDate: string|null; isActive: boolean;
  month: { month; dueDate; estimatedAmount: Decimal; status: 'PENDING'|'PAID'|'OVERDUE'|'NOT_APPLICABLE';
           paidTransactionId: string|null; paidAmount: Decimal|null; paidDate: string|null } };
```

## Especificación
**Cuotas (`/plan/installments`):**
1. Cabecera: total pendiente y gráfico de barras de compromiso por mes (12 meses) — «Lo que ya está comprometido».
2. Tabs «Activas | Terminadas | Canceladas». Tarjeta por plan: descripción, tarjeta, «Cuota 3 de 6», barra de progreso, próxima cuota (fecha + monto), restante nominal y «≈ $X en plata de hoy» (`realRemainingAmount`, explicación: «Las cuotas fijas se licúan con la inflación»), chip «Sin interés» o «Recargo 18%».
3. Detalle (sheet): cronograma con estado por cuota; acciones «Cancelar cuotas restantes» y «Precancelar» (fecha) con confirmación → `FinanceEvents.emit('plan-changed')`.

**Gastos fijos (`/plan/recurring`):**
1. Selector de mes (‹ ›). Resumen: total estimado del mes, pagado y pendiente.
2. Lista ordenada por vencimiento con estado (Pendiente / Vencido en rojo / Pagado con check / No aplica oculto). Botón «Pagar» → sheet con monto precargado (`estimatedAmount` editable), cuenta (default la sugerida o `AccountsStore.defaultAccountFor`), fecha → confirm → toast y `FinanceEvents.emit('transaction-created')`.
3. ADULTO: crear/editar/archivar con formulario (nombre, categoría, cuenta sugerida, alcance Familia/Personal, monto base, divisa, día de vencimiento, regla con explicación: Manual «Monto fijo», IPC «Se ajusta por inflación», Referencia «Está en dólares/UVA», Último pago «Usa lo último que pagaste + inflación», fecha de inicio/fin).

## Tests
- Stores de ambas pantallas (carga, acciones, eventos).
- Componente de tarjeta de plan: textos de recargo/licuación.
- Formulario de gasto fijo: validaciones (día 1–31, monto > 0).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(plan-ui): add installment plans screen with commitment chart`
2. `feat(plan-ui): add recurring expenses screen with payment confirmation`
