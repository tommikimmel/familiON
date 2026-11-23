# 02 — Requerimientos Funcionales y No Funcionales

## 1. Módulos y Requerimientos Funcionales

### M1. Usuarios, Familias, Roles y Visibilidad
- **RF-01 — Gestión y Edición de Familias:** Un usuario adulto puede crear una familia, **editar los datos de la familia** en cualquier momento (nombre de la familia, divisa principal, divisa/fuente de referencia, divisas habilitadas y zona horaria) e invitar/vincular integrantes mediante código o invitación por email.
- **RF-02 — Roles dentro de la Familia:**
  - `ADULTO`: Acceso completo a la gestión familiar (configuración económica, gastos fijos familiares, metas familiares, vehículos, administración de hijos y asignación de mensualidades).
  - `HIJO`: Acceso orientado a sus finanzas personales y educación financiera. Recibe una mensualidad configurada por un adulto y registra sus propios gastos y metas personales.
- **RF-03 — Vista Dual (Personal vs. Familiar) y Permisos de Visibilidad:**
  - Todos los gastos registrados por un miembro pertenecen al ámbito de su familia.
  - En la pantalla principal, el usuario ve por defecto **sus gastos personales**.
  - Mediante un botón/selector rápido (**"Ver gastos de la familia"**), cambia a la vista familiar:
    - **`ADULTO`**: Puede ver el **detalle completo** de todos los gastos, ingresos y movimientos de todos los miembros de la familia.
    - **`HIJO`**: Su visibilidad depende de lo que configuren los adultos para cada hijo:
      - `OWN_ONLY`: Solo ve sus propios movimientos y su mensualidad.
      - `FAMILY_SUMMARY`: Ve sus movimientos y totales consolidados de la familia (sin detalle individual de los demás).
      - `FAMILY_FULL`: Ve sus movimientos y el detalle completo de la familia.
- **RF-04 — Mensualidad para Hijos:**
  - Un `ADULTO` puede definir una mensualidad periódica (mensual o semanal) para un `HIJO`, asociada a una cuenta/presupuesto del hijo.
  - Al acreditarse la mensualidad, se registra el movimiento sin duplicar el gasto en el balance consolidado familiar (ver regla en `03-modelo-de-dominio.md`).
- **RF-04b — Atribución de Gastos a Otro Integrante de la Familia:**
  - Un `ADULTO` puede registrar un gasto pagado desde su propia cuenta (o cuenta familiar) y **atribuirlo a otra persona de la familia** (ej. un padre/madre le da $20.000 a su hijo para salir).
  - El dinero se descuenta de la cuenta de origen del adulto, pero en métricas personales y en el gráfico de torta **Por Personas** el gasto se contabiliza como gasto del integrante atribuido (el hijo), no del adulto.
  - Un `HIJO` solo puede atribuir movimientos a sí mismo.
- **RF-04c — Personalización de Perfil (Tema y Color):**
  - Cada usuario elige su **tema** (`Claro`, `Oscuro` o `Sistema`) y su **color de perfil** (acento único de su interfaz y del toggle `ON` del logo).
  - La selección de color ofrece colores predeterminados más un **botón `+` que abre un color picker** para elegir cualquier color personalizado (`#RRGGBB`), ajustando automáticamente el contraste del texto/íconos para mantener legibilidad.

---

### M2. Finanzas Integrales: Cuentas, Ingresos, Gastos y Cuotas
- **RF-05 — Cuentas y Medios de Pago:**
  - Soporte para múltiples cuentas por usuario o compartidas por la familia: Efectivo, Cuenta Bancaria, Billetera Virtual (ej. Mercado Pago), Caja de Ahorro en USD, Tarjeta de Débito y Tarjeta de Crédito.
  - Cada cuenta tiene moneda base (`ARS`, `USD`, etc.) y saldo actual calculado/conciliable.
  - Para **Tarjetas de Crédito**, se registran límite, día de cierre y día de vencimiento del resumen.
- **RF-06 — Ingresos:**
  - Registro de ingresos (sueldos, aguinaldo, trabajos freelance, rentas, rendimientos, otros) con fecha, categoría, cuenta destino, moneda y alcance (personal/familiar).
- **RF-07 — Gastos y Categorías:**
  - Registro de gastos con monto, moneda, fecha, categoría/subcategoría, cuenta/medio de pago, notas y etiquetas.
  - Categorías predefinidas pensadas para hogares argentinos (Supermercado, Alquiler/Expensas, Servicios, Prepaga, Educación, Vehículos, Salidas, etc.) + categorías personalizadas por familia.
- **RF-08 — Compras en Cuotas (Con y Sin Interés):**
  - Al cargar un gasto con tarjeta de crédito (o financiación), se indica cantidad de cuotas (`N`), si tiene interés o es sin interés, precio contado (opcional, para calcular el recargo) y fecha de primera cuota / resumen de impacto.
  - El sistema genera el cronograma de `N` cuotas futuras, reflejándolas en el flujo proyectado mensual y en el resumen de tarjeta correspondiente.
  - Permite cancelar anticipadamente o ajustar una cuota si fuera necesario.
- **RF-09 — Transferencias entre Cuentas y Compra/Venta de Divisas (Multimoneda):**
  - Cada familia elige su moneda base, su referencia estable y puede habilitar múltiples divisas (`ARS`, `USD`, `EUR`, etc.).
  - Transferencias entre cuentas de la misma moneda sin contarlas como gasto ni ingreso.
  - Operaciones de **compra/venta de divisas** registrando obligatoriamente el **tipo de cambio real al que se operó** (monto origen, moneda origen, monto destino, moneda destino, cotización efectiva y comisiones si existieran), permitiendo llevar el costo promedio real de adquisición de cada divisa.

