# T34 — feat(frontend): módulo Autos (listado, ficha/dashboard, combustible y service)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 4 | 4.1 | `feat/frontend-vehicles` | Fase 3 completa | T33, T35 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend: `core/finance/*` (`AccountsStore`, `FinanceEvents`), `core/state/*`, `shared/ui/*`, `shared/pipes/money.pipe.ts`; rutas en `features/vehicles/vehicles.routes.ts` (T02).

## Objetivo
Pantallas para cargar nafta en la estación con una mano, registrar services y ver el costo real del auto.

## Archivos permitidos
```
frontend/src/app/features/vehicles/**
```

## Contrato BFF

| Llamada | Respuesta |
|---|---|
| `GET /bff/api/vehicles` | `Vehicle[]` |
| `POST /bff/api/vehicles`, `PATCH /bff/api/vehicles/{id}`, `DELETE /bff/api/vehicles/{id}` (ADULTO) | `Vehicle` / `204` |
| `GET /bff/vehicles/{id}/dashboard?lens=` | `VehicleDashboard` (T35; campos pueden ser `null`) |
| `GET /bff/api/vehicles/{id}/fuel-logs?page=&size=` | `Page<FuelLog>` |
| `POST /bff/api/vehicles/{id}/fuel-logs` | `FuelLog` |
| `DELETE /bff/api/vehicles/{id}/fuel-logs/{logId}` | `204` |
| `GET /bff/api/vehicles/{id}/maintenance-logs?page=&size=` | `Page<MaintenanceLog>` |
| `POST /bff/api/vehicles/{id}/maintenance-logs` | `MaintenanceLog` |
| `DELETE /bff/api/vehicles/{id}/maintenance-logs/{logId}` | `204` |

```ts
type FuelType = 'NAFTA_SUPER'|'NAFTA_PREMIUM'|'DIESEL'|'DIESEL_PREMIUM'|'GNC'|'ELECTRICO';
type VehicleAlert = { kind: 'SERVICE_KM'|'SERVICE_DATE'|'VTV'|'INSURANCE'; title; dueDate: string|null; dueKm: number|null; severity: 'INFO'|'WARNING'|'OVERDUE' };
type Vehicle = { id; nickname; brand; model; modelYear: number; licensePlate; primaryFuelType: FuelType|'HIBRIDO'; secondaryFuelType: FuelType|null;
  currentOdometerKm: number; vtvExpirationDate: string|null; insuranceExpiration: string|null; insuranceCompany: string|null; notes: string|null;
  isActive: boolean; alerts: VehicleAlert[] };
type FuelLogCreate = { fuelDate?; odometerKm: number; fuelType: FuelType; liters?: Decimal; pricePerLiter?: Decimal; totalAmount?: Decimal;
  currencyCode; fullTank: boolean; stationName?; notes?; accountId: string; attributedToUserId? };
type FuelLog = { id; fuelDate; odometerKm; fuelType; liters: Decimal; pricePerLiter: Decimal; totalAmount: Decimal; currencyCode; fullTank: boolean;
  stationName; notes; transactionId; user: { id; fullName } };
type MaintenanceType = 'SERVICE'|'OIL_CHANGE'|'FILTERS'|'TIRES'|'BRAKES'|'TIMING_BELT'|'BATTERY'|'SUSPENSION'|'ALIGNMENT'|'VTV'|'REPAIR'|'OTHER';
type MaintenanceLogCreate = { maintenanceType: MaintenanceType; maintenanceDate?; odometerKm: number; description; workshopName?; costAmount: Decimal;
  currencyCode; accountId?: string; nextDueKm?: number; nextDueDate?; notes? };
type MaintenanceLog = MaintenanceLogCreate & { id; transactionId: string|null; user: { id; fullName } };
type VehicleSummary = { vehicleId; from; to; lens; unit; distanceKm: number; totalLiters: Decimal; avgKmPerLiter: Decimal|null;
  avgLitersPer100Km: Decimal|null; costPerKm: Decimal|null; totalCost: Decimal; fuelCost: Decimal; maintenanceCost: Decimal; lensApproximated: boolean;
  monthly: { month; fuelCost: Decimal; maintenanceCost: Decimal; km: number }[]; pricePerLiterTrend: { date; pricePerLiter: Decimal; fuelType }[] };
type VehicleDashboard = { vehicle: Vehicle|null; summary: VehicleSummary|null; recentFuelLogs: FuelLog[]|null;
  recentMaintenance: MaintenanceLog[]|null; alerts: VehicleAlert[]|null };
```
Labels: `NAFTA_SUPER` «Nafta súper», `NAFTA_PREMIUM` «Nafta premium», `DIESEL` «Diésel», `DIESEL_PREMIUM` «Diésel premium», `GNC` «GNC», `ELECTRICO` «Eléctrico», `HIBRIDO` «Híbrido»; mantenimiento: «Service», «Cambio de aceite», «Filtros», «Cubiertas», «Frenos», «Correa de distribución», «Batería», «Suspensión», «Alineación y balanceo», «VTV», «Reparación», «Otro».

## Especificación
1. **`/vehicles`:** tarjetas por auto (apodo, marca/modelo/año, patente, odómetro, chips de alertas por severidad). Vacío → CTA «Agregar vehículo» (ADULTO).
2. **`/vehicles/new` y `/:id/edit`** (ADULTO): formulario completo con validaciones (año 1900–2100, patente obligatoria).
3. **`/vehicles/:id`** (dashboard vía `/bff/vehicles/{id}/dashboard`, lente desde `ViewContextStore` — si es `INCOME_PCT` usar `NOMINAL`): KPIs km/L y L/100 km, costo por km, costo total del período (combustible vs mantenimiento), gráfico mensual (barras apiladas), evolución del precio por litro, alertas destacadas arriba, últimas cargas y services (con borrar), botones grandes **«Cargar combustible»** y **«Registrar service»**.
4. **`/:id/fuel/new`** (pensado para la estación, una mano): odómetro (precargado con el actual, teclado numérico), tipo de combustible (default el principal), 2 de 3 entre litros / precio por litro / total (el tercero se calcula en vivo, el último editado manda), «Tanque lleno» (switch, default sí), cuenta (default habitual), estación opcional, fecha hoy. Guardar → toast, `FinanceEvents.emit('vehicle-changed')` y `('transaction-created')`, volver al dashboard.
5. **`/:id/maintenance/new`:** tipo (chips), fecha, odómetro, descripción, taller, costo (si > 0 pide cuenta), «Próximo» por km y/o fecha (sugerencias: aceite +10.000 km / 12 meses).
6. Errores `422` (odómetro incoherente) mostrados en el campo.

## Tests
- Cálculo en vivo del tercer valor de combustible (función pura `fuel-math.ts`).
- Store del dashboard (lente forzada NOMINAL si INCOME_PCT, manejo de `null`).
- Formulario de service: cuenta obligatoria solo con costo > 0.

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Formulario de carga usable con una mano en 360px de ancho.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(vehicles-ui): add vehicles list and form`
2. `feat(vehicles-ui): add vehicle dashboard with consumption and cost metrics`
3. `feat(vehicles-ui): add fuel and maintenance log forms`
