# T46 — style(auth): pantallas de acceso con el lenguaje visual de los mocks

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 7 (fidelidad visual) | 7.2 | `style/frontend-auth` | T42 | T43, T44 |

## Leer antes
- `docs/plan/00-reglas-worker.md`, `docs/design/README.md`, `e2e/visual/README.md`
- Mocks: `docs/design/mocks/mobile.html` (splash `splash-overlay` + `logo-anim`, `fn-card`, botones, inputs, toast) y `docs/design/mocks/desktop.html` (`modal-box`, `d-input`, `btn-primary`). Los mocks tienen marco de presentación (bezel/chrome/controles) que **no se replica**.
- Componentes y tokens ya disponibles de T42: `frontend/src/styles.css`, `frontend/src/app/shared/**`, `frontend/src/app/core/layout/auth-layout.component.ts`.

## Problema
Las pantallas de acceso (`/auth/login`, `/auth/register`, `/auth/verify-email`, `/auth/2fa`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/invite`) siguen con el estilo del scaffold: no usan la composición, tipografía, inputs, botones ni el logo animado de los mocks. El usuario las ve iguales que antes.

## Objetivo
Que todas las pantallas de acceso se vean como parte de la app de los mocks: mismo fondo (`--fo-bg`), logo `FamiliON` con toggle `ON` y su animación de entrada (como el splash del mock), tarjeta (`fn-card`) con los mismos radios/sombras/bordes, inputs con el estilo `d-input` del mock (altura, radio, borde, foco con el color de acento), botón principal `btn-primary` (alto, radio, peso de fuente, color de acento y `accent-fg`), botón secundario `btn-soft`, links en color de acento, textos con la escala tipográfica del mock (`--fo-text`, `--fo-text-2`, `--fo-muted`), toggle de mostrar contraseña con íconos `eye`/`eye-off`, cajas de código OTP con el mismo lenguaje, toasts del mock. Mobile-first (390px) y desktop centrado (tarjeta tipo `modal-box`, ancho máx ~400px). Claro y oscuro.

## Archivos permitidos
```
frontend/src/app/features/auth/**
frontend/src/app/core/layout/auth-layout.component.ts
e2e/visual/pairs.ts            (solo agregar capturas de auth)
```
> No tocar `shared/**` ni `core/` salvo `auth-layout.component.ts`. Si falta un componente compartido, componer localmente y anotarlo en la PR.

## Especificación
1. `AuthLayoutComponent`: fondo `--fo-bg`, contenido centrado vertical/horizontal, `fo-logo` grande con animación OFF→ON al entrar (respetar `prefers-reduced-motion`), subtítulo tipo `splash-sub` («Las finanzas de tu familia, sin inflación» o texto equivalente), tarjeta con el formulario.
2. Formularios: labels arriba, inputs `d-input`, mensajes de error en rojo del sistema (`--fo-danger`) bajo cada campo, banner de error general con el estilo de `pill-tag`/tarjeta del mock, botón principal de ancho completo con estado `loading`.
3. Pantallas: login (email, contraseña con ojo, «¿Olvidaste tu contraseña?», «Ingresar», «Crear cuenta familiar»), registro, verificación de email (6 cajas OTP), 2FA (6 cajas + «Confiar en este dispositivo» con el switch del mock + reenviar con cuenta regresiva), olvido, reset, invitación.
4. Mantener lógica y tests; solo presentación. Actualizar tests que dependan de estructura/clases.
5. Agregar a `e2e/visual/pairs.ts` capturas de app (sin par de mock) de `/auth/login` y `/auth/register` en 390×844 y 1440×900, claro y oscuro, y compararlas visualmente con el lenguaje del mock (Inicio/splash).

## Criterios de aceptación
- [ ] Las 7 pantallas de acceso usan tokens, tipografía, inputs, botones y logo animado del mock; a simple vista pertenecen a la misma app que los mocks, en mobile y desktop, claro y oscuro.
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Capturas generadas y listadas en la PR.

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
cd ../e2e && npm ci && npm run visual:capture
```

## Commits sugeridos
1. `style(auth): match auth layout with animated logo and mock surfaces`
2. `style(auth): restyle auth forms with mock inputs, buttons and otp boxes`
