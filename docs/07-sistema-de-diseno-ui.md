# 07 — Sistema de Diseño y UX Mobile-First

Este documento resume las decisiones de experiencia de usuario e interfaz validadas para **FamiliON** antes de construir el frontend en **Angular 19+ y Tailwind CSS**.

---

## 1. Principios de Diseño

1. **Mobile-first real:** Toda interacción diaria (consultar saldo, alternar Personal/Familia, cargar un gasto o nafta) debe poder hacerse con una sola mano y el pulgar.
2. **Minimalismo monocromático + 1 color de perfil:** Superficies neutras limpias (tema **Claro** y **Oscuro**) acompañadas por un **único color de acento elegible por cada usuario** en su perfil (`accent_color` en formato `#RRGGBB`): presets curados + **botón `+` que abre un color picker personalizado**, con cálculo automático de contraste (luminancia WCAG) para que textos e íconos sobre el color elegido siempre sean legibles. Evitar exceso de colores compitiendo entre sí.
3. **Cero fricción en la carga diaria:** Cargar un gasto o ingreso común no debe tomar más de 5 segundos ni obligar a completar formularios largos.
4. **Legibilidad financiera:** Uso estricto de `tabular-nums` en montos, porcentajes y kilometrajes.

---

## 2. Identidad Visual (`docs/brand/`)

- **Logo `FamiliON`:** Combina la tipografía limpia `Famili` con un **toggle `ON`** geométrico en SVG puro:
  - [familion-logo-adaptive.svg](./brand/familion-logo-adaptive.svg) (hereda `currentColor` y toma `--familion-accent` del color de perfil).
  - [familion-logo-light.svg](./brand/familion-logo-light.svg) y [familion-logo-dark.svg](./brand/familion-logo-dark.svg).
  - [familion-icon.svg](./brand/familion-icon.svg) (ícono cuadrado para PWA y favicon).

---

## 3. Componentes Clave Validados

### 3.1 Header y Selector Compacto de Divisa
- **Izquierda:** Logo compacto `FamiliON`.
- **Derecha:** Chip pequeño con **bandera SVG + 3 letras** (`ARS`, `USD`, `EUR`). Al tocarlo se abre un dropdown liviano para cambiar entre las divisas de la familia y activar/desactivar el ajuste por inflación con etiqueta humana (**«A plata de hoy»**).
- **Debajo del header:** Toggle limpio **`Personal | Familia`** que cambia todo el contexto en 1 toque.

### 3.2 Barra Inferior de Navegación
- 4 accesos sobrios (`Inicio`, `Plan`, `Autos`, `Perfil`) y un **botón `+` central más grande (`56px`) y elevado** con corte visual sobre la barra, pensado como punto focal de carga rápida.

### 3.3 Carga Rápida (`+`) en 3 Toques
- **Enfoque exclusivo en `Gasto | Ingreso`:** Divisas y cargas detalladas de combustible (litros/odómetro) tienen sus accesos en *Plan* y *Autos* para mantener el `+` diario instantáneo.
- **Campos principales visibles:**
  1. **Monto grande** con chip compacto de divisa y teclado numérico con tecla `000`.
  2. **Categoría o Nombre** (elegir 1 de las categorías frecuentes o escribir un nombre libre; no obliga ambos).
  3. **Botón `Guardar`** único y directo (asume fecha hoy, cuenta habitual, 1 pago y atribución al propio usuario).
- **Secundarios no intrusivos:**
  - **Escaneo de ticket (OCR):** ícono discreto de cámara en la esquina superior derecha del sheet.
  - **Más opciones (colapsado por defecto):** Cuenta, Fecha, **«Para: [Integrante]»** (solo `ADULTO`, para atribuir un gasto a otra persona de la familia) y **Cuotas**.
  - **Regla UX anti-ambigüedad para Cuotas:** al elegir `> 1 cuota`, el usuario indica explícitamente si el monto ingresado **`Es el total`** (se divide por `N`) o **`Es cada cuota`** (se multiplica por `N`), elige **`Sin interés | Con interés`** (con campo opcional de *precio contado* para calcular el recargo real), y ve un resumen en vivo antes de guardar: *«6 cuotas de $36.000/mes · Total $216.000 (Dic 2026 → May 2027)»*.

