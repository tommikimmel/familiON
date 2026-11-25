# T45 — style(frontend): Perfil, Auth, Movimientos, Analítica, Cuentas y Notificaciones con el estilo de los mocks

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 7 (fidelidad visual) | 7.2 | `style/frontend-profile-and-rest` | T42 | T43, T44 |

## Leer antes
- `docs/plan/00-reglas-worker.md`, `docs/design/README.md`, `e2e/visual/README.md` (de T42)
- Mocks: `docs/design/mocks/mobile.html` pestaña **Perfil**, `docs/design/mocks/desktop.html` pestaña **Perfil**, `docs/design/mocks/profile-family-settings.html`
- Los mocks tienen marco de presentación (bezel/chrome/controles externos) que **no se replica**.

## Objetivo
1. `/profile`, `/profile/family`, `/profile/security` **idénticas** a los mocks (pestaña Perfil + `profile-family-settings.html`): tema, color de perfil (`accent-dot` presets + `+` color picker), divisas, miembros (`avatar-circle`, `vis-group`/`vis-btn` para visibilidad de hijos), invitaciones.
2. Las pantallas que **no tienen mock propio** (`/auth/*`, `/transactions`, `/analytics`, `/accounts`, `/notifications`) deben adoptar **exactamente el mismo lenguaje visual** de los mocks (tokens, tarjetas `fn-card`, listas `tx-row`, `tx-table` en desktop, `gran-tabs`, `mini-seg`, `pill-tag`, inputs `d-input`, botones `btn-primary`/`btn-soft`, tipografía y espaciados), para que no se note que no tenían mock.

## Archivos permitidos
```
frontend/src/app/features/profile/**
frontend/src/app/features/auth/**
frontend/src/app/features/transactions/**
frontend/src/app/features/analytics/**
frontend/src/app/features/accounts/**
frontend/src/app/features/notifications/**
e2e/visual/pairs.ts            (solo agregar pares de estas pantallas)
```
> No tocar `shared/**` ni `core/**` (son de T42). Si falta un componente compartido, componer localmente en la feature y anotarlo en la PR.

## Especificación
1. **Perfil** (mock pestaña Perfil + `profile-family-settings.html`): replicar estructura, orden de secciones, `accent-dot` (activo con anillo), selector de tema, switches (`mini-switch`), lista de miembros con `avatar-circle` y `vis-group`, chips de divisas con `flag-svg`.
2. **Movimientos** (`/transactions`): lista mobile igual a `tx-list`/`tx-row` del mock (agrupada por día) y tabla desktop igual a `tx-table` + `table-head-bar` + `search-input` del mock desktop.
3. **Analítica**: `gran-tabs`, tarjetas `fn-card`, donut y barras con el mismo estilo que Inicio.
4. **Cuentas, Notificaciones, Auth**: tarjetas, listas, inputs y botones del mock. Auth centrado con `fo-logo` como en el splash del mock (`splash-overlay`).
5. Mantener lógica y tests; solo presentación. Actualizar tests que dependan de estructura/clases.
6. Agregar a `e2e/visual/pairs.ts`: `profile` (mobile + desktop ↔ `/profile` y `/profile/family`). Para las pantallas sin mock, agregar capturas de la app sola (sin par) para revisión de coherencia.

## Criterios de aceptación
- [ ] Perfil: capturas lado a lado en 390×844 y 1440×900, claro y oscuro, **sin diferencias visibles** salvo datos.
- [ ] Resto de pantallas: capturas de la app coherentes con el lenguaje de los mocks (revisar contra Inicio de los mocks).
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Capturas adjuntas en la PR.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
cd ../e2e && npm ci && npm run visual:seed && npm run visual:capture
```

## Commits sugeridos
1. `style(profile): match profile and family settings to mocks`
2. `style(transactions): align movements list and table with mock language`
3. `style(frontend): align analytics, accounts, notifications and auth with mock language`
