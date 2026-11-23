# T33 — feat(vehicles): vehículos, combustible, mantenimiento, métricas, alertas e integración contable

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 4 | 4.1 | `feat/backend-vehicles` | Fase 3 completa | T34, T35 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **332–399** (`vehicles`, `fuel_logs`, `maintenance_logs`).
- APIs: `TransactionCommandApi`, `TransactionChangedEvent` (T16), `AccountQueryApi`, `CategoryQueryApi` (T13), `FamilyQueryApi` (T07), `EconomicsApi` (T09), `CurrentUserProvider` (T06).

## Objetivo
Control total de los autos de la familia: ficha y vencimientos, cargas de combustible y services vinculados **atómicamente** a la contabilidad (Regla 3), consumo y costo por km en cualquier lente, y alertas preventivas.

## Archivos permitidos
```
backend/src/main/java/com/familion/vehicles/**
backend/src/test/java/com/familion/vehicles/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/vehicles` | auth | — | `Vehicle[]` (activos) |
| GET | `/api/v1/vehicles/{id}` | auth | — | `Vehicle` |
| POST | `/api/v1/vehicles` | ADULTO | `VehicleCreate` | `201 Vehicle` |
| PATCH | `/api/v1/vehicles/{id}` | ADULTO | parcial | `Vehicle` |
| DELETE | `/api/v1/vehicles/{id}` | ADULTO | — | `204` (baja lógica) |
| GET | `/api/v1/vehicles/{id}/summary?lens=&from=&to=` | auth | — | `VehicleSummary` (default últimos 12 meses) |
| GET | `/api/v1/vehicles/{id}/fuel-logs?page=&size=` | auth | — | `Page<FuelLog>` |
| POST | `/api/v1/vehicles/{id}/fuel-logs` | auth | `FuelLogCreate` | `201 FuelLog` |
| DELETE | `/api/v1/vehicles/{id}/fuel-logs/{logId}` | autor / ADULTO | — | `204` |
| GET | `/api/v1/vehicles/{id}/maintenance-logs?page=&size=` | auth | — | `Page<MaintenanceLog>` |
| POST | `/api/v1/vehicles/{id}/maintenance-logs` | auth | `MaintenanceLogCreate` | `201 MaintenanceLog` |
| DELETE | `/api/v1/vehicles/{id}/maintenance-logs/{logId}` | autor / ADULTO | — | `204` |

```ts
type FuelType = 'NAFTA_SUPER'|'NAFTA_PREMIUM'|'DIESEL'|'DIESEL_PREMIUM'|'GNC'|'ELECTRICO';
type VehicleCreate = { nickname; brand; model; modelYear: number; licensePlate; primaryFuelType: FuelType|'HIBRIDO';
  secondaryFuelType?: FuelType; currentOdometerKm?: number; vtvExpirationDate?; insuranceExpiration?; insuranceCompany?; notes? };
type VehicleAlert = { kind: 'SERVICE_KM'|'SERVICE_DATE'|'VTV'|'INSURANCE'; title: string; dueDate: string|null; dueKm: number|null;
  severity: 'INFO'|'WARNING'|'OVERDUE' };
type Vehicle = VehicleCreate & { id; currentOdometerKm: number; isActive: boolean; alerts: VehicleAlert[] };
type FuelLogCreate = { fuelDate?; odometerKm: number; fuelType: FuelType; liters?: Decimal; pricePerLiter?: Decimal;
  totalAmount?: Decimal; currencyCode; fullTank: boolean; stationName?; notes?; accountId: string; attributedToUserId? };
type FuelLog = { id; fuelDate; odometerKm; fuelType; liters: Decimal; pricePerLiter: Decimal; totalAmount: Decimal; currencyCode;
  fullTank: boolean; stationName; notes; transactionId; user: { id; fullName } };
type MaintenanceType = 'SERVICE'|'OIL_CHANGE'|'FILTERS'|'TIRES'|'BRAKES'|'TIMING_BELT'|'BATTERY'|'SUSPENSION'|'ALIGNMENT'|'VTV'|'REPAIR'|'OTHER';
type MaintenanceLogCreate = { maintenanceType: MaintenanceType; maintenanceDate?; odometerKm: number; description; workshopName?;
  costAmount: Decimal; currencyCode; accountId?: string; nextDueKm?: number; nextDueDate?; notes? };
type MaintenanceLog = MaintenanceLogCreate & { id; transactionId: string|null; user: { id; fullName } };
type VehicleSummary = { vehicleId; from; to; lens; unit; distanceKm: number; totalLiters: Decimal;
  avgKmPerLiter: Decimal|null; avgLitersPer100Km: Decimal|null; costPerKm: Decimal|null; totalCost: Decimal;
  fuelCost: Decimal; maintenanceCost: Decimal; lensApproximated: boolean;
  monthly: { month: string; fuelCost: Decimal; maintenanceCost: Decimal; km: number }[];
  pricePerLiterTrend: { date: string; pricePerLiter: Decimal; fuelType }[] };
```

