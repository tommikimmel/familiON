# 01 — Visión y Propuesta Anti-Inflación

## 1. El Problema

En economías con inflación persistente y volatilidad cambiaria como la de Argentina, las aplicaciones tradicionales de finanzas personales fallan por tres motivos:

1. **Los números nominales pierden significado con el tiempo:** comparar un gasto de supermercado de enero contra uno de noviembre en pesos nominales no dice si la familia consumió más o si solo aumentaron los precios.
2. **Los presupuestos y gastos fijos quedan obsoletos todos los meses:** configurar un límite mensual en pesos obliga a reajustarlo manualmente cada vez que hay aumentos o paritarias.
3. **Las cuotas distorsionan la percepción del gasto real:** una cuota fija en pesos de hace 8 meses pesa hoy mucho menos que al inicio (licuación), mientras que las cuotas con interés esconden recargos reales difíciles de evaluar frente a la inflación esperada.

## 2. Visión de FamiliON

Convertirse en el centro de control financiero de la familia donde **la inflación pasa a ser un dato de fondo que el sistema absorbe automáticamente**, permitiendo tomar decisiones basadas en **poder adquisitivo real**, **proporción del ingreso** y **compromisos futuros claros**, integrando además gastos cotidianos, mensualidades de hijos y los costos reales de los vehículos del hogar.

---

## 3. Estrategia Propuesta: "Motor Anti-Inflación de Triple Lente"

> **Nota:** Esta es una propuesta inicial de diseño para resolver cómo volver secundaria la inflación. Puede ajustarse según tus preferencias antes de pasar al desarrollo.

La idea central es **nunca perder el dato nominal real** (lo que efectivamente pagaste en pesos o dólares), pero **enriquecer cada transacción automáticamente** al momento de registrarla para poder analizarla sin distorsión inflacionaria.

### Pilar 1: Multimoneda Configurable y Snapshot Económico Automático
- **Divisas configurables por familia:** Cada familia elige su **moneda principal** (ej. `ARS`), su **moneda/referencia estable** (ej. `USD_MEP`, `USD_BLUE`, `USD_OFICIAL`, `EUR`, `UVA`) y puede operar en **múltiples divisas simultáneamente** (ej. cuentas en ARS, USD, EUR o USDT).
- **Tipo de cambio efectivo en compra/venta de divisas:** Cuando la familia compra una divisa (por ejemplo, compra USD pagando en ARS), se registra el **tipo de cambio real al que se compró** (`exchange_rate`), permitiendo conocer el costo real de adquisición (precio promedio ponderado) y distinguir diferencias cambiarias reales al gastar o vender esa divisa.
- **Snapshot económico por movimiento:** Cada vez que se registra un movimiento (gasto, ingreso, carga de combustible, cuota), el backend guarda:
  - `amount` y `currency_code` (la moneda real en que se operó).
  - **Cotización de referencia del día** según la divisa elegida por la familia (obtenida automáticamente de APIs públicas con fallback manual).
  - **Índice de precios del período** (IPC mensual / UVA vigente).

Con ese snapshot, el sistema puede calcular en cualquier momento el valor equivalente sin recalcular ni corromper datos históricos.

### Pilar 2: Selector de "Lente" en Toda la Aplicación
En cualquier pantalla de reportes, gráficos o listados (día, semana, mes, año), el usuario puede cambiar la unidad de visualización con un selector:

| Lente | Qué muestra | Para qué sirve |
|---|---|---|
| **ARS Nominal** | Los pesos exactos que se pagaron ese día. | Conciliar con el banco, billetera o resumen de tarjeta. |
| **ARS Constantes (Hoy)** | Montos pasados ajustados por IPC a pesos de hoy. | Comparar si realmente gastaste más en comida o combustible que hace 6 meses o 1 año. |
| **Moneda Estable (USD / UVA)** | Equivalente en dólares (o UVA) al día del movimiento. | Medir ahorros, metas a mediano plazo y costos grandes (ej. arreglos del auto, vacaciones). |
| **% del Ingreso Familiar** | Qué porcentaje de los ingresos del mes absorbió cada rubro. | **La métrica más intuitiva anti-inflación:** sin importar cuántos ceros tenga el peso, si el súper pasa del 20% al 28% del ingreso, hay una alerta real. |

### Pilar 3: Presupuestos, Gastos Fijos y Metas "Vivos"
- **Gastos Fijos con Regla de Actualización:** Al crear un gasto fijo mensual (alquiler, expensas, prepaga, colegio, seguro del auto), se puede indicar cómo suele actualizarse:
  - Monto fijo manual.
  - Indexado por **IPC / inflación mensual**.
  - Referenciado en **USD** o **UVA**.
  - Estimación por último monto real pagado (toma automáticamente lo que se pagó el mes anterior + inflación estimada para proyectar el mes siguiente).
- **Metas de Ahorro en Valor Real:** Una meta (ej. *"Vacaciones 2027"* o *"Cambio de cubiertas"*) se define preferentemente en **USD**, **UVA** o **ARS ajustados por IPC**, mostrando cuánto falta en poder adquisitivo real y no en pesos congelados.
- **Cuotas con y sin Interés (Visibilidad Real):**
  - El sistema desglosa las cuotas mes a mes dentro del flujo futuro proyectado.
  - En compras con interés, registra precio contado vs. precio financiado para mostrar el **costo financiero total (recargo real)**.
  - En reportes de proyección futura, permite ver tanto el compromiso nominal en pesos como su **peso real estimado** (mostrando cómo las cuotas fijas se van licuando si los ingresos acompañan la inflación).

### Pilar 4: Indicador de "Salud Familiar" (Semáforo)
En lugar de obligar a la familia a interpretar índices económicos complejos, el dashboard resume todo en 3 indicadores simples:
1. **Tasa de ahorro real del mes** (% de ingresos no consumidos).
2. **Compromiso futuro en cuotas** (% del ingreso mensual ya comprometido para los próximos meses).
3. **Variación real vs. promedio histórico** (ej. *"Este mes gastaron +8% real en Combustible respecto a los últimos 3 meses"*).
