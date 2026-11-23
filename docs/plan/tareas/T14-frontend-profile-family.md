# T14 — feat(frontend): perfil (tema y color), familia, miembros, invitaciones y seguridad

| Fase | Ola | Rama | Requiere mergeado | Paralela con |
|---|---|---|---|---|
| 2 | 2.1 | `feat/frontend-profile-family` | Fase 1 completa | T13, T15 |

## Leer antes
- `docs/plan/00-reglas-worker.md`
- Frontend existente: `core/state/session.store.ts`, `core/theme/theme.service.ts`, `core/auth/auth.store.ts` (`reloadMe`, `logout`), `shared/ui/*`, `shared/utils/color.ts`.
- `frontend/src/app/features/profile/**` (placeholders).

## Objetivo
Pantallas de "Familia y Perfil": personalización (tema + color de acento con presets y color picker), configuración económica de la familia, miembros y visibilidad de hijos, invitaciones compartibles y seguridad (2FA, dispositivos confiables).

## Archivos permitidos
```
frontend/src/app/features/profile/**
```

## Contrato BFF

| Llamada | Request | Respuesta |
|---|---|---|
| `PATCH /bff/api/auth/me/preferences` | `{ themePreference?, accentColor? }` | `Me` |
| `GET /bff/api/family` | — | `Family` |
| `PATCH /bff/api/family/settings` (ADULTO) | `{ name?, baseCurrency?, referenceCurrency?, referenceRateSource?, timezone? }` | `Family` · `409` si cambia base con movimientos |
| `POST /bff/api/family/currencies` (ADULTO) | `{ currencyCode, preferredRateSource? }` | `FamilyCurrency` |
| `PATCH /bff/api/family/currencies/{code}` (ADULTO) | `{ isActive?, preferredRateSource? }` | `FamilyCurrency` |
| `GET /bff/api/family/members` | — | `Member[]` |
| `PATCH /bff/api/family/members/{userId}/visibility` (ADULTO) | `{ familyVisibility }` | `Member` |
| `GET /bff/api/family/invitations` (ADULTO) | — | `Invitation[]` |
| `POST /bff/api/family/invitations` (ADULTO) | `{ email, role, familyVisibility? }` | `InvitationCreated` |
| `DELETE /bff/api/family/invitations/{id}` (ADULTO) | — | `204` |
| `GET /bff/api/auth/me/security` | — | `{ twoFactorEnabled, trustedDevices: [{ id, deviceName, createdAt, expiresAt }] }` |
| `PATCH /bff/api/auth/me/security` | `{ twoFactorEnabled }` | igual |
| `DELETE /bff/api/auth/me/security/trusted-devices/{id}` | — | `204` |

```ts
type Family = { id; name; baseCurrency; referenceCurrency; referenceRateSource; timezone; enabledCurrencies: FamilyCurrency[] };
type FamilyCurrency = { currencyCode: string; preferredRateSource: string|null; isActive: boolean };
type Member = { id; fullName; email; role: 'ADULTO'|'HIJO'; familyVisibility: 'OWN_ONLY'|'FAMILY_SUMMARY'|'FAMILY_FULL'; accentColor; isActive: boolean };
type Invitation = { id; email; role; familyVisibility; expiresAt; createdAt; invitedBy: { id; fullName } };
type InvitationCreated = Invitation & { inviteUrl: string };
```
Valores válidos: divisas `ARS, USD, EUR, USDT`; referencia `USD, EUR, UVA, ARS`; fuentes `USD_OFICIAL, USD_MEP, USD_BLUE, EUR_OFICIAL, EUR_BLUE, UVA`.

## Especificación

1. **Estructura:** `features/profile/data-access/{profile.api.ts, family.api.ts, security.api.ts, family.store.ts}`, `pages/{profile,family,security}.page.ts`, `components/*` presentacionales.
2. **`/profile`:**
   - Cabecera: avatar con inicial sobre color de acento, nombre, email, rol («Adulto» / «Hijo/a»), nombre de la familia.
   - **Tema:** `fo-segmented-toggle` «Claro | Oscuro | Sistema».
   - **Color de perfil:** 8 presets (`#0F766E`, `#2563EB`, `#7C3AED`, `#DB2777`, `#EA580C`, `#CA8A04`, `#16A34A`, `#475569`) en círculos de 40px con check en el seleccionado + botón **`+`** que abre `<input type="color">` nativo (color personalizado). Vista previa en vivo (`ThemeService.apply`) y guardado con debounce 400ms (`PATCH preferences`); al fallar, revertir y toast de error. Mostrar muestra del logo `fo-logo` con el acento.
   - Links a «Familia» y «Seguridad»; botón «Cerrar sesión» (`AuthStore.logout()`).
3. **`/profile/family`:**
   - Configuración (editable solo ADULTO; HIJO en solo lectura): nombre, divisa principal, divisa de referencia + fuente, zona horaria (select con `Intl.supportedValuesOf('timeZone')` filtrado a `America/*` primero). `409` → mensaje «No se puede cambiar la divisa principal porque ya hay movimientos cargados.».
   - Divisas habilitadas: chips con bandera (SVG inline simple o emoji como fallback) + «Agregar divisa» (ADULTO) + switch activo/inactivo (no permitir desactivar la principal).
   - Miembros: lista con avatar de color, nombre, rol; para cada HIJO (si soy ADULTO) select de visibilidad con textos: `OWN_ONLY` «Solo lo suyo», `FAMILY_SUMMARY` «Totales de la familia», `FAMILY_FULL` «Todo el detalle».
   - Invitaciones (ADULTO): formulario email + rol (Adulto/Hijo) + visibilidad (solo si Hijo); al crear muestra el `inviteUrl` con botones «Copiar enlace» (Clipboard API) y «Compartir» (`navigator.share` si existe). Lista de pendientes con «Revocar».
   - Tras cambios que afectan `Me` (settings), llamar `AuthStore.reloadMe()`.
4. **`/profile/security`:** switch «Verificación en dos pasos por email»; lista de dispositivos confiables con fecha de alta y vencimiento + «Quitar».
5. Estados de carga con `fo-skeleton`, vacíos con `fo-empty-state`, errores con toast.

## Tests
- `family.store.spec.ts`: carga, invitar (agrega a la lista), revocar, cambio de visibilidad optimista con rollback ante error.
- Selector de color: preset aplica `ThemeService.apply` y llama PATCH (debounced); el `+` emite color custom.
- La página de familia oculta controles de edición para HIJO.

## Fuera de alcance
Cuentas y categorías (T15), mensualidades (T31).

## Criterios de aceptación
- [ ] `npm run lint && npm test && npm run build -- --configuration production` en verde.
- [ ] Cambio de color se refleja al instante en toda la app y persiste tras recargar.
- [ ] Todo operable con teclado y lector de pantalla (labels y `aria-pressed` en presets).

## Verificación
```bash
cd frontend && npm ci && npm run lint && npm test && npm run build -- --configuration production
```

## Commits sugeridos
1. `feat(profile): add theme and accent color personalization`
2. `feat(profile): add family settings, currencies and members management`
3. `feat(profile): add shareable invitations and security settings`
