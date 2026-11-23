# Diseño de Base de Datos — PostgreSQL 17

Este documento describe las convenciones, estructura relacional, decisiones de modelado financiero e índices de la base de datos de **FamiliON**. El DDL completo ejecutable se encuentra en [schema.sql](./schema.sql), listo para usarse como migración inicial de Flyway (`V1__init_schema.sql`).

---

## 1. Convenciones de Diseño

| Aspecto | Convención adoptada | Motivo |
|---|---|---|
| **Claves primarias** | `UUID` (`DEFAULT gen_random_uuid()`) | Evita enumeración de IDs en la API REST y facilita sincronización/importación futura. |
| **Montos monetarios** | `NUMERIC(19, 4)` | Precisión exacta sin errores de punto flotante (`BigDecimal` en Java). |
| **Tipos de cambio / Índices** | `NUMERIC(19, 6)` | Evita errores de redondeo en conversiones ARS ↔ USD/EUR/UVA e IPC acumulado. |
| **Litros y precios/litro** | `NUMERIC(12, 3)` y `NUMERIC(12, 4)` | Precisión real de surtidores y cálculo exacto de L/100km y costo/km. |
| **Fechas contables** | `DATE` | Las transacciones se imputan a un día calendario local (sin corrimientos por UTC al agrupar por día/semana/mes/año). |
| **Auditoría** | `TIMESTAMPTZ` (`created_at`, `updated_at`) | Trazabilidad exacta en UTC. |
| **Borrado** | Soft delete (`deleted_at TIMESTAMPTZ`) en entidades financieras | Preserva consistencia histórica de reportes, cuotas y vehículos. |
| **Aislamiento multi-familia** | Columna `family_id` en todas las tablas de negocio | Garantiza seguridad a nivel de consulta e índices eficientes por hogar. |
| **Enums** | `VARCHAR` + `CHECK` constraint | Más simple de evolucionar con Flyway que `CREATE TYPE ... AS ENUM` en PostgreSQL. |

---

## 2. Mapa de Tablas por Módulo

### A. Familia, Usuarios, Roles y Permisos
1. **`families`**: Configuración del hogar: moneda base (`base_currency`, ej. `ARS`), divisa/referencia estable (`reference_currency`, ej. `USD`), fuente de cotización preferida (`reference_rate_source`, ej. `USD_MEP`, `USD_BLUE`, `USD_OFICIAL`) y zona horaria.
2. **`family_currencies`**: Divisas habilitadas por la familia (`ARS`, `USD`, `EUR`, `USDT`, etc.) con la fuente de cotización que prefieren usar para cada una.
3. **`users`**: Miembros de la familia con `role` (`ADULTO` | `HIJO`), `theme_preference` (`LIGHT` | `DARK` | `SYSTEM`), `accent_color` (código hexadecimal `#RRGGBB`, ya sea preset o color personalizado desde el color picker `+`) y `family_visibility`:
   - Los `ADULTO` tienen siempre `FAMILY_FULL` (ven el detalle de todos los gastos familiares).
   - Los `HIJO` tienen visibilidad configurable por los adultos: `OWN_ONLY`, `FAMILY_SUMMARY` o `FAMILY_FULL`.
4. **`family_invitations`**: Invitaciones con token y vencimiento para sumar integrantes a la familia.
5. **`refresh_tokens`**: Tokens de refresco hasheados para rotación segura y revocación de sesiones JWT.
6. **`email_security_codes`**: Códigos OTP (6 dígitos) y tokens de un solo uso hasheados para **2FA por email** (`TWO_FACTOR_LOGIN`), verificación de correo (`EMAIL_VERIFICATION`) y **olvido de contraseña** (`PASSWORD_RESET`), con control de intentos (`attempts`/`max_attempts`) y expiración corta.
7. **`trusted_devices`**: Dispositivos confiables recordados tras aprobar 2FA (ej. el celular habitual del usuario por 30 días, revocables desde Perfil).
8. **`allowances`**: Configuración de mensualidades asignadas por un adulto a un hijo (monto, divisa, frecuencia, regla de ajuste por inflación y cuenta destino del hijo).

### B. Motor Económico y Anti-Inflación
7. **`economic_indices`**: Serie histórica de cotizaciones e índices (`USD_OFICIAL`, `USD_MEP`, `USD_BLUE`, `EUR_OFICIAL`, `EUR_BLUE`, `UVA`, `IPC_MENSUAL`, `IPC_ACUMULADO`). Permite distinguir fuente `API` vs. `MANUAL`.
8. **`economic_snapshots`**: Relación `1:1` con cada `transaction`. Congela el día del movimiento:
   - Tipo de cambio efectivo a la moneda de referencia de la familia (`reference_exchange_rate`).
   - Monto equivalente en la moneda de referencia (`amount_in_reference_currency`).
   - Índice IPC/UVA del período (`ipc_index_value`, `uva_value`) para llevar cualquier monto pasado a **pesos constantes de hoy** en una sola operación matemática (`monto * ipc_hoy / ipc_fecha`).

