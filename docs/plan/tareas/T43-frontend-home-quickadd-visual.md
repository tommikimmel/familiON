# T43 — style(frontend): Inicio y Carga rápida idénticos a los mocks

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 7 (fidelidad visual) | 7.2 | `style/frontend-home-quickadd` | T42 | T44, T45 |

## Leer antes
- `docs/plan/00-reglas-worker.md`, `docs/design/README.md`, `e2e/visual/README.md` (de T42)
- Mocks: `docs/design/mocks/mobile.html` (pestaña **Inicio**), `docs/design/mocks/desktop.html` (pestaña **Inicio**), `docs/design/mocks/quick-add.html`, `docs/design/mocks/installments.html`
- Los mocks tienen marco de presentación (bezel/chrome/controles externos) que **no se replica**.

## Objetivo
Pantalla `/home` (mobile y desktop) y la carga rápida `+` (sheet mobile y modal desktop, incluida la sección de cuotas) **visualmente idénticas** a los mocks, usando los tokens y componentes de T42.

## Archivos permitidos
```
frontend/src/app/features/home/**
frontend/src/app/features/quick-add/**
e2e/visual/pairs.ts            (solo agregar pares home/quick-add)
```
> No tocar `shared/**` ni `core/**` (son de T42). Si falta un componente compartido, componer localmente en la feature y anotarlo en la PR.

## Especificación (replicar del mock, inspeccionando valores exactos)
1. **Inicio mobile** (`mobile.html` tab Inicio): hero de balance (`hero-label`, `hero-amount` con count-up, `hero-metrics` ingresos/gastos/ahorro, glow del acento), tarjeta donut con `mini-seg` «Categorías | Personas», leyenda (`legend-row`, `legend-dot`, `legend-name`, `legend-val`), sección movimientos (`sec-head`, `tx-list`, `tx-row`, `tx-icon`, `tx-title`, `tx-meta`, `tx-amt`, `tx-amt-sub`, animación `tx-new`), toast de guardado. Mismo orden, espaciados, tamaños y animaciones (`anim-in`, `stagger-1..4`).
2. **Inicio desktop** (`desktop.html` tab Inicio): `kpi-grid` con 3 KPIs (`kpi-label`, `kpi-val`, `kpi-sub`), `charts-grid` con `bar-chart` de evolución (`b-col`, `b-rect`, `b-val`, `b-lbl`) + donut, `table-head-bar` con `search-input` y `tx-table` (columnas y `row-new`).
3. **Carga rápida** (`quick-add.html` + sheet de `mobile.html` + `modal-box` de `desktop.html`): `type-switch` Gasto/Ingreso, `qa-top`, `qa-amount-area`/`qa-amount-display` con `bump`, `keypad`/`key-btn` (incluye `000`), `cat-grid`/`cat-pill`, `qa-input` nombre, `more-toggle`/`more-panel`/`more-row`/`more-chips` (Cuenta, Fecha, «Para:», Cuotas), `save-btn`, cámara secundaria.
4. **Cuotas** (`installments.html`): controles «Es el total | Es cada cuota», «Sin interés | Con interés», precio contado y `live-summary` con el mismo texto y formato.
5. Mantener toda la lógica y los tests existentes; solo cambia presentación (templates/estilos). Actualizar tests que dependan de clases o estructura.
6. Agregar a `e2e/visual/pairs.ts`: `home` (mobile + desktop) y `quick-add` (abrir el sheet/modal en ambos).

## Criterios de aceptación
- [ ] Capturas lado a lado (`npm run visual:capture`) de Inicio y Carga rápida en 390×844 y 1440×900, claro y oscuro, **sin diferencias visibles** salvo datos (montos/nombres); diferencias residuales justificadas en la PR.
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Capturas adjuntas en la PR.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
cd ../e2e && npm ci && npm run visual:seed && npm run visual:capture
```

## Commits sugeridos
1. `style(home): match mobile home to mock`
2. `style(home): match desktop dashboard grid to mock`
3. `style(quick-add): match quick add sheet, modal and installments ux to mocks`
