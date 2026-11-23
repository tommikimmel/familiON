# FamiliON BFF

Backend for Frontend (BFF) for the FamiliON PWA. Acts as the single HTTP entry point from the browser:

- Stores Core API JWTs in `HttpOnly`, `SameSite=Strict` cookies (`FAMILION_AT`, `FAMILION_RT`, `FAMILION_DT`) so tokens are never exposed to JavaScript.
- Enforces double-submit cookie CSRF protection (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header) on all non-GET requests under `/bff/**`.
- Proxies `/bff/api/**` to Core API `/api/v1/**` with `Authorization: Bearer <FAMILION_AT>` and `X-Forwarded-For`, stripping browser cookies.
- Applies fail-open Redis-backed rate limiting (Bucket4j + Lettuce) per user (`sub`) or client IP.
- Exposes `CoreClient` and `ParallelCalls` helpers for downstream screen aggregation endpoints.

## Configuration

| Environment Variable | Default | Description |
|---|---|---|
| `CORE_API_URL` | `http://localhost:8081` | Base URL of the FamiliON Core API |
| `COOKIE_SECURE` | `false` | Sets the `Secure` attribute on BFF cookies (`true` in HTTPS environments) |
| `BFF_RATE_LIMIT_PER_MIN` | `120` | Requests per minute per user or IP |
| `REDIS_HOST` | `localhost` | Redis host for Bucket4j rate limiting |
| `REDIS_PORT` | `6379` | Redis port |

## Local Development

```bash
./mvnw spring-boot:run
```

## Verification

```bash
./mvnw -B verify
docker build -t familion-bff .
```
