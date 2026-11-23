# T06 — feat(backend): plataforma común (JWT, CurrentUser, visibilidad, errores, Redis, eventos)

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 0 | 0.2 | `feat/backend-platform-security` | Ola 0.1 (T01) | T04, T05 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `backend/` existente (creado por T01): `pom.xml`, `application.yml`, `IntegrationTestBase`.

## Objetivo
Construir el paquete `com.familion.common` que **todas** las tareas de backend posteriores usan: seguridad JWT (emisión y validación), usuario actual, política de visibilidad, errores RFC 7807, Redis, invalidación de caché por eventos y fixtures de test.

## Archivos permitidos
```
backend/pom.xml
backend/src/main/resources/application.yml
backend/src/main/java/com/familion/common/**
backend/src/test/java/com/familion/common/**
backend/src/test/resources/**
```

## Contrato (API pública que otras tareas importan — firmas exactas)

```java
package com.familion.common.security;
public enum Role { ADULTO, HIJO }
public enum FamilyVisibility { OWN_ONLY, FAMILY_SUMMARY, FAMILY_FULL }
public enum View { PERSONAL, FAMILY }
public enum ViewAccess { OWN, FAMILY_TOTALS_ONLY, FAMILY_FULL }
public record CurrentUser(UUID userId, UUID familyId, Role role, FamilyVisibility visibility) {
  public boolean isAdult() { return role == Role.ADULTO; }
}
public interface CurrentUserProvider { CurrentUser get(); }   // sin autenticación → AuthenticationCredentialsNotFoundException (401)
public interface VisibilityPolicy {
  ViewAccess resolveForListing(CurrentUser u, View requested);
  ViewAccess resolveForTotals(CurrentUser u, View requested);
}
public record IssuedAccessToken(String token, UUID jti, Instant expiresAt, long expiresInSeconds) {}
public interface AccessTokenIssuer {
  IssuedAccessToken issue(UUID userId, UUID familyId, Role role, FamilyVisibility visibility);
  String issueChallenge(UUID userId, Duration ttl);           // JWT typ="2fa" (lo usa T11)
  UUID parseChallenge(String challengeToken);                 // valida firma/exp/typ="2fa" → userId; inválido → BusinessRuleException
}
public interface TokenRevocationService { void revoke(UUID jti, Instant expiresAt); boolean isRevoked(UUID jti); }

package com.familion.common.events;
public record FamilyFinancialDataChangedEvent(UUID familyId) {}

package com.familion.common.error;
public class NotFoundException extends RuntimeException { public NotFoundException(String message) }
public class ForbiddenException extends RuntimeException { ... }
public class ConflictException extends RuntimeException { ... }
public class BusinessRuleException extends RuntimeException { ... }      // → 422
public class TooManyRequestsException extends RuntimeException { public TooManyRequestsException(String msg, Duration retryAfter) }

package com.familion.common.money;
public final class Money { public static final int SCALE = 4; public static final int RATE_SCALE = 6;
  public static BigDecimal scale(BigDecimal v); public static BigDecimal rate(BigDecimal v);
  public static BigDecimal divide(BigDecimal a, BigDecimal b); /* escala 4, HALF_EVEN */ }
```

**Reglas de `VisibilityPolicy`:**

| Usuario | `PERSONAL` | `FAMILY` listing | `FAMILY` totals |
|---|---|---|---|
| `ADULTO` | `OWN` | `FAMILY_FULL` | `FAMILY_FULL` |
| `HIJO` + `OWN_ONLY` | `OWN` | `ForbiddenException` | `ForbiddenException` |
| `HIJO` + `FAMILY_SUMMARY` | `OWN` | `ForbiddenException` | `FAMILY_TOTALS_ONLY` |
| `HIJO` + `FAMILY_FULL` | `OWN` | `FAMILY_FULL` | `FAMILY_FULL` |

**JWT de acceso (HS256, secreto `JWT_SECRET` en base64, ≥ 32 bytes):** claims `sub`=userId, `fam`=familyId, `role`, `vis`, `jti`, `typ`="access", `iat`, `exp` (= `JWT_ACCESS_TTL`, default `PT15M`). Authorities: `ROLE_ADULTO` / `ROLE_HIJO`.

## Especificación

