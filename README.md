# FamiliON

FamiliON is a personal and family finance platform built for high-inflation, fluctuating economies (such as Argentina).
It combines household finances, allowance-based financial education for children, and family vehicle tracking.

## Monorepo structure

```
backend/     Core API (Java 21 + Spring Boot 3, Maven) — internal only
bff/         Backend for Frontend (Java 21 + Spring Boot 3) — HttpOnly cookies, aggregation
frontend/    Angular PWA + Tailwind CSS
edge/        Hardened Nginx reverse proxy (single public entry point)
docs/        Product, architecture and implementation documentation
docker-compose.yml           Full stack (postgres, redis, mailpit, backend, bff, frontend, edge)
docker-compose.override.yml  Dev-only: publishes internal ports for local debugging
```

## Quickstart

Requirements: Docker with Compose v2, GNU Make, OpenSSL.

```bash
make env   # creates .env from .env.example and generates JWT_SECRET
make up    # builds and starts the whole stack
```

| URL | What |
|---|---|
| http://localhost:8088 | App (through the edge proxy) |
| http://localhost:8025 | Mailpit (captured emails) |
| http://localhost:8081/swagger-ui.html | Core API Swagger UI (dev only, port published by `docker-compose.override.yml`) |

Other targets: `make down`, `make logs`, `make ps`, `make build`, `make reset-db`, `make db-shell`,
`make backend-test`, `make bff-test`, `make frontend-test`. Run `make` to list them all.

## Branching

- Work on `feature/*` (or `feat/*`, `fix/*`, ...) branches created from `develop`.
- Open a pull request to `develop`.
- `develop` is merged into `main` for releases, which are tagged (SemVer).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## Documentation

- [Project documentation](docs/README.md)
- [Implementation plan](docs/plan/README.md)
- [Edge proxy hardening](edge/README.md)
