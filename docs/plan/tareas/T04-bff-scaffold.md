# T04 — feat(bff): BFF con cookies HttpOnly, CSRF, proxy al Core y rate limit

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 0 | 0.2 | `feat/bff-scaffold` | Ola 0.1 (T01, T02, T03) | T05, T06 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Esta tarjeta (el contrato completo está copiado abajo).

## Objetivo
Crear `bff/`: único punto de entrada de la PWA. Guarda los JWT del Core en cookies `HttpOnly`, valida CSRF, reenvía `/bff/api/**` al Core con `Authorization: Bearer`, aplica rate limit por usuario/IP en Redis y expone utilidades (`CoreClient`, `ParallelCalls`) para las agregaciones de T21/T29/T35.

## Archivos permitidos
```
bff/**
```

## Contrato (no modificar)

**Core → respuesta de tokens** (`/api/v1/auth/login`, `/auth/2fa/verify`, `/auth/refresh`, `/auth/register-invite`):
```json
{ "requires2fa": false, "challengeToken": null, "accessToken": "...", "accessTokenExpiresIn": 900,
  "refreshToken": "...", "refreshTokenExpiresIn": 2592000, "deviceToken": null }
```
Con 2FA pendiente: `{ "requires2fa": true, "challengeToken": "..." }` (resto null).
Core refresh: `POST /api/v1/auth/refresh {"refreshToken"}`. Core logout: `POST /api/v1/auth/logout` con Bearer y `{"refreshToken"}`.

**Cookies emitidas por el BFF:**

| Cookie | Atributos | Contenido |
|---|---|---|
| `FAMILION_AT` | `HttpOnly; Secure=${COOKIE_SECURE}; SameSite=Strict; Path=/bff; Max-Age=accessTokenExpiresIn` | access token |
| `FAMILION_RT` | `HttpOnly; Secure; SameSite=Strict; Path=/bff/auth; Max-Age=refreshTokenExpiresIn` | refresh token |
| `FAMILION_DT` | `HttpOnly; Secure; SameSite=Strict; Path=/bff/auth; Max-Age=2592000` | device token 2FA |
| `XSRF-TOKEN` | no HttpOnly; `SameSite=Strict; Path=/` | CSRF |

**Rutas:**

| BFF | Core | Comportamiento |
|---|---|---|
| `POST /bff/auth/login` | `/api/v1/auth/login` | Si existe `FAMILION_DT`, agrega `"deviceToken"` al JSON. Con tokens → setea `AT`/`RT`, responde `200 {"requires2fa":false}`. Con 2FA → `200 {"requires2fa":true,"challengeToken":"..."}`. Errores del Core se propagan tal cual (status + problem+json). |
| `POST /bff/auth/2fa/verify` | `/api/v1/auth/2fa/verify` | Igual que login; si la respuesta trae `deviceToken` → setea `FAMILION_DT`. |
| `POST /bff/auth/register-invite` | `/api/v1/auth/register-invite` | Igual que login. |
| `POST /bff/auth/refresh` | `/api/v1/auth/refresh` | Lee `FAMILION_RT`; sin cookie → `401`. OK → re-setea `AT`/`RT`, `204`. Core 401 → borra cookies, `401`. |
| `POST /bff/auth/logout` | `/api/v1/auth/logout` | Best-effort al Core (Bearer AT + RT); siempre borra `AT`/`RT` (Max-Age=0) y responde `204`. |
| `POST /bff/auth/{register,verify-email,2fa/resend,forgot-password,reset-password}` | `/api/v1/auth/...` | Passthrough exacto (body, status, content-type). |
| `GET /bff/csrf` | — | `204`, garantiza cookie `XSRF-TOKEN`. |
| `ANY /bff/api/**` | `/api/v1/**` | Proxy: agrega `Authorization: Bearer <FAMILION_AT>` si hay cookie (si no, igual reenvía: el Core decide 401), propaga query string, body (bytes, incluido `multipart/form-data`), status, `Content-Type`, `Content-Disposition`, `Retry-After`. Agrega `X-Forwarded-For`. No reenvía cookies del navegador al Core. |

CSRF: obligatorio (header `X-XSRF-TOKEN`) en todo método no-GET bajo `/bff/**`, **incluido** `/bff/auth/**`.

## Especificación

1. **Proyecto:** Maven, Java 21, Spring Boot 3.5.x, `groupId=com.familion`, `artifactId=familion-bff`, paquete `com.familion.bff`, Maven Wrapper (`mvn -N wrapper:wrapper -Dmaven=3.9.9`). Dependencias: `web`, `security`, `validation`, `actuator`, `data-redis`, `com.bucket4j:bucket4j_jdk17-core` + `com.bucket4j:bucket4j_jdk17-lettuce` (última 8.x), test: `spring-boot-starter-test`, `spring-security-test`, `org.wiremock:wiremock-standalone` (3.x), `spring-boot-testcontainers` + `org.testcontainers:junit-jupiter`.
2. **`application.yml`:** `server.port: 8080`, `spring.threads.virtual.enabled: true`, `familion.core-url: ${CORE_API_URL:http://localhost:8081}`, `familion.cookie-secure: ${COOKIE_SECURE:false}`, `familion.rate-limit-per-min: ${BFF_RATE_LIMIT_PER_MIN:120}`, Redis `${REDIS_HOST:localhost}:${REDIS_PORT:6379}`, actuator `health,info`.
3. **Paquetes:**
   ```
   com.familion.bff
     config/       SecurityConfig, RestClientConfig, BffProperties (@ConfigurationProperties "familion")
     auth/         AuthController, AuthCookieService, AccessTokenResolver, CoreTokenResponse (record)
     proxy/        CoreProxyController
     core/         CoreClient, CoreClientException
     aggregation/  ParallelCalls, PartialResult
     ratelimit/    RateLimitFilter
     error/        GlobalExceptionHandler (ProblemDetail)
   ```