### 3.4 Gráficos de Torta (Donut)
- Tarjeta interactiva en Inicio/Analítica con selector **`Categorías | Personas`**:
  - Usa una **escala tonal derivada del color de perfil + grises neutros** (respetando la regla de menos colores).
  - Al tocar una porción o ítem de la leyenda, muestra en el centro el nombre, monto (en la divisa seleccionada) y porcentaje exacto.
  - La vista **Por Personas** agrupa por `attributed_to_user_id`, reflejando los gastos realmente imputados a cada integrante.

---

## 4. Motion, Animaciones y Micro-Interacciones

Las animaciones deben reforzar la sensación de precisión y fluidez **sin ralentizar nunca la carga diaria** (duraciones cortas entre `140ms` y `360ms`):

| Interacción | Efecto / Curva | Objetivo UX |
|---|---|---|
| **Splash / Logo `FamiliON`** | Toggle `OFF → ON` con spring (`cubic-bezier(0.34, 1.35, 0.64, 1)`). | Refuerza la identidad de marca al abrir la PWA instalada. |
| **Selectores `Personal \| Familia` y `Categorías \| Personas`** | Cápsula deslizante (*sliding pill*) con física spring (`320ms`). | Sensación táctil nativa al cambiar de contexto. |
| **Balance y cambios de divisa/período** | Interpolación numérica suave (*count-up* en `280ms` ease-out) + *ambient glow* sutil del color de perfil en la tarjeta Hero. | Lectura fluida sin saltos bruscos de números. |
| **Gráfico de Torta (Donut)** | Transición suave de segmentos (`stroke-dasharray`/`dashoffset`), *pop-out* y glow al seleccionar una porción. | Exploración intuitiva de gastos por categoría o persona. |
| **Botón `+` y Bottom Sheet** | Rotación `45°` del `+`, sheet deslizante desde abajo (`360ms`) sobre backdrop difuminado (`blur(5px)`), micro-bump al teclear el monto. | Carga rápida cómoda con una mano. |
| **Confirmación de guardado** | Toast superior estilo píldora con checkmark SVG animado + entrada iluminada de la nueva fila en la lista. | Feedback inmediato de que el movimiento impactó correctamente. |
| **Accesibilidad** | Soporte estricto de `@media (prefers-reduced-motion: reduce)`. | Desactiva transiciones para usuarios sensibles al movimiento. |

---

## 5. Adaptación Responsive a Desktop (Web)

Mantiene exactamente la misma identidad minimalista (monocromático + 1 color de perfil, tema claro/oscuro y logo SVG `FamiliON`), reorganizando el layout para pantallas anchas (`≥ 1024px`):

- **Sidebar izquierda fija (`226px`):** Logo `FamiliON` interactivo, toggle deslizante **`Personal | Familia`**, CTA principal **`+ Nuevo movimiento`** (con atajo de teclado `N`), las 4 secciones (`Inicio`, `Planificación`, `Vehículos`, `Familia y Perfil`) y footer de usuario con toggle de tema.
- **Top bar limpia:** Título de contexto, selector temporal (`Día | Semana | Mes | Año`), selector compacto de divisa (**bandera + 3 letras** + switch *«A plata de hoy»*) y campana de notificaciones.
- **Grilla de Dashboard:**
  1. **3 KPI Cards:** Balance (con ambient glow sutil), Gastos vs. Ingresos, y resumen de Divisas (TC de compra) + Cuotas.
  2. **2 Gráficos lado a lado:** Evolución de 6 meses (que se reexpresa en vivo al activar *«A plata de hoy»*) + Gráfico de Torta interactivo **`Categorías | Personas`**.
  3. **Tabla de Movimientos:** columnas aireadas (Movimiento, Categoría, Atribuido a / quién pagó, Cuenta/Cuota, Fecha, Monto) con buscador instantáneo.
- **Carga Rápida en Desktop:** modal centrado con backdrop blur (input directo para teclado físico, atajo `N` para abrir y `Esc` para cerrar), conservando la simplicidad de Monto + Categoría/Nombre, atribución `Para: [Integrante]` (adultos), cuotas anti-ambigüedad y cámara OCR secundaria.
