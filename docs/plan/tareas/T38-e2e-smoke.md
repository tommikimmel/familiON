# T38 — test(e2e): smoke tests Playwright sobre el stack completo + workflow de CI

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 5 | 5.1 | `feat/e2e-smoke-tests` | Fase 4 completa | T36, T37 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Raíz: `docker-compose.yml`, `docker-compose.override.yml`, `.env.example`, `Makefile`, `README.md` (T03).

## Objetivo
Probar de punta a punta (navegador → edge → BFF → Core → Postgres/Mailpit) los flujos críticos, para detectar roturas entre capas antes de cada release.

## Archivos permitidos
```
e2e/**
.github/workflows/e2e.yml
```

## Especificación
1. **Proyecto `e2e/`** (TypeScript): `@playwright/test`, `playwright.config.ts` con `baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:8088'`, proyectos `chromium-mobile` (Pixel 7) y `chromium-desktop`, `retries: 1` en CI, traces `on-first-retry`, reporter `html` + `list`. Scripts `test`, `test:headed`, `install-browsers`.
2. **Helpers:**
   - `mailpit.ts`: leer correos vía API de Mailpit (`http://localhost:8025/api/v1/messages`, `/api/v1/message/{ID}`), esperar con reintentos el último mail a un destinatario y extraer el código de 6 dígitos o el link.
   - `factory.ts`: emails únicos por test (`e2e+{timestamp}@familion.local`).
3. **Tests (`e2e/tests/`):**
   - `auth.spec.ts`: registro → verificación de email con código de Mailpit → login → 2FA con código de Mailpit y «Confiar en este dispositivo» → llega a `/home`; logout → login sin 2FA (dispositivo confiable).
   - `quick-add.spec.ts`: crear cuenta Efectivo ARS en `/accounts`; abrir `+`, cargar `1500` con categoría «Supermercado», Guardar → toast → el movimiento aparece en Inicio y en `/transactions`.
   - `family.spec.ts`: adulto invita a un hijo → abrir `inviteUrl` en otro contexto → registrar hijo → el hijo no ve el toggle «Familia» (visibilidad `OWN_ONLY`).
   - `security-headers.spec.ts`: la respuesta de `/` incluye `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`; ningún request del navegador va a `:8081`.
   - `pwa.spec.ts`: `manifest.webmanifest` válido (name, icons 192/512) y service worker registrado en build de producción.
4. **Workflow `.github/workflows/e2e.yml`:** `on: pull_request (branches: [main])`, `workflow_dispatch` y `schedule` diario 06:00 UTC. Pasos: checkout → `cp .env.example .env` + `JWT_SECRET` generado con `openssl rand -base64 48` → `docker compose up -d --build --wait` (timeout 10 min) → `npm ci` + `npx playwright install --with-deps chromium` en `e2e/` → `npx playwright test` → subir `playwright-report` como artifact siempre → `docker compose logs` si falla → `docker compose down -v`.
5. `e2e/README.md`: cómo correr local (`make up` y `cd e2e && npm ci && npm test`).

## Criterios de aceptación
- [ ] `cd e2e && npm ci && npx tsc --noEmit` sin errores.
- [ ] Con el stack levantado (`make up`), `npm test` pasa en ambos proyectos (si algún flujo falla por un bug de otra capa, dejar el test marcado `test.fail()` con link a un issue y explicarlo en la PR).
- [ ] `actionlint` sin errores en `e2e.yml`.

## Verificación
```bash
make env && make up
cd e2e && npm ci && npx playwright install chromium && npx tsc --noEmit && npm test
cd .. && docker run --rm -v "$PWD:/repo" --workdir /repo rhysd/actionlint:latest -color .github/workflows/e2e.yml
make down
```

## Commits sugeridos
1. `test(e2e): add playwright project with mailpit helpers`
2. `test(e2e): add auth, quick add, family and hardening smoke tests`
3. `ci: add nightly and pre-release e2e workflow`
