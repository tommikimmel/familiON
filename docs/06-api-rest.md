# 06 — Contrato de API REST

## 1. Convenciones Generales

- **Base URL:** `/api/v1`
- **Autenticación:** `Authorization: Bearer <access_token>` (JWT). Refresh token mediante endpoint dedicado o cookie `HttpOnly`.
- **Formato de errores:** RFC 7807 (`application/problem+json` con `ProblemDetail` nativo de Spring Boot 3).
- **Paginación:** `?page=0&size=20&sort=transactionDate,desc`.
- **Parámetros globales de visualización (Finanzas y Analítica):**
  - `view=PERSONAL | FAMILY` — Alterna entre los gastos del usuario autenticado y los gastos consolidados de la familia (validado en backend según `role` y `family_visibility`).
  - `lens=NOMINAL | CONSTANT_BASE | REFERENCE_CURRENCY | INCOME_PCT` — Lente anti-inflación.
  - `granularity=DAY | WEEK | MONTH | YEAR` — Agrupación temporal.

---

## 2. Endpoints por Módulo

### 2.1 Autenticación, 2FA y Recuperación por Email (`/api/v1/auth`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/auth/register` | Público | Registra un usuario `ADULTO`, crea su familia inicial y envía correo de verificación. |
| `POST` | `/auth/register-invite` | Público | Registra un miembro (`ADULTO` o `HIJO`) usando un token de invitación. |
| `POST` | `/auth/verify-email` | Público | Valida el código enviado por correo y activa la verificación de email. |
| `POST` | `/auth/login` | Público | Valida credenciales. Si el usuario tiene 2FA activo y el dispositivo no es confiable, envía código OTP por email y devuelve `requires2fa: true` + `challengeToken`. |
| `POST` | `/auth/2fa/verify` | Público (con `challengeToken`) | Verifica el código de 6 dígitos enviado por mail, emite sesión JWT (cookies `HttpOnly` vía BFF) y opcionalmente marca el dispositivo como confiable. |
| `POST` | `/auth/2fa/resend` | Público (con `challengeToken`) | Reenvía un nuevo código 2FA respetando cooldown anti-spam (60 seg). |
| `POST` | `/auth/forgot-password` | Público | Inicia recuperación por olvido de contraseña enviando enlace/código por mail (respuesta genérica anti-enumeración). |
| `POST` | `/auth/reset-password` | Público | Valida el token de un solo uso, actualiza la contraseña, revoca todas las sesiones activas y envía mail de confirmación. |
| `POST` | `/auth/refresh` | Público | Rota el Refresh Token y emite un nuevo Access Token. |
| `POST` | `/auth/logout` | Autenticado | Revoca el Refresh Token activo e invalida el Access Token en Redis. |
| `GET` | `/auth/me` | Autenticado | Devuelve perfil del usuario, rol, `familyVisibility`, `themePreference`, `accentColor`, estado 2FA y configuración de su familia. |
| `PATCH` | `/auth/me/preferences` | Autenticado | Actualiza preferencias de interfaz del usuario (`themePreference` y `accentColor`). |
| `PATCH` | `/auth/me/security` | Autenticado | Configura 2FA y permite listar/revocar dispositivos confiables (`trusted_devices`). |

### 2.2 Familia, Miembros, Divisas y Mensualidades (`/api/v1/family`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/family` | Autenticado | Configuración de la familia (moneda base, moneda de referencia, divisas habilitadas). |
| `PATCH` | `/family/settings` | `ADULTO` | Actualiza nombre, divisa base, divisa/fuente de referencia y zona horaria. |
| `GET` | `/family/currencies` | Autenticado | Lista divisas habilitadas de la familia. |
| `POST` | `/family/currencies` | `ADULTO` | Habilita una nueva divisa para la familia (ej. `USD`, `EUR`, `USDT`). |
| `GET` | `/family/members` | Autenticado | Lista integrantes de la familia con su rol y permiso de visibilidad. |
| `POST` | `/family/invitations` | `ADULTO` | Genera invitación para un nuevo adulto o hijo. |
| `PATCH` | `/family/members/{userId}/visibility` | `ADULTO` | Cambia permiso de visibilidad de un `HIJO` (`OWN_ONLY`, `FAMILY_SUMMARY`, `FAMILY_FULL`). |
| `GET` | `/family/allowances` | Autenticado | Lista mensualidades (el `HIJO` solo ve las propias; el `ADULTO` ve todas). |
| `POST` | `/family/allowances` | `ADULTO` | Configura mensualidad periódica para un hijo. |
| `POST` | `/family/allowances/{id}/disburse` | `ADULTO` | Acredita la mensualidad generando el par `ALLOWANCE_OUT` / `ALLOWANCE_IN` (sin doble conteo). |