1. **pom.xml:** agregar `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, test `spring-security-test`.
2. **application.yml:** `familion.security.jwt-secret: ${JWT_SECRET:<valor dev base64 de 48 bytes, comentado como solo-dev>}`, `familion.security.access-ttl: ${JWT_ACCESS_TTL:PT15M}`, `familion.security.refresh-ttl: ${JWT_REFRESH_TTL:P30D}`; `spring.data.web.pageable.max-page-size: 50`, `default-page-size: 20`.
3. **`SecurityConfig`** (`common.security`): stateless, CSRF deshabilitado (el Core solo recibe Bearer desde el BFF), `@EnableMethodSecurity`. Públicos: `POST /api/v1/auth/{register,register-invite,verify-email,login,2fa/verify,2fa/resend,forgot-password,reset-password,refresh}`, `GET /api/v1/system/version`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. Todo lo demás autenticado. `oauth2ResourceServer.jwt` con `NimbusJwtDecoder.withSecretKey(...)` + validadores: default (exp), `typ == "access"`, y **no revocado** (`TokenRevocationService`). Converter: claim `role` → `ROLE_<role>`. `AuthenticationEntryPoint` y `AccessDeniedHandler` responden problem+json (401 / 403).
4. **`JwtAccessTokenIssuer`** implementa `AccessTokenIssuer` con `NimbusJwtEncoder` (misma clave). Inyectar `java.time.Clock` (bean UTC en `common.time.ClockConfig`) para testear expiraciones.
5. **`RedisTokenRevocationService`**: clave `auth:revoked:{jti}` con TTL = `expiresAt - now`; si Redis falla → log WARN y `isRevoked` devuelve `false` (fail-open documentado).
6. **`SecurityContextCurrentUserProvider`**: arma `CurrentUser` desde el `Jwt` del contexto (`sub`, `fam`, `role`, `vis`).
7. **`GlobalExceptionHandler`** (`@RestControllerAdvice`, `common.error`) → `ProblemDetail`:
   - `NotFoundException` 404, `ForbiddenException`/`AccessDeniedException` 403, `ConflictException` 409, `BusinessRuleException` 422, `TooManyRequestsException` 429 + header `Retry-After` (segundos).
   - `MethodArgumentNotValidException` / `ConstraintViolationException` / `HttpMessageNotReadableException` → 400 con propiedad `errors: [{field, message}]`.
   - `DataIntegrityViolationException` → 409 genérico (sin filtrar SQL).
   - Cualquier otra → 500 genérico (log ERROR con stack; el body no expone detalles).
   - `type` = `https://familion.app/problems/<slug>`; `instance` = path.
8. **Persistencia común** (`common.persistence`): `@MappedSuperclass CreatedAtEntity` (`createdAt` Instant, set en `@PrePersist`) y `AuditableEntity extends CreatedAtEntity` (`updatedAt`, set en `@PrePersist`/`@PreUpdate`). Columnas `created_at`/`updated_at`.
9. **Caché/eventos** (`common.cache`): `RedisConfig` (`StringRedisTemplate`); `AnalyticsCacheInvalidator` con `@TransactionalEventListener(phase = AFTER_COMMIT)` sobre `FamilyFinancialDataChangedEvent` → borra claves `family:{familyId}:analytics:*` usando `SCAN` (nunca `KEYS`); fail-safe ante Redis caído. Helper `CacheKeys` con `analytics(UUID familyId, String hash)`, `categories(UUID familyId)`.
10. **Web común** (`common.web`): `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.
11. **Fixtures de test** (`src/test/java/com/familion/common/test/`), para que las tareas siguientes escriban ITs sin depender de módulos de otros:
    ```java
    public class TestFixtures {          // @Component de test con JdbcTemplate
      UUID createFamily(String name);                                   // ARS / USD / USD_MEP / AR tz; habilita ARS y USD en family_currencies
      UUID createUser(UUID familyId, String email, Role role, FamilyVisibility vis);   // password hash dummy, email verificado, 2FA off
      UUID createAccount(UUID familyId, UUID ownerUserId, String type, String currency);
    }
    public class JwtTestTokens {         // @Component de test
      String bearer(UUID userId, UUID familyId, Role role, FamilyVisibility vis);     // "Bearer eyJ..."
    }
    ```
    Registrar ambos para que `IntegrationTestBase` los exponga (p. ej. `@Import` o `@TestConfiguration` en la base, con campos `protected TestFixtures fixtures; protected JwtTestTokens tokens;`).

## Tests
- `VisibilityPolicyTest`: las 12 combinaciones de la tabla.
- `JwtAccessTokenIssuerTest`: emitir + decodificar (claims correctos, `typ=access`, exp = clock + ttl); challenge `typ=2fa` se parsea y un access token **no** se acepta como challenge.
- `SecurityIT` (con un `@RestController` de test en `src/test` bajo `/api/v1/test/**`): sin token → 401 problem+json; token válido → 200 y `CurrentUser` correcto; token `typ=2fa` → 401; token revocado → 401; endpoint `@PreAuthorize("hasRole('ADULTO')")` con HIJO → 403; ruta pública `/api/v1/system/version` no exige token (404 está bien, no 401).
- `GlobalExceptionHandlerIT`: cada excepción mapea a su status y `errors[]` en validación.
- `AnalyticsCacheInvalidatorIT`: claves borradas tras commit; no se borran si la transacción hace rollback.

## Fuera de alcance
Login/registro/refresh (T07), endpoints de negocio.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Firmas públicas idénticas al contrato.
- [ ] Ningún stack trace ni SQL en bodies de error.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(common): add jwt resource server and access token issuer`
2. `feat(common): add current user provider and visibility policy`
3. `feat(common): add rfc7807 global exception handling`
4. `feat(common): add redis config, token revocation and cache invalidation`
5. `test(common): add integration fixtures and jwt test tokens`
