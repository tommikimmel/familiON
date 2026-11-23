# T03 — feat(infra): docker compose + edge Nginx + entorno local

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 0 | 0.1 | `feat/infra-compose-edge` | — | T01, T02 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- (Opcional, ya resumido acá) `docs/09-seguridad-edge-bff-y-cache.md` §2.2.

## Objetivo
Levantar todo el stack con un comando (`make up`), con aislamiento de red y un reverse proxy Nginx endurecido (rate limit, anti-slowloris, headers de seguridad). Los Dockerfiles de `backend/`, `bff/` y `frontend/` los crean otras tareas: acá solo se referencian.

## Archivos permitidos
```
docker-compose.yml
docker-compose.override.yml
.env.example
edge/**
Makefile
README.md            (raíz del repo, nuevo)
.gitignore           (raíz del repo, nuevo)
```

## Especificación

1. **`docker-compose.yml`** (sin `version:`), `name: familion`:

| Servicio | Config clave |
|---|---|
| `postgres` | `postgres:17-alpine`; env `POSTGRES_DB/USER/PASSWORD` desde `.env`; volumen `pgdata:/var/lib/postgresql/data`; healthcheck `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}`; red `internal_net`. |
| `redis` | `redis:7-alpine`; `command: ["redis-server","--maxmemory","128mb","--maxmemory-policy","allkeys-lru","--save","","--appendonly","no"]`; healthcheck `redis-cli ping`; `internal_net`. |
| `mailpit` | `axllent/mailpit:latest`; puertos `8025:8025`; `internal_net`. |
| `backend` | `build: ./backend`; env: `SPRING_PROFILES_ACTIVE`, `DB_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST=redis`, `REDIS_PORT=6379`, `JWT_SECRET`, `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`, `MAIL_HOST=mailpit`, `MAIL_PORT=1025`, `MAIL_FROM`, `APP_PUBLIC_URL`, `APP_VERSION`, `ECON_DOLARAPI_URL`, `ECON_ARGDATOS_URL`, `ECON_SYNC_CRON`, `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`, `RECEIPTS_STORAGE_DIR=/data/receipts`; volumen `receipts:/data/receipts`; `depends_on` postgres/redis `service_healthy`, mailpit `service_started`; healthcheck `wget -qO- http://localhost:8081/actuator/health/readiness`; `internal_net`; **sin `ports`**. |
| `bff` | `build: ./bff`; env `CORE_API_URL=http://backend:8081`, `REDIS_HOST=redis`, `REDIS_PORT=6379`, `COOKIE_SECURE`, `BFF_RATE_LIMIT_PER_MIN`; `depends_on` backend `service_healthy`; healthcheck `wget -qO- http://localhost:8080/actuator/health`; `internal_net`; sin `ports`. |
| `frontend` | `build: ./frontend`; `internal_net`; sin `ports`. |
| `edge` | `nginx:1.27-alpine`; `ports: ["8088:80"]`; volumen `./edge/nginx.conf:/etc/nginx/nginx.conf:ro`; `depends_on` bff (healthy) y frontend (started); redes `public_net` e `internal_net`. |

   Redes: `public_net` (bridge) e `internal_net` (bridge). Todos los servicios con `restart: unless-stopped`.