---

### M3. Gastos Fijos Recurrentes y Metas de Ahorro
- **RF-10 — Gastos Fijos Mensuales:**
  - Definición de gastos fijos recurrentes (ej. alquiler, expensas, luz, gas, internet, colegio, seguro) con día estimado de vencimiento, monto base, regla de actualización (manual, IPC, USD, último valor) y cuenta sugerida.
  - Cada mes el sistema genera los ítems pendientes de pago para que el usuario solo los confirme (ajustando el monto final exacto si varió).
  - Alertas de próximos vencimientos.
- **RF-11 — Metas de Ahorro:**
  - Creación de metas familiares o personales con nombre, monto objetivo, moneda de referencia (`ARS`, `USD`, `UVA` o `ARS ajustados por IPC`), fecha objetivo y aportes acumulados.
  - Seguimiento visual del porcentaje de avance y cuánto debería ahorrarse por mes para cumplirla.

---

### M4. Módulo de Vehículos (Contabilidad y Desgaste)
- **RF-12 — Ficha de Vehículos Familiares:**
  - Alta, edición y baja lógica de uno o más vehículos pertenecientes a la familia: apodo, marca, modelo, año, patente, tipo de combustible principal/secundario (ej. Nafta + GNC), kilometraje actual y datos de vencimientos (VTV/RTO, póliza de seguro, patente).
- **RF-13 — Registro de Combustible:**
  - Carga de cada abastecimiento con: fecha, vehículo, tipo de combustible, kilometraje del odómetro, litros (o m³ para GNC), precio por litro, monto total, estación de servicio (opcional) y marca de "tanque lleno".
  - Cálculo automático de métricas: **consumo promedio (km/L y L/100km)**, **costo por kilómetro** (nominal y en valor constante/USD) y evolución del precio del litro.
- **RF-14 — Desgaste, Services y Mantenimiento:**
  - Registro de eventos de mantenimiento y desgaste: service programado, cambio de aceite y filtros, cubiertas, frenos, batería, correa de distribución, amortiguadores, reparaciones, alineación/balanceo.
  - Cada mantenimiento guarda: fecha, kilometraje, descripción/repuestos, taller, costo y **próximo vencimiento por kilometraje y/o fecha** (ej. *"próximo cambio de aceite a los 95.000 km o en 12 meses"*).
  - Alertas preventivas cuando el vehículo se acerca al kilometraje o fecha de próximo service/VTV/seguro.
- **RF-15 — Integración Contable del Vehículo:**
  - Todo gasto de combustible, service, seguro, patente, peaje o multa cargado en el módulo de vehículos genera automáticamente (y queda vinculado a) un gasto en la contabilidad general bajo la categoría **Vehículos**, evitando la doble carga manual.

---

### M5. Organización Temporal y Analítica Anti-Inflación
- **RF-16 — Vistas Temporales:**
  - Navegación y agregación de gastos, ingresos y balance por **Año**, **Mes**, **Semana** y **Día**.
  - Vista de detalle diario / calendario de movimientos y vencimientos.
  - Comparativas período contra período (mes actual vs. mes anterior, año actual vs. año anterior, promedio de últimos 3/6 meses).
- **RF-17 — Selector de Unidad Anti-Inflación:**
  - Posibilidad de visualizar dashboards y reportes en **ARS Nominal**, **ARS Constantes (IPC)**, **USD** o **% del Ingreso Familiar**.
- **RF-18 — Panel de Indicadores Económicos:**
  - Sincronización periódica automática de cotizaciones (USD Oficial/MEP/Blue) e índices (IPC/UVA) con posibilidad de carga o corrección manual por parte de un administrador.

---

### M6. Carga de Datos y Escaneo de Tickets (OCR)
- **RF-19 — Carga Rápida Manual (MVP):**
  - Formulario optimizado para celular y escritorio que permita registrar un gasto en menos de 10 segundos (monto, categoría, cuenta, fecha hoy por defecto).
- **RF-20 — Escaneo de Tickets / Facturas (Fase Avanzada):**
  - Subida o captura desde cámara de una foto de ticket o factura.
  - Procesamiento asíncrono (OCR + extracción estructurada) para pre-rellenar: fecha, comercio, monto total, moneda, categoría sugerida y opcionalmente ítems.
  - El usuario revisa y confirma antes de impactar en la contabilidad; la imagen queda adjunta como comprobante.

---

## 2. Requerimientos No Funcionales

- **RNF-01 — Precisión Financiera:** Todos los montos y tipos de cambio se almacenan con precisión exacta (`NUMERIC(19, 4)` en PostgreSQL y `BigDecimal` en Java). Nunca usar `float`/`double` para dinero.
- **RNF-02 — Seguridad y Aislamiento:** Autenticación stateless con **JWT** (Access Token corto + Refresh Token revocable). Todo acceso a datos valida estrictamente pertenencia a la `familia` y permisos del `rol` (`ADULTO` / `HIJO`).
- **RNF-03 — Experiencia Móvil (Responsive / PWA):** La interfaz en Angular debe ser usable cómodamente desde el teléfono (mobile-first para carga de gastos y combustible en la estación de servicio).
- **RNF-04 — Portabilidad Local:** Todo el entorno (PostgreSQL, Backend, Frontend) debe levantarse con un único comando vía **Docker Compose**.
- **RNF-05 — Trazabilidad y Auditoría:** Campos de auditoría (`created_at`, `updated_at`, `created_by`) y borrado lógico en entidades financieras clave para preservar integridad histórica.
- **RNF-06 — Resiliencia ante APIs Externas:** Si las APIs de cotización de dólar/IPC no responden, la aplicación debe seguir operando normalmente usando la última cotización cacheada o ingreso manual.