### C. Finanzas: Cuentas, Divisas, Movimientos, Cuotas, Fijos y Metas
9. **`accounts`**: Efectivo, bancos, billeteras virtuales, cajas de ahorro en cualquier divisa y tarjetas de crédito (`closing_day`, `due_day`, `credit_limit`). Pueden ser personales (`owner_user_id`) o compartidas de la familia.
10. **`categories`**: Categorías jerárquicas (padre/hijo) de ingresos y gastos, con soporte para categorías del sistema y personalizadas por familia.
11. **`currency_exchanges`**: Operaciones de compra/venta de divisas entre cuentas de distinta moneda. Guarda:
    - Monto y moneda origen (`source_amount`, `source_currency`).
    - Monto y moneda destino (`target_amount`, `target_currency`).
    - **`applied_exchange_rate`**: el tipo de cambio real al que la familia compró/vendió esa divisa.
    - Comisiones (`fee_amount`, `fee_currency`) y notas (ej. *"Compra MEP por bróker"*).
    - Esto permite calcular el **precio promedio ponderado de compra (PPP)** de las tenencias en USD/EUR y conocer el resultado cambiario real.
12. **`installment_plans`**: Planes de cuotas (con o sin interés). Almacena `total_installments`, `has_interest`, `cash_price` (precio contado opcional para calcular el recargo real), `financed_total`, `installment_amount`, `start_billing_month` y la tarjeta de crédito asociada.
13. **`recurring_expenses`**: Gastos fijos mensuales (alquiler, expensas, prepaga, servicios, seguro) con día de vencimiento, monto base, moneda y regla de actualización (`MANUAL`, `IPC`, `REFERENCE_CURRENCY`, `LAST_PAID`).
14. **`receipt_attachments`**: Metadatos de imágenes/PDF de tickets escaneados, estado de procesamiento OCR (`UPLOADED`, `PROCESSING`, `PARSED`, `FAILED`) y JSON crudo extraído (preparado para Fase 4).
15. **`transactions`**: Tabla central de movimientos financieros:
    - Soporta `EXPENSE`, `INCOME`, `TRANSFER_OUT`, `TRANSFER_IN`, `EXCHANGE_OUT`, `EXCHANGE_IN`, `ALLOWANCE_OUT`, `ALLOWANCE_IN`.
    - Separa `user_id` (quien paga/registra) de **`attributed_to_user_id`** (a qué integrante de la familia se imputa el gasto; por defecto igual a `user_id`, editable por un `ADULTO`, ej. dinero entregado a un hijo para salir).
    - Incluye `status` (`POSTED` | `PROJECTED` | `CANCELLED`) para distinguir cuotas/gastos ya impactados de cuotas futuras proyectadas.
    - Enlaza opcionalmente a `installment_plan_id` (+ `installment_number`), `recurring_expense_id`, `currency_exchange_id`, `allowance_id` y `receipt_attachment_id`.
16. **`savings_goals`** y **`goal_contributions`**: Metas de ahorro personales o familiares con objetivo expresable en `ARS`, `USD`, `EUR`, `UVA` o `ARS_IPC` (ajustado por inflación), vinculadas a aportes reales.

### D. Módulo de Vehículos (Contabilidad y Desgaste)
17. **`vehicles`**: Vehículos de la familia (apodo, marca, modelo, año, patente, combustible principal/secundario, `current_odometer_km`, vencimientos de VTV/RTO, seguro y patente).
18. **`fuel_logs`**: Cargas de combustible vinculadas `1:1` a una `transaction`. Guarda `odometer_km`, `fuel_type`, `liters`, `price_per_liter`, `total_amount`, `full_tank`, `station_name`.
19. **`maintenance_logs`**: Historial de desgaste y services (aceite, filtros, cubiertas, frenos, correa, batería, reparaciones, VTV) vinculados a `transaction` (opcional si fue en garantía/costo $0), con `odometer_km`, `next_due_km` y `next_due_date` para alertas preventivas.

### E. Notificaciones, Web Push (PWA) y Versiones
20. **`notifications`**: Centro de notificaciones por usuario (`APP_UPDATE`, `EXPENSE_ATTRIBUTED`, `ALLOWANCE`, `FIXED_EXPENSE_DUE`, `VEHICLE_ALERT`, `SYSTEM`), con estado de lectura (`read_at`) y `metadata` en `JSONB`.
21. **`push_subscriptions`**: Suscripciones Web Push del Service Worker de la PWA para avisos en el celular cuando la web está agregada como atajo al inicio.
22. **`app_releases`**: Historial de versiones (`SemVer`) y novedades en Markdown para el modal «Qué cambió» de la aplicación.

---

## 3. Estrategia de Índices para Consultas Temporales

Como el usuario navegará frecuentemente por **año, mes, semana y día** y alternará entre **vista personal** y **vista familiar**:
- `idx_tx_family_date_active`: `(family_id, transaction_date DESC) WHERE deleted_at IS NULL` — acelera dashboards familiares, calendario y agregaciones temporales.
- `idx_tx_user_date_active`: `(family_id, user_id, transaction_date DESC) WHERE deleted_at IS NULL` — acelera consultas por pagador/autor.
- `idx_tx_attributed_date_active`: `(family_id, attributed_to_user_id, transaction_date DESC) WHERE deleted_at IS NULL` — acelera la vista personal de gastos atribuidos a cada integrante y el gráfico de torta **Por Personas**.
- `idx_tx_installment_plan`: `(installment_plan_id, installment_number)` — consulta inmediata del cronograma de cuotas.
- `idx_fuel_vehicle_date` y `idx_maint_vehicle_date`: cálculo rápido de consumo L/100km (comparando cargas consecutivas con `full_tank = TRUE`) y alertas de próximo service por kilometraje/fecha.
- `uq_economic_index_code_date`: `(index_code, effective_date)` — búsqueda instantánea `O(log n)` del snapshot económico de cualquier fecha.
