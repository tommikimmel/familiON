# T15 — feat(frontend): cuentas y categorías + servicios compartidos de finanzas

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.1 | `feat/frontend-accounts-categories` | Fase 1 completa | T13, T14 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/state/session.store.ts`, `core/state/view-context.store.ts`, `shared/ui/*`, `shared/pipes/money.pipe.ts`.

## Objetivo
1) Crear la **capa compartida de finanzas** en `core/finance/` (cuentas, categorías, eventos) que usarán carga rápida, inicio, movimientos, planificación y vehículos. 2) Pantalla `/accounts` para administrar cuentas y categorías.

## Archivos permitidos
```
frontend/src/app/core/finance/**
frontend/src/app/features/accounts/**
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `GET /bff/api/accounts?view=PERSONAL\|FAMILY` | — | `Account[]` |
| `POST /bff/api/accounts` | `AccountCreate` | `Account` |
| `PATCH /bff/api/accounts/{id}` | parcial | `Account` |
| `DELETE /bff/api/accounts/{id}` | — | `204` |
| `GET /bff/api/categories` | — | `Category[]` |
| `POST /bff/api/categories` (ADULTO) | `{ name, kind, parentCategoryId?, icon?, colorHex? }` | `Category` |
| `PATCH /bff/api/categories/{id}` (ADULTO) | `{ name?, icon?, colorHex?, isActive? }` | `Category` |

```ts
type Decimal = string;
type AccountType = 'CASH'|'BANK'|'WALLET'|'DEBIT_CARD'|'CREDIT_CARD'|'SAVINGS';
type Account = { id: string; name: string; accountType: AccountType; currencyCode: string; initialBalance: Decimal;
  currentBalance: Decimal; ownerUserId: string|null; creditLimit: Decimal|null; closingDay: number|null; dueDay: number|null; isActive: boolean };
type AccountCreate = { name: string; accountType: AccountType; currencyCode: string; initialBalance?: Decimal;
  ownerUserId?: string|null; creditLimit?: Decimal; closingDay?: number; dueDay?: number };
type Category = { id: string; parentCategoryId: string|null; name: string; kind: 'EXPENSE'|'INCOME';
  icon: string|null; colorHex: string|null; isSystem: boolean; systemKey: string|null };
```

## API compartida que crea esta tarea (otras tareas la importan — nombres exactos)

```ts
// core/finance/finance.models.ts   → exporta Decimal, AccountType, Account, AccountCreate, Category (arriba)
// core/finance/finance-events.ts
export type FinanceEvent = { type: 'transaction-created'|'transaction-updated'|'transaction-deleted'|'plan-changed'|'vehicle-changed'; at: number };
@Injectable({ providedIn: 'root' }) export class FinanceEvents {
  readonly last: Signal<FinanceEvent|null>;      // las pantallas hacen effect() sobre esto para recargar
  emit(type: FinanceEvent['type']): void;
}
// core/finance/accounts.store.ts
@Injectable({ providedIn: 'root' }) export class AccountsStore {
  readonly accounts: Signal<Account[]>; readonly loading: Signal<boolean>;
  load(view?: 'PERSONAL'|'FAMILY'): Promise<void>;
  create(a: AccountCreate): Promise<Account>; update(id: string, patch: Partial<AccountCreate>): Promise<Account>; remove(id: string): Promise<void>;
  usableFor(currency?: string): Signal<Account[]>;           // activas, propias o compartidas
  creditCards: Signal<Account[]>;
  defaultAccountFor(currency: string): Account | null;       // override del usuario en localStorage 'fo.defaultAccount.<currency>' o la primera no-tarjeta de esa divisa
  setDefaultAccount(currency: string, accountId: string): void;
}
// core/finance/categories.store.ts
@Injectable({ providedIn: 'root' }) export class CategoriesStore {
  readonly categories: Signal<Category[]>;
  load(force?: boolean): Promise<void>;                     // cachea en memoria
  byKind(kind: 'EXPENSE'|'INCOME'): Signal<Category[]>;
  tree(kind): Signal<(Category & { children: Category[] })[]>;
  frequent(kind, limit = 8): Signal<Category[]>;            // ranking por uso en localStorage 'fo.categoryUsage' (fallback: orden del sistema)
  recordUsage(categoryId: string): void;
  bySystemKey(key: string): Category | undefined;
  create(...); update(...);
}
// core/finance/account-labels.ts
export const ACCOUNT_TYPE_LABEL: Record<AccountType, string> =
  { CASH:'Efectivo', BANK:'Cuenta bancaria', WALLET:'Billetera virtual', DEBIT_CARD:'Tarjeta de débito', CREDIT_CARD:'Tarjeta de crédito', SAVINGS:'Ahorro' };
```

## Especificación de la pantalla `/accounts`

1. Tabs `fo-segmented-toggle` «Cuentas | Categorías».
2. **Cuentas:** agrupadas por tipo; cada tarjeta muestra ícono por tipo, nombre, divisa, saldo (`money`, `tabular-nums`, rojo si negativo), «Compartida» si `ownerUserId === null`, y para tarjetas «Cierra el día X · Vence el día Y» + límite. Total por divisa arriba. Respeta `ViewContextStore.view` (recargar al cambiar).
3. Botón «Nueva cuenta» → `fo-bottom-sheet` con formulario: nombre, tipo (chips), divisa (de `SessionStore.me().family.enabledCurrencies`), saldo inicial, titular (solo ADULTO: «Yo», «Compartida» o miembro — miembros desde `GET /bff/api/family/members`), y si es tarjeta: límite, día de cierre, día de vencimiento (1–31, requeridos). Editar reutiliza el sheet. Eliminar con confirmación («La cuenta se archivará; los movimientos históricos se conservan.»). Acción «Usar como cuenta habitual» → `setDefaultAccount`.
4. **Categorías:** árbol por tipo (Gastos / Ingresos), con ícono lucide y color. ADULTO: crear (con padre opcional), renombrar, cambiar color/ícono (selector simple de 20 íconos lucide), desactivar las personalizadas; las de sistema muestran candado.
5. Emitir `FinanceEvents.emit('plan-changed')` no aplica acá; tras crear/editar cuentas recargar `AccountsStore`.

## Tests
- `accounts.store.spec.ts`: load, create, `defaultAccountFor` (override y fallback), `creditCards`.
- `categories.store.spec.ts`: `tree`, `frequent` con contadores en localStorage, `bySystemKey`.
- `finance-events.spec.ts`: `emit` actualiza `last`.
- Formulario de cuenta: campos de tarjeta obligatorios solo para `CREDIT_CARD`.

## Fuera de alcance
Movimientos, carga rápida (T17).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] APIs compartidas con los nombres exactos de esta tarjeta.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(finance-ui): add shared accounts and categories stores with finance events`
2. `feat(accounts): add accounts management screen`
3. `feat(accounts): add categories tree management`
