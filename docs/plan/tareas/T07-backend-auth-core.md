# T07 — feat(auth): registro, login, refresh rotatorio, logout, /me y preferencias

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 1 | 1.1 | `feat/backend-auth-core` | Ola 0.2 (T06) | T08, T09 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **12–101** (families, family_currencies, users, family_invitations, refresh_tokens, email_security_codes, trusted_devices).
- `backend/src/main/java/com/familion/common/security/*` (T06: `AccessTokenIssuer`, `TokenRevocationService`, `CurrentUserProvider`).

## Objetivo
Sesiones JWT completas: registrar un adulto creando su familia, login con bloqueo progresivo, refresh token rotatorio con detección de reuso, logout con revocación inmediata, perfil `/auth/me` y preferencias de UI. Además, crear las entidades `Family`/`User` y las APIs que usan otros módulos.

## Archivos permitidos
```
backend/src/main/java/com/familion/auth/**
backend/src/main/java/com/familion/family/**
backend/src/test/java/com/familion/auth/**
backend/src/test/java/com/familion/family/**
```

## Contrato

**APIs Java públicas (otras tareas las importan — firmas exactas):**
```java
package com.familion.family.application.api;
public record FamilySettings(UUID familyId, String name, String baseCurrency, String referenceCurrency,
                             String referenceRateSource, ZoneId timezone, List<String> enabledCurrencies) {}
public record MemberRef(UUID userId, UUID familyId, String fullName, Role role, String accentColor) {}
public interface FamilyQueryApi {
  FamilySettings getSettings(UUID familyId);
  MemberRef requireMember(UUID familyId, UUID userId);   // NotFoundException si no pertenece o inactivo
  List<MemberRef> listMembers(UUID familyId);
}
public record UserAccount(UUID id, UUID familyId, String email, String fullName, Role role, FamilyVisibility visibility,
                          String passwordHash, boolean emailVerified, boolean twoFactorEnabled, boolean active) {}
public interface UserAccountApi {
  Optional<UserAccount> findByEmail(String email);        // email normalizado lower-case
  UserAccount require(UUID userId);
  UUID createMember(UUID familyId, String email, String fullName, String rawPassword, Role role,
                    FamilyVisibility visibility, boolean emailVerified);          // lo usa T10 (invitaciones)
  void markEmailVerified(UUID userId);
  void changePassword(UUID userId, String rawPassword);
  void setTwoFactorEnabled(UUID userId, boolean enabled);
}

package com.familion.auth.application.api;
public record SessionTokens(boolean requires2fa, String challengeToken, String accessToken, Long accessTokenExpiresIn,
                            String refreshToken, Long refreshTokenExpiresIn, String deviceToken) {}
public interface SessionIssuer {
  SessionTokens issueSession(UUID userId, String deviceInfo);   // access + refresh nuevos
  void revokeAllSessions(UUID userId);                          // revoca todos los refresh tokens del usuario
}
public interface SecondFactorGate {                              // T11 provee la implementación real
  Optional<String> challengeIfRequired(UserAccount user, String deviceToken);   // devuelve challengeToken si exige 2FA
}
public record UserRegisteredEvent(UUID userId, UUID familyId, String email, String fullName) {}
```
Si no existe ningún bean `SecondFactorGate`, el login **no** exige 2FA (usar `ObjectProvider<SecondFactorGate>`).

**Endpoints** (`/api/v1/auth`):

| Método | Ruta | Request | Respuesta |
|---|---|---|---|
| POST | `/register` (público) | `{ email, password, fullName, familyName }` | `201 { "userId", "email" }` |
| POST | `/login` (público) | `{ email, password, deviceToken? }` | `200 SessionTokens` (sin `deviceToken`) |
| POST | `/refresh` (público) | `{ refreshToken }` | `200 SessionTokens` |
| POST | `/logout` (auth) | `{ refreshToken? }` | `204` |
| GET | `/me` (auth) | — | `200 Me` |
| PATCH | `/me/preferences` (auth) | `{ themePreference?, accentColor? }` | `200 Me` |