4. **SecurityConfig:** stateless, sin formLogin/httpBasic/logout por defecto; todas las rutas `permitAll` (la autenticación real la decide el Core); CSRF con `CookieCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN`, path `/`, `SameSite=Strict` vía `setCookieCustomizer`) y el patrón SPA de Spring Security 6 (`CsrfTokenRequestAttributeHandler` + filtro que fuerza la carga del token en cada respuesta). Ignorar CSRF solo en `/actuator/**`.
5. **CoreClient** (API pública para T21/T29/T35, no cambiar firmas):
   ```java
   public <T> T get(String pathAndQuery, String accessToken, ParameterizedTypeReference<T> type);   // path relativo a /api/v1
   public <T> T get(String pathAndQuery, String accessToken, Class<T> type);
   // lanza CoreClientException(int status, String problemJson) ante 4xx/5xx; timeouts: connect 2s, read 5s
   ```
   `AccessTokenResolver`: `Optional<String> resolve(HttpServletRequest)` lee `FAMILION_AT`.
6. **ParallelCalls** (para agregaciones):
   ```java
   public final class ParallelCalls {
     public static <T> PartialResult<T> call(Supplier<T> s);                         // envuelve una llamada
     public static void awaitAll(Duration timeout, PartialResult<?>... calls);        // ejecuta en virtual threads, espera todo
   }
   public final class PartialResult<T> { T valueOrNull(); boolean unauthorized(); Optional<Integer> status(); }
   ```
   Regla: si alguna llamada devolvió `401` → el endpoint agregador responde `401`; cualquier otro fallo/timeout → ese campo queda `null`.
7. **RateLimitFilter** (Bucket4j + Redis/Lettuce): clave `rl:bff:user:{sub}` si hay `FAMILION_AT` (decodificar **solo** el payload base64 del JWT para leer `sub`, sin validar firma) o `rl:bff:ip:{ip}` (primer valor de `X-Forwarded-For`, si no `remoteAddr`); capacidad `rate-limit-per-min` por minuto (greedy refill). Excedido → `429` problem+json con `Retry-After`. **Fail-open** si Redis no responde (log WARN). No aplica a `/actuator/**`.
8. **Errores:** problem+json para errores propios del BFF (`502` si el Core no responde: `title: "Core unavailable"`).
9. **Dockerfile** multi-stage igual al del Core (`EXPOSE 8080`, healthcheck `wget -qO- http://localhost:8080/actuator/health`), `.dockerignore`, `bff/README.md`.

## Tests (WireMock simula el Core; Testcontainers Redis para rate limit)
- Login con tokens → setea `FAMILION_AT` y `FAMILION_RT` con atributos correctos y el body **no** contiene tokens.
- Login con `requires2fa` → no setea cookies de sesión, devuelve `challengeToken`.
- Login con cookie `FAMILION_DT` → el request al Core incluye `deviceToken`.
- 2FA verify con `deviceToken` → setea `FAMILION_DT`.
- POST sin header CSRF → `403`; con header y cookie coincidentes → pasa.
- Proxy `GET /bff/api/accounts?view=FAMILY` → Core recibe `GET /api/v1/accounts?view=FAMILY` con `Authorization: Bearer <at>`; status y body propagados.
- Proxy de `multipart/form-data` llega íntegro.
- Refresh sin cookie → `401`; con cookie → rota cookies; Core 401 → cookies borradas.
- Logout borra cookies aunque el Core falle.
- Rate limit: superar el límite → `429`; con Redis caído → requests pasan.
- `ParallelCalls`: un `401` se detecta; un timeout deja `null`.

## Fuera de alcance
Endpoints de agregación (`/bff/home`, `/bff/plan`, `/bff/vehicles/{id}/dashboard`) → T21, T29, T35.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde con todos los tests listados.
- [ ] El navegador nunca recibe un JWT en un body de respuesta.
- [ ] `docker build -t familion-bff ./bff` OK.

## Verificación
```bash
cd bff && ./mvnw -B verify
cd .. && docker build -t familion-bff ./bff
```

## Commits sugeridos
1. `chore(bff): scaffold spring boot bff with maven wrapper`
2. `feat(bff): add cookie-based auth handler with csrf protection`
3. `feat(bff): add core api proxy and parallel call helpers`
4. `feat(bff): add redis-backed per-user rate limiting`
