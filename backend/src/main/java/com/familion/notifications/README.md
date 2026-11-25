# FamiliON Notifications & Web Push

This module provides in-app notifications, web push subscriptions (PWA), version and changelog endpoints, and automated household alerts.

## Web Push & VAPID Configuration

Web Push delivery is configured via VAPID (Voluntary Application Server Identification) environment variables:

- `VAPID_PUBLIC_KEY`: Base64-encoded VAPID public key.
- `VAPID_PRIVATE_KEY`: Base64-encoded VAPID private key.
- `VAPID_SUBJECT`: Contact URI (e.g. `mailto:admin@familion.local`).

### Generating VAPID Keys

To generate a new VAPID keypair using Node.js / npm:

```bash
npx web-push generate-vapid-keys
```

Example output:
```text
=======================================

Public Key:
BNc...your-public-key...

Private Key:
...your-private-key...

=======================================
```

Copy the public and private keys into your `.env` or deployment secrets:

```env
VAPID_PUBLIC_KEY=BNc...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:admin@familion.local
```

### Disabled Mode (Default)

If `VAPID_PUBLIC_KEY` or `VAPID_PRIVATE_KEY` are empty or omitted:
- Web push delivery is disabled gracefully.
- `GET /api/v1/notifications/push-config` returns `{ "publicKey": null }`.
- In-app notifications, event listeners, and schedulers continue to operate normally without attempting push network calls.
