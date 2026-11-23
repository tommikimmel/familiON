# T17 — feat(frontend): carga rápida `+` en 3 toques (gasto/ingreso, atribución y cuotas)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.2 | `feat/frontend-quick-add` | Ola 2.1 (T15) | T16, T18 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/layout/shell.component.*` y `core/layout/quick-add-launcher.service.ts` (T08), `core/finance/*` (T15), `core/state/*`, `shared/ui/fo-bottom-sheet`, `shared/ui/fo-segmented-toggle`.

## Objetivo
El flujo más usado de la app: registrar un gasto o ingreso en menos de 5 segundos. Monto → categoría o nombre → Guardar. Opciones avanzadas colapsadas: cuenta, fecha, «Para: [Integrante]» y cuotas con regla anti-ambigüedad.

## Archivos permitidos
```
frontend/src/app/features/quick-add/**
frontend/src/app/core/layout/shell.component.ts
frontend/src/app/core/layout/shell.component.html     (si existe; solo montar el host)
frontend/src/app/core/finance/transactions.api.ts      (nuevo)
frontend/src/app/core/finance/transactions.models.ts   (nuevo)
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `POST /bff/api/transactions` | `{ type: 'EXPENSE'|'INCOME', amount, currencyCode, transactionDate, accountId, categoryId?, description?, attributedToUserId? }` | `201 TransactionItem` |
| `POST /bff/api/installment-plans` | `InstallmentPlanCreate` (abajo) | `201 InstallmentPlan` (se ignora el body) |
| `GET /bff/api/family/members` | — | `[{ id, fullName, role, accentColor, isActive }]` |

```ts
type InstallmentPlanCreate = { description: string; categoryId: string; creditCardAccountId: string; currencyCode: string;
  totalInstallments: number; amountMode: 'TOTAL'|'PER_INSTALLMENT'; amount: string; hasInterest: boolean;
  cashPrice?: string; purchaseDate: string; attributedToUserId?: string };
```
> El endpoint de cuotas lo implementa T25 (fase 3). Hasta entonces responderá 404: mostrar toast de error y no romper.

`core/finance/transactions.api.ts` (nuevo, lo amplía T23):
```ts
@Injectable({ providedIn: 'root' }) export class TransactionsApi {
  create(body: TransactionCreate): Observable<TransactionItem>;
  createInstallmentPlan(body: InstallmentPlanCreate): Observable<unknown>;
}
```
Definir `TransactionCreate`, `TransactionItem`, `InstallmentPlanCreate` en `core/finance/transactions.models.ts` (nuevo, permitido): `TransactionItem` = `{ id; type; scope; status; amount; currencyCode; transactionDate; description; notes; category: {id;name;icon;colorHex}|null; account: {id;name;accountType}; paidBy: {id;fullName}; attributedTo: {id;fullName}; installment: {planId;number;total}|null; lens; lensAmount; lensUnit; lensApproximated }`.

## Especificación

1. **Montaje:** `features/quick-add/quick-add-host.component.ts` (`fo-quick-add-host`) observa `QuickAddLauncher.isOpen` y renderiza `fo-bottom-sheet` (mobile: sheet desde abajo; desktop: modal centrado). Montarlo en el shell donde T08 dejó el comentario `<!-- T17 mounts fo-quick-add-host here -->`.
2. **Cabecera del sheet:** `fo-segmented-toggle` **«Gasto | Ingreso»** + ícono de cámara discreto arriba a la derecha (deshabilitado con `title="Próximamente"`; lo activa T41).
3. **Monto grande** (40px, `tabular-nums`) con chip de divisa (divisas habilitadas de `SessionStore.me().family.enabledCurrencies`, default `baseCurrency`). Mobile: teclado propio en grilla 3×4 (`1-9`, `,`, `0`, `000`) + borrar; micro-bump (scale 1.04, 140ms) al teclear. Desktop: input directo enfocado al abrir (acepta teclado físico; `,` o `.` como decimal). Guardar el monto como string decimal con punto.
4. **Categoría o Nombre:** 8 chips de `CategoriesStore.frequent(kind)` + chip «Más» que abre buscador con todas (`byKind`). Alternativamente un input «¿En qué?» para nombre libre (va a `description`). Basta con uno de los dos.
5. **Botón «Guardar»** único (deshabilitado hasta tener monto > 0 y categoría o nombre). Asume: fecha hoy, cuenta `AccountsStore.defaultAccountFor(currency)`, 1 pago, atribución propia.
6. **«Más opciones»** (colapsado por defecto):
   - **Cuenta:** select de `AccountsStore.usableFor(currency)`.
   - **Fecha:** date input (default hoy).
   - **«Para: [Integrante]»** (solo si `SessionStore.isAdult()`): chips con miembros activos (avatar de color + nombre).
   - **Cuotas** (solo Gasto): stepper 1–24. Si `> 1`:
     - Toggle obligatorio **«Es el total | Es cada cuota»** (sin default seleccionado: hay que elegir para poder guardar).
     - Toggle **«Sin interés | Con interés»** (default sin interés); con interés → campo opcional «Precio contado».
     - La cuenta se restringe a tarjetas de crédito (`AccountsStore.creditCards`); si no hay ninguna, mensaje y link a `/accounts`.
     - Resumen en vivo: «6 cuotas de $36.000/mes · Total $216.000 (Dic 2026 → May 2027)». Cálculo: TOTAL → cuota = monto/N; CADA CUOTA → total = monto×N. Primer mes estimado: si la tarjeta tiene `closingDay` y el día de compra ≤ `closingDay` → mes siguiente; si no → el subsiguiente; sin `closingDay` → mes siguiente. Meses en español abreviado (`Intl.DateTimeFormat('es-AR', {month:'short', year:'numeric'})`).
7. **Envío:** cuotas = 1 → `TransactionsApi.create`; cuotas > 1 → `createInstallmentPlan` (`description` = nombre o nombre de la categoría; `categoryId` obligatorio en cuotas → si solo hay nombre, usar la categoría de sistema `OTHER_EXPENSE` vía `CategoriesStore.bySystemKey`). Éxito: toast «Gasto guardado» / «Ingreso guardado», `CategoriesStore.recordUsage`, `FinanceEvents.emit('transaction-created')`, cerrar y resetear. Error: toast con `detail` del problem+json; el sheet queda abierto con los datos.
8. **Teclado:** `Enter` guarda, `Esc` cierra. Animaciones: el `+` del shell rota 45° (ya lo hace T08 vía `isOpen`), sheet 360ms, backdrop blur.

## Tests
- Cálculo de cuotas (TOTAL vs CADA CUOTA, redondeo a 2 decimales para el resumen) y meses estimados con/sin `closingDay` — función pura `installmentPreview()` en `features/quick-add/installment-preview.ts`.
- Teclado numérico: `000`, coma única, borrar.
- Guardar con 1 cuota llama `POST /bff/api/transactions` con el body esperado; con 6 cuotas llama `installment-plans` con `amountMode` elegido; sin elegir modo → botón deshabilitado.
- «Para:» oculto para HIJO.

## Fuera de alcance
OCR (T41), edición de movimientos (T23).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Flujo feliz en 3 toques en mobile: monto → categoría → Guardar.
- [ ] Accesible: foco inicial en monto, trap de foco, `aria-live` para el resumen de cuotas.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(quick-add): add installment preview calculator`
2. `feat(quick-add): add quick add sheet with amount keypad and category chips`
3. `feat(quick-add): add advanced options with attribution and installments`
4. `feat(shell): mount quick add host in app shell`