2. **`docker-compose.override.yml`** (dev, se carga automáticamente): expone `postgres` `5432:5432`, `bff` `8080:8080`, `backend` `8081:8081`, `redis` `6379:6379` para desarrollo y debugging local.
3. **`.env.example`** con todas las variables anteriores y valores dev: `POSTGRES_DB=familion`, `POSTGRES_USER=familion`, `POSTGRES_PASSWORD=familion`, `SPRING_PROFILES_ACTIVE=dev`, `JWT_SECRET=` (comentario: generar con `openssl rand -base64 48`), `JWT_ACCESS_TTL=PT15M`, `JWT_REFRESH_TTL=P30D`, `MAIL_FROM=no-reply@familion.local`, `APP_PUBLIC_URL=http://localhost:8088`, `APP_VERSION=0.1.0`, `ECON_DOLARAPI_URL=https://dolarapi.com`, `ECON_ARGDATOS_URL=https://api.argentinadatos.com`, `ECON_SYNC_CRON=0 0 */6 * * *`, `VAPID_PUBLIC_KEY=`, `VAPID_PRIVATE_KEY=`, `VAPID_SUBJECT=mailto:admin@familion.local`, `COOKIE_SECURE=false`, `BFF_RATE_LIMIT_PER_MIN=120`.
4. **`edge/nginx.conf`** (archivo completo `nginx.conf`, no `conf.d`):
   - `worker_processes auto; events { worker_connections 1024; }`, `server_tokens off;`.
   - `resolver 127.0.0.11 valid=30s;` y upstreams `bff:8080` y `frontend:80`.
   - Rate limit: `limit_req_zone $binary_remote_addr zone=auth:10m rate=5r/m;` `limit_req_zone $binary_remote_addr zone=api:10m rate=60r/m;` `limit_conn_zone $binary_remote_addr zone=conn:10m;` `limit_req_status 429; limit_conn_status 429;`.
   - Timeouts: `client_body_timeout 10s; client_header_timeout 10s; keepalive_timeout 15s; send_timeout 10s;` `client_max_body_size 1m;`.
   - `map $http_user_agent $bad_ua { default 0; "" 1; ~*(curl|wget|python-requests|scrapy|httpclient|go-http-client) 1; }` → `if ($bad_ua) { return 403; }` **solo** en `location /bff/` (no bloquear healthchecks internos).
   - Métodos: `if ($request_method !~ ^(GET|HEAD|POST|PUT|PATCH|DELETE)$) { return 405; }`.
   - Headers (en `server`, con `always`): `Strict-Transport-Security "max-age=31536000; includeSubDomains"`, `X-Content-Type-Options nosniff`, `X-Frame-Options DENY`, `Referrer-Policy strict-origin-when-cross-origin`, `Permissions-Policy "camera=(self), geolocation=(), microphone=()"`, `Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; manifest-src 'self'; worker-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"`.
   - Locations:
     - `location ~ ^/bff/auth/(login|register|register-invite|forgot-password|reset-password|verify-email|2fa/) { limit_req zone=auth burst=3 nodelay; limit_conn conn 20; proxy_pass http://bff_upstream; }`
     - `location = /bff/api/receipts/scan { client_max_body_size 6m; limit_req zone=api burst=20; proxy_pass ...; }`
     - `location /bff/ { limit_req zone=api burst=20; limit_conn conn 20; proxy_pass http://bff_upstream; }`
     - `location / { proxy_pass http://frontend_upstream; }`
     - `location = /healthz { access_log off; return 200 "ok"; }`
   - Proxy headers: `Host`, `X-Real-IP`, `X-Forwarded-For $proxy_add_x_forwarded_for`, `X-Forwarded-Proto $scheme`; `proxy_http_version 1.1`.
   - **Nota sobre burst de auth:** 5 req/min con `burst=3 nodelay` (según doc 09).
5. **`edge/README.md`**: qué protege cada directiva y cómo probar el rate limit (`for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code}\n" -X POST -A "Mozilla/5.0" localhost:8088/bff/auth/login; done` → aparecen `429`).
6. **`Makefile`** (targets con `.PHONY`): `up` (`docker compose up -d --build`), `down`, `logs` (`docker compose logs -f --tail=100`), `ps`, `build`, `reset-db` (down + borrar volumen `pgdata` con confirmación), `db-shell` (`docker compose exec postgres psql -U familion familion`), `backend-test` (`cd backend && ./mvnw -B verify`), `bff-test`, `frontend-test`, `env` (copia `.env.example` a `.env` si no existe y genera `JWT_SECRET` con `openssl rand -base64 48`).
7. **`README.md` raíz**: qué es FamiliON (2 líneas), estructura del monorepo, quickstart (`make env && make up`, URLs: app `http://localhost:8088`, Mailpit `http://localhost:8025`, Swagger vía `make` con override `http://localhost:8081/swagger-ui.html`), flujo de ramas (`feature/*` → PR a `develop` → `main` con tag), link a `docs/README.md` y `docs/plan/README.md`.
8. **`.gitignore` raíz**: `.env`, `node_modules/`, `dist/`, `target/`, `.angular/`, `*.log`, `.idea/`, `.vscode/` (excepto `extensions.json`), `.DS_Store`, `coverage/`, `playwright-report/`, `test-results/`.

## Fuera de alcance
Dockerfiles de cada app (T01, T02, T04), CI (T05), TLS/Cloudflare (producción).

## Criterios de aceptación
- [ ] `cp .env.example .env && docker compose config -q` sin errores.
- [ ] `nginx -t` válido (ver verificación).
- [ ] `postgres`, `redis` y `mailpit` levantan healthy con `docker compose up -d postgres redis mailpit`.
- [ ] Ningún servicio salvo `edge` y `mailpit` publica puertos en `docker-compose.yml` (el override sí, solo para dev).

## Verificación
```bash
cp .env.example .env && docker compose config -q && echo COMPOSE_OK
docker run --rm --add-host bff:127.0.0.1 --add-host frontend:127.0.0.1 \
  -v "$PWD/edge/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:1.27-alpine nginx -t
docker compose up -d postgres redis mailpit && sleep 10 && docker compose ps && docker compose down
rm .env
```

## Commits sugeridos
1. `chore(infra): add docker compose stack with isolated networks`
2. `feat(edge): add hardened nginx reverse proxy with rate limiting`
3. `docs(infra): add root readme, makefile and env example`
