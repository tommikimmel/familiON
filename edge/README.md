# Edge (Nginx reverse proxy)

`edge/nginx.conf` is the single public entry point of the stack (`http://localhost:8088`).
It routes `/bff/*` to the BFF and everything else to the frontend. No other service is reachable
from outside the `internal_net` network (except Mailpit's UI in development).

## What each directive protects

| Directive | Protection |
|---|---|
| `server_tokens off` | Hides the Nginx version in headers and error pages. |
| `resolver 127.0.0.11` | Uses Docker's embedded DNS to resolve `bff` and `frontend`. |
| `limit_req_zone ... zone=auth rate=5r/m` + `burst=3 nodelay` | Brute-force protection on login, register, invite, password reset, email verification and 2FA endpoints. |
| `limit_req_zone ... zone=api rate=60r/m` + `burst=20` | General per-IP throttling of the BFF API. |
| `limit_conn conn 20` | Caps concurrent connections per IP on BFF routes. |
| `limit_req_status 429` / `limit_conn_status 429` | Throttled clients get `429 Too Many Requests` instead of `503`. |
| `client_body_timeout`, `client_header_timeout`, `send_timeout` (10s), `keepalive_timeout` (15s) | Slowloris / slow-body mitigation: slow clients are dropped quickly. |
| `client_max_body_size 1m` (6m on `/bff/api/receipts/scan`) | Limits upload size; only the receipt scan endpoint accepts larger bodies. |
| `$bad_ua` map + `return 403` (BFF routes only) | Rejects empty user agents and common scripting clients. Not applied to `/` or `/healthz` so internal health checks keep working. |
| Method allow-list (`return 405`) | Only `GET, HEAD, POST, PUT, PATCH, DELETE` are accepted. |
| `Strict-Transport-Security` | Forces HTTPS once served behind TLS (production). |
| `X-Content-Type-Options nosniff` | Disables MIME sniffing. |
| `X-Frame-Options DENY` + CSP `frame-ancestors 'none'` | Clickjacking protection. |
| `Referrer-Policy strict-origin-when-cross-origin` | Avoids leaking full URLs to third parties. |
| `Permissions-Policy` | Camera allowed only for the app itself (receipt scan); geolocation and microphone disabled. |
| `Content-Security-Policy` | Restricts scripts, styles, images, fonts, connections and workers to same origin. |
| `X-Forwarded-*` / `X-Real-IP` headers | Lets the BFF see the real client IP and scheme. |

The auth location is a regex location, so it takes precedence over `location /bff/`; the
user-agent filter is therefore repeated in every `/bff/` location.

## Validate the configuration

```bash
docker run --rm --add-host bff:127.0.0.1 --add-host frontend:127.0.0.1 \
  -v "$PWD/edge/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:1.27-alpine nginx -t
```

## Test the rate limit

With the stack running (`make up`):

```bash
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST -A "Mozilla/5.0" localhost:8088/bff/auth/login
done
```

After the first 4 requests (1 + burst of 3), the remaining ones return `429`.
Without `-A "Mozilla/5.0"` every request returns `403` (curl user agent is blocked).

Liveness probe: `curl -A "Mozilla/5.0" localhost:8088/healthz` returns `ok`.