### 2.3 Motor Económico e Índices (`/api/v1/economics`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/economics/indices/latest` | Autenticado | Últimas cotizaciones e índices disponibles (`USD_MEP`, `USD_BLUE`, `USD_OFICIAL`, `EUR`, `UVA`, `IPC`). |
| `GET` | `/economics/indices/history` | Autenticado | Serie histórica por `indexCode`, `from`, `to`. |
| `POST` | `/economics/indices/manual` | `ADULTO` | Carga o corrección manual de una cotización/índice para una fecha. |

### 2.4 Cuentas y Compra/Venta de Divisas (`/api/v1/accounts` y `/api/v1/currency-exchanges`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/accounts` | Autenticado | Lista cuentas con saldo actual calculado (soporta `?view=PERSONAL\|FAMILY`). |
| `POST` | `/accounts` | Autenticado | Crea cuenta (efectivo, banco, billetera, ahorro en divisa o tarjeta de crédito). |
| `PATCH` | `/accounts/{id}` | Propietario / `ADULTO` | Actualiza datos de la cuenta (ej. límite, cierre/vencimiento de tarjeta). |
| `DELETE` | `/accounts/{id}` | Propietario / `ADULTO` | Baja lógica de cuenta. |
| `POST` | `/currency-exchanges` | Autenticado | Registra compra/venta de divisas entre cuentas con `appliedExchangeRate` real. |
| `GET` | `/currency-exchanges` | Autenticado | Historial de operaciones cambiarias y **precio promedio ponderado de compra** por divisa. |

### 2.5 Categorías y Transacciones (`/api/v1/categories` y `/api/v1/transactions`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/categories` | Autenticado | Lista categorías del sistema + personalizadas de la familia. |
| `POST` | `/categories` | `ADULTO` | Crea categoría o subcategoría personalizada. |
| `GET` | `/transactions` | Autenticado | Listado paginado con filtros: `view`, `lens`, `from`, `to`, `type`, `categoryId`, `accountId`, `attributedToUserId`, `currency`, `status`. |
| `POST` | `/transactions` | Autenticado | Registra ingreso, gasto o transferencia y genera automáticamente su `EconomicSnapshot`. Un `ADULTO` puede indicar `attributedToUserId` para imputar el gasto a otro miembro de la familia. |
| `PATCH` | `/transactions/{id}` | Autor / `ADULTO` | Edita un movimiento y recalcula saldo/snapshot si cambió fecha o monto. |
| `DELETE` | `/transactions/{id}` | Autor / `ADULTO` | Baja lógica del movimiento. |

