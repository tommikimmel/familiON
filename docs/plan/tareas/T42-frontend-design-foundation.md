# T42 — style(frontend): fundación visual igual a los mocks (tokens, shell, UI kit) + herramienta de comparación

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 7 (fidelidad visual) | 7.1 | `style/frontend-design-foundation` | — | — (va sola; T43–T45 dependen de esta) |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/design/README.md` (tabla de mocks y tokens)
- Mocks: `docs/design/mocks/mobile.html` y `docs/design/mocks/desktop.html` (abrir en navegador e inspeccionar)

## Objetivo
Que la "carcasa" de la app sea **visualmente idéntica** a los mocks: paleta, tipografía, radios, sombras, header mobile, toggle Personal/Familia, barra inferior con `+`, sidebar desktop, top bar, chip de divisa, y todos los componentes compartidos. Además, dejar una herramienta reproducible para comparar mock vs app que usarán T43–T45.

> **Importante:** los mocks incluyen un marco de presentación que **NO se replica**: `phone-bezel`, `phone-screen` (solo como contenedor), `notch`, `status-bar`, `browser-frame`, `browser-chrome`, `url-pill`, `win-dots`, `outer-bar`, `outer-controls`, `stage`, `accent-bar`/`topAccentBar` (selector de color de demostración). Solo cuenta la superficie de la app (`.fn-surface` / `.app-shell`).

## Archivos permitidos
```
frontend/src/styles.css
frontend/src/index.html                 (solo fuente/meta si hace falta)
frontend/src/app/core/layout/**
frontend/src/app/core/theme/**
frontend/src/app/shared/**
frontend/package.json, frontend/package-lock.json   (solo si hace falta una fuente; preferir @fontsource/inter ya presente)
e2e/visual/**                            (nuevo)
.gitignore                               (solo agregar e2e/visual/out/)
```

## Especificación

1. **Tokens** en `frontend/src/styles.css`: reemplazar los valores `--fo-*` por los del mock (tabla en `docs/design/README.md`), agregando los que falten con el mismo significado: `--fo-text-2` (= `--fn-text-2`), `--fo-sidebar`, `--fo-chrome`, `--fo-nav-bg`, `--fo-overlay`, `--fo-shadow`, `--fo-off-track`, `--fo-slice-4`, `--fo-slice-5`, y las variantes de acento `--fo-accent-soft`, `--fo-accent-glow`, `--fo-accent-mid`, `--fo-accent-light`, `--fo-accent-contrast`. Mantener los nombres `--fo-*` existentes (otras pantallas los usan) y mapearlos en `@theme`. Tema oscuro con clase `dark` (como hoy) pero con los valores del mock. Si mobile y desktop difieren en un token (p. ej. `--fn-card` oscuro), usar el de mobile por defecto y el de desktop en `@media (min-width: 1024px)`.
2. **ThemeService:** calcular las variantes del acento igual que el mock (`hexToRgba` con las opacidades: soft 11%/16%, glow 20%/28% (desktop 18%/26%), mid 56%, light 28%) y el `accent-fg` según el mock. Presets de color de perfil = lista `ACCENTS` del mock (copiar ids, colores claro/oscuro y `darkFg`).
3. **Tipografía:** Inter 400/500/600/700 con la misma escala de tamaños/pesos/letter-spacing del mock (inspeccionar `hero-amount`, `sec-title`, `tx-title`, `kpi-val`, `page-title`, etc.).
4. **Shell mobile** (replicar del mock: `app-header`, `currency-pill` + `currency-dropdown` + `ipc-row` «A plata de hoy», `scope-bar` + `scope-slider` Personal/Familia, `bottom-nav` + `nav-item` + `fab-slot` + `fab-btn` con su corte visual, `gran-tabs` si aparece en mobile): mismas alturas, paddings, radios, sombras, blur de la nav (`--fn-nav-bg`), animaciones (`anim-in`, `stagger-*`, `bump`) y curva spring.
5. **Shell desktop** (replicar de `desktop.html`: `app-shell`, `sidebar` 226px con `logo-wrap`, `scope-bar`, `new-btn` + `kbd-hint` «N», `nav-list`/`nav-btn`, `sidebar-footer` + `user-chip`; `main-topbar` con `page-title`, `gran-tabs`, `currency-pill`, `icon-btn` campana + `notif-dot`).
6. **UI kit** (`shared/ui`): ajustar `fo-card` (= `fn-card`), `fo-button` (= `btn-primary`/`btn-soft`), `fo-segmented-toggle` (= `mini-seg` + `mini-seg-slider`, y el `scope-slider`), `fo-donut-chart` (= `donut-*`, `legend-*`, centro `donut-center-k/v/p`, colores de slices incluyendo `--fo-slice-4/5`), `fo-toast` (= `toast` + `toast-check`), `fo-bottom-sheet` (= `sheet`, `sheet-handle`, `sheet-backdrop`; desktop = `modal-backdrop` + `modal-box`), `fo-currency-chip` (= `currency-pill` + `flag-svg`), switch (= `mini-switch`/`toggle-track`/`toggle-knob`), `fo-logo` (= mock `logo.html`, toggle ON). Agregar los componentes compartidos que falten y que usen varias pantallas: `pill-tag`, `pill-accent`, `mini-chip`, `progress-track`/`progress-fill`, `sec-head`/`sec-title`/`sec-link`, `cat-pill`, `avatar`. No romper sus APIs (inputs/outputs) existentes; si hace falta un input nuevo, opcional.
7. **Herramienta de comparación** `e2e/visual/` (usar `@playwright/test` ya instalado en `e2e/`):
   - `e2e/visual/seed.ts`: crea un usuario adulto listo para usar contra el stack local (`http://localhost:8088`), reutilizando `e2e/helpers/{auth,mailpit,factory}.ts`, y carga datos de muestra realistas vía BFF: 2 cuentas (Efectivo ARS, Mercado Pago), ~12 movimientos en 5 categorías del mes actual con un ingreso, 1 gasto atribuido a un hijo (invitar y registrar un HIJO), 1 compra en 6 cuotas, 2 gastos fijos, 1 meta en USD con un aporte, 1 vehículo con 2 cargas de combustible y 1 service. Guarda las credenciales en `e2e/visual/out/session.json`.
   - `e2e/visual/capture.ts`: para cada par **(mock, pestaña del mock) ↔ (ruta de la app)** de una tabla editable (`e2e/visual/pairs.ts`), captura ambos en **390×844** y **1440×900**, en **claro y oscuro**, y genera `e2e/visual/out/<pair>-<viewport>-<theme>.png` con mock y app **lado a lado**. Para el mock, ocultar el marco de presentación (inyectar CSS que haga `display:none` de bezel/chrome/outer-controls y que el `.fn-surface` ocupe el viewport) y hacer click en la pestaña (`[data-tab="home"|"plan"|"vehicles"|"profile"]`) y en el tema (`data-theme`). Para la app, loguearse con `session.json`.
   - Scripts en `e2e/package.json`: `visual:seed`, `visual:capture`. `e2e/visual/README.md` con instrucciones.
   - `e2e/visual/out/` en `.gitignore`.
8. **Pares iniciales** en `pairs.ts` (las demás tareas agregan los suyos): `shell-home` (mock `mobile.html` tab `home` ↔ `/home`; `desktop.html` tab `home` ↔ `/home`).

## Fuera de alcance
Contenido interno de cada pantalla (Inicio, Plan, Autos, Perfil, etc.) → T43, T44, T45. Solo la carcasa y componentes compartidos.

## Criterios de aceptación
- [ ] Tokens, tipografía y acentos iguales al mock (verificado inspeccionando valores computados en ambos temas).
- [ ] Header, toggle, bottom nav + `+`, sidebar y top bar **visualmente idénticos** al mock en 390×844 y 1440×900, claro y oscuro (capturas lado a lado revisadas; diferencias residuales listadas en la PR con justificación).
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde (actualizar tests de shell/UI kit que dependan de clases).
- [ ] `e2e/visual` funciona: `npm run visual:seed && npm run visual:capture` genera las imágenes.
- [ ] Capturas lado a lado del shell adjuntas en la PR (subirlas como comentario de la PR o enlazarlas).

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
# stack local (si no está arriba):
cd .. && JWT_SECRET=$(openssl rand -base64 48) docker compose --env-file .env.example up -d --build --wait
cd e2e && npm ci && npx playwright install chromium && npm run visual:seed && npm run visual:capture
```

## Commits sugeridos
1. `style(frontend): align design tokens, typography and accent variants with mocks`
2. `style(shell): match mobile header, scope toggle and bottom nav to mocks`
3. `style(shell): match desktop sidebar and top bar to mocks`
4. `style(ui): align shared ui kit components with mocks`
5. `test(visual): add mock vs app side-by-side capture tooling`
