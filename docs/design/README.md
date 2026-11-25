# FamiliON — Mocks de diseño (fuente de verdad visual)

Los mocks HTML de esta carpeta son la **referencia visual obligatoria** del frontend. La app debe verse **igual** que estos mocks en mobile y desktop, en tema claro y oscuro, con cualquier color de perfil.

| Archivo | Qué define | Viewport de referencia |
|---|---|---|
| [`mocks/mobile.html`](./mocks/mobile.html) | App mobile completa con animaciones: header, toggle Personal/Familia, hero de balance, donut Categorías/Personas, movimientos, barra inferior con `+`, pestañas Inicio / Plan / Autos / Perfil | 390 × 844 |
| [`mocks/desktop.html`](./mocks/desktop.html) | App desktop: sidebar 226px, top bar, grilla de KPIs, gráficos, tabla de movimientos, pestañas Inicio / Plan / Autos / Perfil | 1440 × 900 |
| [`mocks/quick-add.html`](./mocks/quick-add.html) | Carga rápida `+` (sheet mobile y modal desktop), teclado, chips de categoría, «Más opciones» | 390 × 844 y 1440 × 900 |
| [`mocks/installments.html`](./mocks/installments.html) | UX de cuotas: «Es el total / Es cada cuota», con/sin interés y resumen en vivo | 390 × 844 |
| [`mocks/profile-family-settings.html`](./mocks/profile-family-settings.html) | Perfil y configuración de familia (tema, color de perfil, divisas, miembros) | 390 × 844 |
| [`mocks/logo.html`](./mocks/logo.html) | Logo `FamiliON` con toggle `ON` y animación | — |

## Tokens de diseño de los mocks

Los mocks usan variables `--fn-*` y `--familion-accent*`. En la app se implementan como tokens `--fo-*` (ver `frontend/src/styles.css`) con **los mismos valores**:

| Mock | Claro | Oscuro |
|---|---|---|
| `--fn-bg` | `#f8f9fb` | `#0b0d11` |
| `--fn-card` | `#ffffff` | `#13171e` (mobile) / `#141922` (desktop) |
| `--fn-card-alt` | `#f1f3f6` | `#1b212b` / `#1c2330` |
| `--fn-border` | `#e5e7eb` | `#242c38` / `#232c3a` |
| `--fn-text` | `#111827` | `#f3f4f6` |
| `--fn-text-2` | `#4b5563` | `#cbd5e1` |
| `--fn-muted` | `#6b7280` | `#8b95a5` |
| `--fn-sidebar` (desktop) | `#ffffff` | `#10141b` |
| acento | `--familion-accent`, `--familion-accent-fg`, `--fn-accent-soft` (11% claro / 16% oscuro), `--fn-accent-glow` (20% / 28%), `--fn-accent-mid` (56%), `--fn-accent-light` (28%) | |

Tipografía: **Inter** 400/500/600/700. Para el detalle exacto (radios, sombras, espaciados, animaciones), abrir el mock en el navegador e inspeccionar.

## Cómo comparar

Ver `e2e/visual/README.md` (lo crea T42): captura el mock y la pantalla equivalente de la app en ambos viewports y temas, y deja las imágenes lado a lado en `e2e/visual/out/` para revisarlas.
