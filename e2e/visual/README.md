# Visual fidelity: mock vs app

Tooling that renders every design mock (`docs/design/mocks/`) next to the app screen it should match. It captures both at **390×844** and **1440×900**, in **light** and **dark** themes, so differences are easy to spot.

## Prerequisites

- The local stack is running (edge `:8088`, BFF `:8080`, Mailpit `:8025`).
- The app you want to review is served. By default this is the dev server: `cd frontend && npx ng serve`, which runs on `http://localhost:4200` and proxies `/bff` to `:8080`.
- Dependencies are installed: `cd e2e && npm ci && npx playwright install chromium`.

## Usage

```bash
cd e2e
npm run visual:seed      # once: creates a seeded adult and writes visual/out/session.json
npm run visual:capture   # writes visual/out/<pair>-<viewport>-<theme>.png
```

Each output image shows the **mock on the left** and the **app on the right**. The raw screenshots are kept in `visual/out/raw/`. Everything under `visual/out/` is git-ignored.

### What the seed creates

The seed goes through the BFF and creates:

- an adult (`Laura Gómez`) and an invited child (`Sofía Gómez`, `OWN_ONLY`);
- the accounts `Efectivo ARS` and `Mercado Pago`, plus a `Visa Galicia` credit card for the installment plan;
- 12 movements in the current month across 5 categories, including one income and one expense attributed to the child;
- a purchase paid in 6 installments;
- 2 fixed expenses;
- a USD savings goal with one contribution;
- a vehicle with 2 fuel loads and 1 service.

The credentials and the browser storage state are saved to `visual/out/session.json`. If that session has expired, `capture` logs in again with the stored credentials, reading the 2FA code from Mailpit when one is needed.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `SEED_BASE_URL` | `http://localhost:8088` | Stack used by `visual:seed` |
| `APP_BASE_URL` | `http://localhost:4200` | App captured by `visual:capture` |
| `E2E_MAILPIT_URL` | `http://localhost:8025` | Mailpit API (used by the shared helpers) |
| `VISUAL_PAIRS` | all pairs | Comma-separated pair ids, for example `shell-home` |
| `VISUAL_SETTLE_MS` | `900` | Wait time before each screenshot |

## Adding pairs

Add an entry to `PAIRS` in `pairs.ts`:

```ts
{
  id: 'plan-overview',
  route: '/plan',
  mocks: {
    mobile: { file: 'mobile.html', tab: 'plan' },
    desktop: { file: 'desktop.html', tab: 'plan' },
  },
}
```

## How the mock side is captured

1. CSS is injected to hide the presentation frame: the bezel, notch, status bar, browser chrome, outer controls, accent picker and splash.
2. The app surface (`.fn-surface`) is stretched to fill the viewport.
3. The theme is set through the mock's own `setTheme()`.
4. The script clicks `[data-tab="…"]`.

Captures run with `prefers-reduced-motion: reduce` so animations settle immediately.