### 2.6 Cuotas, Gastos Fijos y Metas
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/installment-plans` | Autenticado | Lista planes de cuotas activos/completados con resumen de deuda restante y recargo real. |
| `POST` | `/installment-plans` | Autenticado | Crea compra en cuotas (con o sin interés) y proyecta automáticamente las `N` cuotas mensuales. |
| `POST` | `/installment-plans/{id}/cancel-remaining` | Autor / `ADULTO` | Cancela o precancela cuotas futuras pendientes. |
| `GET` | `/recurring-expenses` | Autenticado | Lista gastos fijos mensuales y vencimientos pendientes del mes actual. |
| `POST` | `/recurring-expenses` | `ADULTO` | Crea gasto fijo con regla de actualización (`MANUAL`, `IPC`, `REFERENCE_CURRENCY`, `LAST_PAID`). |
| `POST` | `/recurring-expenses/{id}/confirm-payment` | Autenticado | Confirma el pago del mes (permitiendo ajustar el monto exacto pagado) y genera la `Transaction`. |
| `GET` | `/savings-goals` | Autenticado | Lista metas personales y familiares con progreso en moneda/unidad estable. |
| `POST` | `/savings-goals` | Autenticado | Crea una meta de ahorro (`ARS`, `USD`, `EUR`, `UVA`, `ARS_IPC`). |
| `POST` | `/savings-goals/{id}/contributions` | Autenticado | Registra aporte a una meta con conversión a la unidad de la meta. |

### 2.7 Módulo de Vehículos (`/api/v1/vehicles`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/vehicles` | Autenticado | Lista vehículos de la familia con odómetro actual y alertas activas (service, VTV, seguro). |
| `POST` | `/vehicles` | `ADULTO` | Registra un nuevo vehículo familiar. |
| `PATCH` | `/vehicles/{id}` | `ADULTO` | Actualiza datos técnicos, vencimientos o kilometraje actual. |
| `GET` | `/vehicles/{id}/summary` | Autenticado | Métricas del vehículo: consumo promedio (L/100km y km/L), costo por km y costo total mensual (con `lens`). |
| `GET` | `/vehicles/{id}/fuel-logs` | Autenticado | Historial de cargas de combustible. |
| `POST` | `/vehicles/{id}/fuel-logs` | Autenticado | Registra carga de combustible (litros, precio/litro, odómetro, tanque lleno) y crea automáticamente la `Transaction`. |
| `GET` | `/vehicles/{id}/maintenance-logs` | Autenticado | Historial de desgastes y services + próximos vencimientos. |
| `POST` | `/vehicles/{id}/maintenance-logs` | Autenticado | Registra service/arreglo (con `nextDueKm` / `nextDueDate`) y crea la `Transaction` si tiene costo > 0. |

### 2.8 Analítica Temporal y Anti-Inflación (`/api/v1/analytics`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/analytics/summary` | Autenticado | Resumen por `granularity=DAY\|WEEK\|MONTH\|YEAR`, `view=PERSONAL\|FAMILY` y `lens`: totales de ingresos, gastos, tasa de ahorro y compromiso en cuotas. |
| `GET` | `/analytics/series` | Autenticado | Serie temporal para gráficos comparativos (ej. últimos 12 meses en ARS Constantes vs. ARS Nominales vs. USD). |
| `GET` | `/analytics/by-category` | Autenticado | Distribución para gráfico de torta por categoría con variación real respecto al período anterior y % del ingreso. |
| `GET` | `/analytics/by-person` | Autenticado | Distribución para gráfico de torta **por persona** de la familia (agrupado por `attributed_to_user_id`, respetando permisos de visibilidad). |
| `GET` | `/analytics/calendar` | Autenticado | Vista diaria de movimientos y próximos vencimientos (cuotas, gastos fijos, VTV/seguro/services). |

### 2.9 Escaneo de Tickets / OCR (`/api/v1/receipts` — Fase 4)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `POST` | `/receipts/scan` | Autenticado | Sube foto de ticket (`multipart/form-data`), guarda el adjunto y dispara extracción OCR devolviendo borrador de transacción sugerido. |
| `GET` | `/receipts/{id}` | Propietario / `ADULTO` | Consulta estado del OCR y descarga del comprobante. |

### 2.10 Notificaciones, Web Push (PWA) y Versiones (`/api/v1/notifications` y `/api/v1/system`)
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| `GET` | `/system/version` | Público | Devuelve versión actual desplegada (`SemVer`) y última nota de cambios para la PWA. |
| `GET` | `/system/changelog` | Autenticado | Historial de versiones y novedades (`app_releases`) para el modal «Qué cambió». |
| `GET` | `/notifications` | Autenticado | Lista notificaciones del usuario (soporta `?unreadOnly=true`). |
| `PATCH` | `/notifications/{id}/read` | Propietario | Marca una notificación como leída. |
| `POST` | `/notifications/read-all` | Autenticado | Marca todas las notificaciones del usuario como leídas. |
| `POST` | `/notifications/push-subscriptions` | Autenticado | Registra la suscripción Web Push (Service Worker PWA) del dispositivo. |
| `DELETE` | `/notifications/push-subscriptions` | Autenticado | Desactiva las notificaciones push del dispositivo actual. |
