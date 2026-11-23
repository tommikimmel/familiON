# T11 — feat(auth): verificación de email, 2FA por OTP, dispositivos confiables y reset de contraseña

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 1 | 1.2 | `feat/backend-email-security` | Ola 1.1 (T07) | T10, T12 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **79–101** (`email_security_codes`, `trusted_devices`).
- Código de T07: `com.familion.auth.application.api.*` (`SecondFactorGate`, `SessionIssuer`, `UserRegisteredEvent`, `SessionTokens`), `com.familion.family.application.api.UserAccountApi`.
- Código de T06: `AccessTokenIssuer.issueChallenge/parseChallenge`.

## Objetivo
Completar la seguridad por email: verificar la cuenta, exigir un código de 6 dígitos al iniciar sesión desde dispositivos no confiables, recordar dispositivos 30 días, y recuperar la contraseña con un link de un solo uso.

## Archivos permitidos
```
backend/src/main/java/com/familion/auth/**          (NO modificar las firmas de application/api creadas por T07)
backend/src/main/resources/templates/mail/**
backend/src/test/java/com/familion/auth/**
```

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/auth/verify-email` | público | `{ email, code }` | `204` · inválido/vencido → `422` |
| POST | `/api/v1/auth/verify-email/resend` | auth | — | `202` (cooldown 60 s → `429`) |
| POST | `/api/v1/auth/2fa/verify` | público | `{ challengeToken, code, trustDevice: boolean, deviceName? }` | `200 SessionTokens` (con `deviceToken` si `trustDevice`) |
| POST | `/api/v1/auth/2fa/resend` | público | `{ challengeToken }` | `202` · cooldown → `429` + `Retry-After` |
| POST | `/api/v1/auth/forgot-password` | público | `{ email }` | **siempre** `202` |
| POST | `/api/v1/auth/reset-password` | público | `{ token, newPassword }` | `204` · inválido/vencido → `422` |
| GET | `/api/v1/auth/me/security` | auth | — | `{ twoFactorEnabled, trustedDevices: [{ id, deviceName, createdAt, expiresAt }] }` |
| PATCH | `/api/v1/auth/me/security` | auth | `{ twoFactorEnabled }` | igual que GET |
| DELETE | `/api/v1/auth/me/security/trusted-devices/{id}` | auth | — | `204` |

> `POST /api/v1/auth/verify-email/resend` **no** está en la lista pública de T06 → requiere token (correcto).

## Especificación

1. **`EmailCodeService`** (`auth.application`): genera códigos y los guarda en `email_security_codes` con `code_hash = SHA-256 hex`.
   - `TWO_FACTOR_LOGIN`: 6 dígitos (`SecureRandom`, con ceros a la izquierda), vence en **10 min**, `max_attempts = 5`.
   - `EMAIL_VERIFICATION`: 6 dígitos, vence en **24 h**.
   - `PASSWORD_RESET`: token de 32 bytes base64url, vence en **20 min**.
   - Al crear un código nuevo del mismo propósito, consumir (`consumed_at = now`) los anteriores pendientes.
   - Verificar: incrementa `attempts`; si supera `max_attempts` o venció → inválido; éxito → `consumed_at = now`. Comparación en tiempo constante (`MessageDigest.isEqual`).
   - Guardar `request_ip` (desde `X-Forwarded-For` primer valor o `remoteAddr`).
2. **Cooldown** de reenvíos: Redis `auth:otp-cooldown:{userId}:{purpose}` 60 s; Redis caído → sin cooldown (WARN).
3. **Verificación de email:** listener `@TransactionalEventListener(AFTER_COMMIT)` de `UserRegisteredEvent` → genera código y envía mail `verify-email`. `verify-email` busca el usuario por email (si no existe → `422` genérico) y llama `UserAccountApi.markEmailVerified`.
4. **`SecondFactorGateImpl`** (`@Component` que implementa `SecondFactorGate` de T07): si `user.twoFactorEnabled()` y el `deviceToken` recibido **no** coincide con un `trusted_devices` vigente del usuario (hash SHA-256, no revocado, no vencido) → genera OTP, envía mail `two-factor-code`, devuelve `AccessTokenIssuer.issueChallenge(userId, 10 min)`. Si no → `Optional.empty()`.
5. **2FA verify:** `parseChallenge` → userId; valida OTP; si `trustDevice` crea `trusted_devices` (token aleatorio 32 bytes; guardar hash; `expires_at = now + 30 días`; `device_name` del request o User-Agent truncado) y lo devuelve como `deviceToken`; emite sesión con `SessionIssuer.issueSession`. Challenge inválido → `401`; código inválido → `422` con `detail` que indica intentos restantes.
6. **Forgot/reset:** forgot responde siempre `202` (anti-enumeración; mismo tiempo de respuesta aproximado: enviar el mail en un `@Async`). El mail `password-reset` incluye `${familion.app.public-url}/auth/reset-password?token=<token>`. Reset: valida token, `UserAccountApi.changePassword`, `SessionIssuer.revokeAllSessions`, revoca todos los `trusted_devices`, envía mail `password-changed`. Política de password: ≥10, letra + número.
7. **Plantillas Thymeleaf** en `templates/mail/`: `verify-email`, `two-factor-code`, `password-reset`, `password-changed` — cada una `.html` (layout minimalista: logo "FamiliON" en texto con el "ON" en `#0F766E`, código grande en monoespaciado, footer «Si no solicitaste esta acción, puedes ignorar este mensaje.») y `.txt`. Asuntos: «Verifica tu correo en FamiliON», «Tu código de acceso a FamiliON», «Restablecer tu contraseña de FamiliON», «Tu contraseña de FamiliON cambió». Envío multipart (HTML + texto) con `MimeMessageHelper`, remitente `familion.app.mail-from`. Registrar `@EnableAsync` en una config del módulo si no existe.
8. **Security settings:** desactivar 2FA revoca dispositivos confiables? **No**; solo cambia el flag. Revocar dispositivo de otro usuario → `404`.

## Tests
- `EmailCodeServiceTest`: generación (6 dígitos con ceros), hash, expiración, intentos máximos, consumo de anteriores.
- Registro (usando el endpoint de T07) → se envía mail de verificación (mock `JavaMailSender`) → `verify-email` con el código correcto marca verificado; código erróneo 5 veces → inválido aunque luego sea correcto.
- Login con 2FA activo → `requires2fa`; `2fa/verify` correcto devuelve tokens; con `trustDevice` devuelve `deviceToken`; login siguiente con ese `deviceToken` → sin 2FA.
- `2fa/resend` dos veces seguidas → `429`.
- Forgot con email inexistente → `202` y sin mail; existente → mail con link; reset → password nueva funciona, refresh tokens previos → `401`.
- Plantillas renderizan sin errores (test de render con `TemplateEngine`).

## Fuera de alcance
Pantallas (T12, T14). SMTP de producción.

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Ningún código/token persistido en claro; comparación en tiempo constante.
- [ ] `forgot-password` no revela si el email existe.
- [ ] Probado manualmente con Mailpit (`make up`, registrar, ver mail en `http://localhost:8025`) — opcional si el stack no levanta aún; indicarlo en la PR.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(auth): add email security code service with attempts and expiry`
2. `feat(auth): add email verification flow with thymeleaf templates`
3. `feat(auth): add email otp second factor with trusted devices`
4. `feat(auth): add password reset flow revoking active sessions`
