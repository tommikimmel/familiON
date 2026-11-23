# T05 — chore(ci): pipelines GitHub Actions + higiene del repo

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 0 | 0.2 | `feat/ci-cd-workflows` | Ola 0.1 (T01, T02, T03) | T04, T06 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Esta tarjeta.

## Objetivo
CI en cada PR a `develop`/`main`, publicación de imágenes `:staging` al mergear en `develop`, y release con changelog e imágenes versionadas al pushear un tag `vX.Y.Z`. Notificación por email si hay secretos SMTP configurados.

## Archivos permitidos
```
.github/workflows/ci.yml
.github/workflows/cd-staging.yml
.github/workflows/cd-release.yml
.github/pull_request_template.md
.github/dependabot.yml
.github/release.yml
.editorconfig
```

## Contexto mínimo
- Proyectos: `backend/` (Maven wrapper, Java 21, tests con Testcontainers → necesitan Docker, disponible en `ubuntu-latest`), `bff/` (igual; puede no existir aún si T04 no está mergeado), `frontend/` (npm; scripts `lint`, `test`, `build`).
- Compose: `docker-compose.yml` + `.env.example` en la raíz.
- Registry: `ghcr.io/${{ github.repository_owner }}/familion-{backend,bff,frontend}` (en minúsculas).

## Especificación

1. **`ci.yml`** — `on: pull_request (branches: develop, main)` y `push (branches: develop, main)`. `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }`. `permissions: contents: read`.
   - Job `changes`: `dorny/paths-filter@v3` → outputs `backend`, `bff`, `frontend`, `infra` (`docker-compose*.yml`, `edge/**`, `.github/**`).
   - Job `backend` (si `backend == 'true'`): `actions/setup-java@v4` (temurin 21, `cache: maven`), `cd backend && ./mvnw -B verify`. Subir reportes surefire/failsafe como artifact si falla.
   - Job `bff` (si `bff == 'true'` y existe `bff/pom.xml`): igual en `bff/`.
   - Job `frontend` (si `frontend == 'true'`): `actions/setup-node@v4` (node 24, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`), `npm ci`, `npm run lint`, `npm test`, `npm run build -- --configuration production`.
   - Job `docker` (needs los anteriores, `if: always() && !failure() && !cancelled()`): `cp .env.example .env`, `docker compose config -q`, `docker compose build` (usar `docker/setup-buildx-action@v3`).
   - Job `notify-failure` (`if: failure()`, needs todos): envía email con `dawidd6/action-send-mail@v3` **solo si** el secreto `SMTP_HOST` existe (patrón: `env: SMTP_HOST: ${{ secrets.SMTP_HOST }}` y `if: env.SMTP_HOST != ''` a nivel de step). Asunto `❌ FamiliON CI failed on ${{ github.ref_name }}`, cuerpo con link `${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}`. Secretos: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `CI_NOTIFY_EMAIL`.
2. **`cd-staging.yml`** — `on: push: branches: [develop]` + `workflow_dispatch`. `permissions: contents: read, packages: write`.
   - Matriz `app: [backend, bff, frontend]` (saltear si no existe `${app}/Dockerfile`): `docker/login-action@v3` a `ghcr.io` con `GITHUB_TOKEN`, `docker/metadata-action@v5` (tags `staging` y `sha-<short>`), `docker/build-push-action@v6` con cache `type=gha`.
   - Job `deploy-staging` (needs build): `if: vars.STAGING_DEPLOY_ENABLED == 'true'`; placeholder documentado que corre `ssh` a `${{ secrets.STAGING_SSH_HOST }}` ejecutando `docker compose pull && docker compose up -d` (usar `appleboy/ssh-action@v1`).
   - Job `notify`: email "🧪 Staging updated" con los commits del push (`github.event.commits`), condicionado a `SMTP_HOST`.
3. **`cd-release.yml`** — `on: push: tags: ['v*.*.*']`. `permissions: contents: write, packages: write`.
   - Job `release`: checkout con `fetch-depth: 0`; generar notas con `gh release create ${{ github.ref_name }} --generate-notes --verify-tag` (usa `.github/release.yml` para agrupar por labels).
   - Job `images`: matriz como staging con tags `${{ github.ref_name }}` (sin `v` también, vía metadata-action `type=semver,pattern={{version}}`) y `latest`.
   - Job `deploy-production`: `if: vars.PROD_DEPLOY_ENABLED == 'true'`, `environment: production`; pasos placeholder: backup previo de PostgreSQL (`pg_dump` vía ssh) → `docker compose pull` → `up -d` → verificar `GET /actuator/health` (vía `docker compose exec`).
   - Job `notify`: "🚀 FamiliON ${{ github.ref_name }} released" con link a la release.
4. **`.github/release.yml`**: categorías `Features` (labels `feat`, `enhancement`), `Fixes` (`fix`, `bug`), `Docs` (`docs`), `Maintenance` (`chore`, `ci`, `refactor`), `Other` (`*`).
5. **`.github/pull_request_template.md`**: secciones `## Task`, `## Summary`, `## How to verify`, `## Acceptance criteria`, `## Out of scope / follow-ups` (mismo template de las reglas de workers).
6. **`.github/dependabot.yml`**: `maven` en `/backend` y `/bff`, `npm` en `/frontend`, `github-actions` en `/`, `docker` en `/backend`, `/bff`, `/frontend`; `schedule: weekly`, `target-branch: develop`, `open-pull-requests-limit: 5`, prefijo de commit `chore(deps)`.
7. **`.editorconfig`**: `root = true`; `[*]` utf-8, lf, `insert_final_newline`, `trim_trailing_whitespace`, indent 2 espacios; `[*.{java,xml}]` indent 4; `[*.md]` `trim_trailing_whitespace = false`; `[Makefile]` `indent_style = tab`.

## Fuera de alcance
Configurar secretos/variables en GitHub (lo hace el dueño del repo), servidores de staging/producción, tests E2E (T38).

## Criterios de aceptación
- [ ] `actionlint` sin errores sobre los 3 workflows.
- [ ] Los jobs se saltean limpiamente cuando una carpeta no existe o no cambió.
- [ ] Ningún secreto hardcodeado; todo por `secrets.*` / `vars.*`.
- [ ] El workflow `ci.yml` de esta misma PR corre en GitHub (verificar en la pestaña Checks de la PR).

## Verificación
```bash
docker run --rm -v "$PWD:/repo" --workdir /repo rhysd/actionlint:latest -color
```

## Commits sugeridos
1. `ci: add continuous integration workflow with path filters`
2. `ci: add staging and release delivery workflows`
3. `chore(repo): add pr template, dependabot and editorconfig`
