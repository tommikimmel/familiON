# T44 — style(frontend): Plan y Autos idénticos a los mocks

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 7 (fidelidad visual) | 7.2 | `style/frontend-plan-vehicles` | T42 | T43, T45 |

## Leer antes
- `docs/plan/00-reglas-worker.md`, `docs/design/README.md`, `e2e/visual/README.md` (de T42)
- Mocks: `docs/design/mocks/mobile.html` pestañas **Plan** y **Autos**; `docs/design/mocks/desktop.html` pestañas **Plan** y **Autos**; `docs/design/mocks/installments.html`
- Los mocks tienen marco de presentación (bezel/chrome/controles externos) que **no se replica**.

## Objetivo
Pantallas de planificación (`/plan` y sus subrutas `installments`, `recurring`, `goals`, `exchanges`, `allowances`) y de vehículos (`/vehicles`, `/vehicles/:id`, formularios de combustible y service) **visualmente idénticas** a los mocks, usando los tokens y componentes de T42.

## Archivos permitidos
```
frontend/src/app/features/plan/**
frontend/src/app/features/vehicles/**
e2e/visual/pairs.ts            (solo agregar pares plan/vehicles)
```
> No tocar `shared/**` ni `core/**` (son de T42). Si falta un componente compartido, componer localmente en la feature y anotarlo en la PR.

## Especificación (replicar del mock, inspeccionando valores exactos)
1. **Plan** (tab Plan en mobile y desktop): replicar las tarjetas/listas tal como aparecen (`m-card`, `m-head`, `m-list`, `m-item`, `m-badge`, `pill-tag`, `pill-accent`, `progress-track`/`progress-fill`, `mini-chip`, `two-col` en desktop, `main-grid`). Aplicar ese mismo lenguaje visual a la pantalla resumen `/plan` y a las subrutas (cuotas, gastos fijos, metas, divisas, mensualidades), que deben verse como continuaciones coherentes de las tarjetas del mock (mismas tarjetas, listas, badges, barras de progreso, tipografía y espaciados).
2. **Cuotas**: cronograma y tarjetas coherentes con `installments.html` (resumen «6 cuotas de $X/mes · Total $Y (Mes → Mes)», recargo, licuación).
3. **Autos** (tab Autos en mobile y desktop): ficha del vehículo y métricas (`veh-stats`, `veh-stat-box`, `veh-stat-k`, `veh-stat-v`), alertas, últimas cargas/services, botones de acción (`action-row`, `btn-primary`, `btn-soft`). Los formularios de combustible y service usan los mismos inputs/botones del mock (`d-input`, `btn-primary`).
4. Mantener lógica y tests; solo presentación. Actualizar tests que dependan de estructura/clases.
5. Agregar a `e2e/visual/pairs.ts`: `plan` (mobile + desktop ↔ `/plan`) y `vehicles` (mobile + desktop ↔ `/vehicles/:id` del vehículo sembrado).

## Criterios de aceptación
- [ ] Capturas lado a lado de Plan y Autos en 390×844 y 1440×900, claro y oscuro, **sin diferencias visibles** salvo datos; subrutas coherentes con el mismo lenguaje visual. Diferencias residuales justificadas en la PR.
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Capturas adjuntas en la PR.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
cd ../e2e && npm ci && npm run visual:seed && npm run visual:capture
```

## Commits sugeridos
1. `style(plan): match plan overview and subroutes to mocks`
2. `style(vehicles): match vehicle screens and forms to mocks`