**API Java para notificaciones (T36):**
```java
package com.familion.vehicles.application.api;
public record VehicleAlertRef(UUID familyId, UUID vehicleId, String vehicleNickname, String kind, String title,
                              LocalDate dueDate, Integer dueKm, String severity) {}
public interface VehicleAlertQueryApi { List<VehicleAlertRef> activeAlerts(); }   // WARNING u OVERDUE de todas las familias
```

## Reglas
1. **Combustible:** se requieren 2 de 3 entre `liters`, `pricePerLiter`, `totalAmount`; calcular el tercero (litros escala 3, precio escala 4, total escala 4); si llegan los 3 y `liters × price` difiere > 1% del total → `422`. `odometerKm` coherente: ≥ odómetro de la carga anterior por fecha y ≤ el de la siguiente (`422`). En **un** `@Transactional`: `TransactionCommandApi.record(EXPENSE, categoría VEHICLES_FUEL, cuenta, monto total, fecha, description "Combustible · {nickname}")` + `fuel_logs` con `transaction_id`. Si `odometerKm > current_odometer_km` → actualizarlo.
2. **Mantenimiento:** si `costAmount > 0` → `accountId` obligatorio y transacción `EXPENSE` con `VEHICLES_MAINTENANCE` (description "{tipo} · {nickname}"); si es 0 → sin transacción. `nextDueKm` > `odometerKm` (`422`). Actualiza odómetro igual que combustible. Tipo `VTV` con `nextDueDate` → actualiza `vtv_expiration_date`.
3. **Sincronización (Regla 3):** `@TransactionalEventListener(phase = BEFORE_COMMIT)` o `@EventListener` dentro de la transacción sobre `TransactionChangedEvent`: si la transacción pertenece a un `fuel_log` → actualizar `total_amount` y `fuel_date` (recalcular `price_per_liter = total/liters`); si `deleted` → borrar el log. Para `maintenance_logs` → actualizar `cost_amount`/`maintenance_date` o poner `transaction_id = NULL` y `cost_amount = 0` si se borró. Borrar un log desde este módulo → `TransactionCommandApi.softDelete`.
4. **Métricas** (rango `from`–`to`):
   - Consumo por el método **tanque lleno a tanque lleno**: para cada par de cargas consecutivas con `full_tank = true`, litros = suma de litros de las cargas posteriores a la primera hasta la segunda inclusive; km = diferencia de odómetros. `avgKmPerLiter = Σkm / Σlitros`; `avgLitersPer100Km = Σlitros / Σkm × 100`. Excluir `GNC` (m³) y `ELECTRICO` del promedio de litros (documentar). Sin pares → `null`.
   - `distanceKm` = odómetro máx − mín en el rango (de cargas y services).
   - Costos desde las transacciones vinculadas (`fuel_logs.transaction_id`, `maintenance_logs.transaction_id`) con su snapshot aplicando la lente (misma regla que el resto: NOMINAL, CONSTANT_BASE con IPC, REFERENCE_CURRENCY; `INCOME_PCT` no aplica → `400`). `costPerKm = totalCost / distanceKm`.
5. **Alertas:** VTV y seguro: `OVERDUE` si vencido, `WARNING` si ≤ 30 días. Por cada `maintenance_type`, tomar el último log con `next_due_km`/`next_due_date`: km → `WARNING` si faltan ≤ 1000 km, `OVERDUE` si se superó; fecha → `WARNING` ≤ 30 días, `OVERDUE` vencida. Títulos en español: «VTV vence el 12/12», «Cambio de aceite en 800 km».
6. Patente única por familia (`409`). Todos los accesos filtran por familia.

## Tests
- Unit: completar el tercer valor de combustible; consumo tanque lleno a tanque lleno con carga parcial intermedia; alertas por km y fecha.
- IT: carga de combustible crea transacción con categoría correcta y actualiza odómetro (todo o nada: forzar error en el log y verificar que no quedó transacción); odómetro incoherente → 422; editar monto de la transacción vía `PATCH /transactions/{id}` (T16) actualiza el `fuel_log`; borrar la transacción borra el log; service sin costo no crea transacción; summary con lente REFERENCE_CURRENCY; HIJO crea vehículo → 403; `activeAlerts`.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Integridad contable verificada por tests (Regla 3).

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(vehicles): add vehicle registry with expiration alerts`
2. `feat(vehicles): add fuel logs with atomic accounting integration`
3. `feat(vehicles): add maintenance logs with next due tracking`
4. `feat(vehicles): add consumption and cost per km summary with lenses`
5. `feat(vehicles): keep logs in sync with transaction changes`