```ts
type Me = { id; email; fullName; role: 'ADULTO'|'HIJO'; familyVisibility; themePreference: 'LIGHT'|'DARK'|'SYSTEM';
  accentColor: '#RRGGBB'; twoFactorEnabled: boolean; emailVerified: boolean;
  family: { id; name; baseCurrency; referenceCurrency; referenceRateSource; timezone; enabledCurrencies: string[] } };
```

## Especificación

1. **Entidades** (`family.domain`): `Family` (tabla `families`, extiende `AuditableEntity`), `FamilyCurrency` (`family_currencies`), `User` (`users`) con enums `Role`/`FamilyVisibility` de `common.security` (`@Enumerated(STRING)`), `ThemePreference`. Repositorios en `family.infrastructure.persistence`. Implementaciones de `FamilyQueryApi` y `UserAccountApi` en `family.application`.
2. **Registro:** email normalizado (trim + lower-case), único (`409` si existe); password mínimo 10 caracteres con al menos una letra y un número (`400`); crea en **una** transacción: `families` (defaults: ARS / USD / USD_MEP / `America/Argentina/Buenos_Aires`, `name = familyName`), `family_currencies` ARS (`preferred_rate_source` null) y USD (`USD_MEP`), `users` (`ADULTO`, `FAMILY_FULL`, `email_verified_at` null, `two_factor_enabled` true). Publica `UserRegisteredEvent` (T11 envía el mail de verificación).
3. **Password:** `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt).
4. **Login:** respuesta de error **genérica** `401 "Invalid credentials"` para usuario inexistente, inactivo o password incorrecto. Bloqueo progresivo en Redis `auth:login-fail:{email}`: tras 5 fallos consecutivos → `429` con `Retry-After` = `60s × 2^(fallos-5)` (máx 1 h); éxito resetea el contador; Redis caído → sin bloqueo (log WARN). Email no verificado → igual permite login (la UI avisa). Si `SecondFactorGate` devuelve challenge → `{ requires2fa: true, challengeToken }`; si no → sesión.
5. **Refresh tokens:** valor = 32 bytes `SecureRandom` en base64url; en DB se guarda `SHA-256` hex en `token_hash`, `expires_at = now + JWT_REFRESH_TTL`, `device_info` (User-Agent truncado a 255). Refresh: buscar por hash; inexistente/expirado → `401`; **ya revocado → reuso detectado**: revocar todos los refresh del usuario y `401`; válido → revocar el actual y emitir par nuevo (rotación).
6. **Logout:** revoca el refresh enviado (si pertenece al usuario) y el `jti` actual vía `TokenRevocationService`.
7. **Preferencias:** `accentColor` regex `^#[0-9A-Fa-f]{6}$` (`400` si no); `themePreference` ∈ enum.
8. Controladores en `auth.infrastructure.web` (`AuthController`, `MeController`); DTOs record con Bean Validation.

## Tests
- Registro crea familia + 2 divisas + usuario ADULTO; email duplicado → 409; password débil → 400; publica `UserRegisteredEvent`.
- Login OK devuelve tokens válidos (el access token funciona contra `/auth/me`); password incorrecto → 401 genérico; 6.º intento fallido → 429 con `Retry-After`.
- Con un bean de test `SecondFactorGate` que exige 2FA → `requires2fa: true` y sin tokens.
- Refresh rota (el viejo deja de servir); reusar el viejo revoca toda la familia de tokens.
- Logout → el access token anterior recibe 401; el refresh revocado → 401.
- `/me` devuelve familia con `enabledCurrencies` `["ARS","USD"]`; PATCH preferencias valida color.
- `FamilyQueryApi.requireMember` con usuario de otra familia → `NotFoundException`.

## Fuera de alcance
Verificación de email, 2FA real, reset de contraseña (T11); invitaciones y settings de familia (T10).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Contratos Java y JSON idénticos a esta tarjeta.
- [ ] Refresh tokens nunca se guardan en claro.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(family): add family and user entities with query apis`
2. `feat(auth): add registration and login with progressive lockout`
3. `feat(auth): add rotating refresh tokens with reuse detection and logout`
4. `feat(auth): add me profile and ui preferences endpoints`
