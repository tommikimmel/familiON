# T10 — feat(family): settings, divisas, miembros, invitaciones y visibilidad

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 1 | 1.2 | `feat/backend-family` | Ola 1.1 (T07) | T11, T12 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- `docs/database/schema.sql` líneas **12–67** (families, family_currencies, users, family_invitations).
- Código existente de T07: `com.familion.family.domain.*`, `com.familion.family.application.api.*` (`UserAccountApi.createMember`), `com.familion.auth.application.api.SessionIssuer`.

## Objetivo
Gestión del hogar: editar configuración de la familia, habilitar divisas, listar miembros, invitar adultos/hijos (por email y por link compartible), configurar la visibilidad de cada hijo y registrar a un invitado.

## Archivos permitidos
```
backend/src/main/java/com/familion/family/**        (excepto el subpaquete allowances, que es de T28)
backend/src/test/java/com/familion/family/**
```
> El endpoint `/api/v1/auth/register-invite` se implementa en **este** módulo con un controller propio (`family.infrastructure.web.InvitationRegistrationController`) para no tocar el paquete `auth` (lo edita T11 en paralelo).

## Contrato

| Método | Ruta | Acceso | Request | Respuesta |
|---|---|---|---|---|
| GET | `/api/v1/family` | auth | — | `Family` |
| PATCH | `/api/v1/family/settings` | ADULTO | `{ name?, baseCurrency?, referenceCurrency?, referenceRateSource?, timezone? }` | `Family` |
| GET | `/api/v1/family/currencies` | auth | — | `FamilyCurrency[]` |
| POST | `/api/v1/family/currencies` | ADULTO | `{ currencyCode, preferredRateSource? }` | `201 FamilyCurrency` |
| PATCH | `/api/v1/family/currencies/{currencyCode}` | ADULTO | `{ isActive?, preferredRateSource? }` | `FamilyCurrency` |
| GET | `/api/v1/family/members` | auth | — | `Member[]` |
| PATCH | `/api/v1/family/members/{userId}/visibility` | ADULTO | `{ familyVisibility }` | `Member` |
| GET | `/api/v1/family/invitations` | ADULTO | — | `Invitation[]` (pendientes, no vencidas) |
| POST | `/api/v1/family/invitations` | ADULTO | `{ email, role, familyVisibility? }` | `201 InvitationCreated` |
| DELETE | `/api/v1/family/invitations/{id}` | ADULTO | — | `204` |
| GET | `/api/v1/auth/invitations/{token}` | **público** | — | `{ email, role, familyName, expiresAt }` (`404` si inválida/vencida/aceptada) |
| POST | `/api/v1/auth/register-invite` | **público** | `{ token, fullName, password }` | `200 SessionTokens` |

```ts
type Family = { id; name; baseCurrency; referenceCurrency; referenceRateSource; timezone;
  enabledCurrencies: FamilyCurrency[] };
type FamilyCurrency = { currencyCode: string; preferredRateSource: string|null; isActive: boolean };
type Member = { id; fullName; email; role: 'ADULTO'|'HIJO'; familyVisibility; accentColor; isActive: boolean };
type Invitation = { id; email; role; familyVisibility; expiresAt; createdAt; invitedBy: { id; fullName } };
type InvitationCreated = Invitation & { inviteUrl: string };   // `${APP_PUBLIC_URL}/auth/invite?token=<token>`
type SessionTokens = { requires2fa:false; challengeToken:null; accessToken; accessTokenExpiresIn; refreshToken; refreshTokenExpiresIn; deviceToken:null };
```

> **Importante:** `GET /api/v1/auth/invitations/{token}` es público. Como `SecurityConfig` (T06) no lo incluye, **declararlo público sin tocar `common`**: registrar en este módulo un `@Bean @Order(1) SecurityFilterChain` con `securityMatcher("/api/v1/auth/invitations/**")` y `permitAll()`. Documentarlo en la PR.

## Reglas

1. **Divisas válidas:** `ARS, USD, EUR, USDT`. Referencias válidas (`referenceCurrency`): `USD, EUR, UVA, ARS`. Fuentes (`referenceRateSource`, `preferredRateSource`): `USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA`. `timezone` debe ser un `ZoneId` válido. Valores inválidos → `400`.
2. **Cambiar `baseCurrency`** con transacciones existentes (`SELECT EXISTS(SELECT 1 FROM transactions WHERE family_id = ?)` vía `JdbcTemplate` en `family.infrastructure.persistence`) → `409` "Base currency cannot change once transactions exist".
3. Habilitar divisa ya existente → `409`. No se puede desactivar la `baseCurrency` → `409`.
4. **Visibilidad:** solo aplica a `HIJO`; para un `ADULTO` → `409` (siempre `FAMILY_FULL`). Usuario de otra familia → `404`.
5. **Invitaciones:** token = 32 bytes `SecureRandom` base64url; guardar `SHA-256` hex en `token_hash`; `expires_at = now + 7 días`. Email ya registrado en el sistema → `409`. Para `ADULTO`, `familyVisibility` se fuerza a `FAMILY_FULL`; para `HIJO`, default `OWN_ONLY`. Enviar email (texto plano + HTML simple inline, sin Thymeleaf) con `JavaMailSender` desde `familion.app.mail-from`: asunto «Te invitaron a FamiliON», cuerpo con nombre de la familia, quién invita y el `inviteUrl`. Fallo de envío → log WARN, la invitación igual se crea (el adulto puede compartir el link).
6. **register-invite:** token válido, no vencido, no aceptado; crea el usuario con `UserAccountApi.createMember(familyId, invitation.email, fullName, password, role, visibility, emailVerified=true)`, marca `accepted_at`, emite sesión con `SessionIssuer.issueSession`. Misma política de password que el registro (≥10, letra + número).
7. Todas las queries filtran por `CurrentUser.familyId()`.

## Tests
- PATCH settings como ADULTO OK / como HIJO 403 / base currency con transacciones → 409 (insertar una transacción con SQL en el test).
- Habilitar `EUR` → aparece en `/family` y en `FamilyQueryApi.getSettings().enabledCurrencies()`.
- Visibilidad de HIJO cambia; de ADULTO → 409; de otra familia → 404.
- Invitación: se guarda hash (no el token), email enviado (usar `GreenMail` **no**: mockear `JavaMailSender` con `@MockitoBean`), `inviteUrl` contiene el token.
- `GET /auth/invitations/{token}` público OK; vencida → 404.
- `register-invite` crea HIJO con visibilidad de la invitación, devuelve tokens, marca aceptada; reutilizar token → 404.

## Fuera de alcance
Mensualidades (T28), plantillas Thymeleaf (T11).

## Criterios de aceptación
- [ ] `./mvnw -B verify` en verde.
- [ ] Contratos JSON exactos.
- [ ] Tokens de invitación nunca persistidos en claro.

## Verificación
```bash
cd backend && ./mvnw -B verify
```

## Commits sugeridos
1. `feat(family): add family settings and currencies endpoints`
2. `feat(family): add members listing and child visibility management`
3. `feat(family): add invitations with email delivery and invite registration`
