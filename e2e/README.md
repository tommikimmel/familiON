# FamiliON End-to-End Smoke Tests

Playwright smoke suite covering the full FamiliON stack (`browser -> edge -> BFF -> Core -> PostgreSQL / Redis / Mailpit`) on both mobile (`chromium-mobile`, Pixel 7) and desktop (`chromium-desktop`) viewports.

## Covered flows

- `tests/auth.spec.ts`: registration, email verification via Mailpit, login, 2FA via Mailpit with "Confiar en este dispositivo", arrival at `/home`, logout, and trusted-device login without 2FA.
- `tests/quick-add.spec.ts`: creating a Cash ARS account in `/accounts`, recording a `1500` expense under "Supermercado" via Quick Add (`+`), verifying the confirmation toast, and checking visibility on `/home` and `/transactions`.
- `tests/family.spec.ts`: inviting a child with `OWN_ONLY` visibility, accepting the invite in an isolated browser context, and confirming the child does not see the "Familia" view toggle.
- `tests/security-headers.spec.ts`: verifying `Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` on `/`, and ensuring no browser request targets `:8081`.
- `tests/pwa.spec.ts`: validating `manifest.webmanifest` (`name`, `192x192` and `512x512` icons) and production service worker (`ngsw-worker.js`) registration.

## Running locally

From the repository root:

```bash
make env
make up
```

Wait until all services report healthy (`make ps`), then install dependencies and run the suite:

```bash
cd e2e
npm ci
npm run install-browsers
npm test
```

Useful commands:

- `npm run test:headed` — run with a visible browser window.
- `npx tsc --noEmit` — type-check the test project.
- `E2E_BASE_URL=http://localhost:8088 E2E_MAILPIT_URL=http://localhost:8025 npm test` — override target URLs.

When finished, stop the stack from the repository root:

```bash
make down
```

## Note on edge rate limiting

The hardened edge proxy enforces `5 r/m` (`burst=3`) on sensitive `/bff/auth/*` routes. Tests run serially (`workers: 1`) and `helpers/auth.ts` paces auth submissions automatically so the suite exercises the real edge configuration without triggering `429` rejections.
