# Security Policy

## Supported versions

Only the latest commit on `main` is supported.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's **Report a vulnerability** button (Security tab, private
vulnerability reporting) on this repository. Include steps to reproduce and the affected endpoint or file.
You can expect an acknowledgement within a few days; fixes are published together with the advisory.

## What the project does

- Passwords hashed with bcrypt; login takes the same path for unknown emails (no user enumeration by timing or message).
- Stateless JWT (HMAC, issuer pinned, strict signature length), 60 minute default lifetime. The server refuses to start
  with a missing, short or placeholder `JWT_SECRET`.
- Every task, address and phone is looked up through its owner, so other users' objects return 404.
- Rate limit on `/api/auth/**` (per client address), request body cap of 64 KiB, per-user caps on tasks (1000),
  addresses (10) and phones (10), bounded page size.
- Strict Content-Security-Policy on the bundled page, plus `nosniff`, frame denial and `no-referrer`.
- Container runs as a non-root user with a read-only filesystem and no extra capabilities; databases listen on
  `127.0.0.1` only and require credentials from `.env`.

## Known limitations

- **No token revocation or refresh.** A stolen JWT works until it expires. Keep `JWT_EXPIRATION_MINUTES` short.
- **Rate limiting is in memory and keyed by the TCP peer address.** Behind a reverse proxy every client shares the
  proxy address, and several instances each keep their own counters. Put a gateway or WAF limiter in front for
  production.
- **No TLS and no HSTS in this repository.** Terminate HTTPS at a reverse proxy before exposing the API.
- **The web page keeps the token in `sessionStorage`.** The CSP reduces XSS risk but cannot remove it.
- **Swagger UI and `/v3/api-docs` are public.** Disable them (`springdoc.api-docs.enabled=false`,
  `springdoc.swagger-ui.enabled=false`) for a production deployment.
- **Schema is managed by Hibernate `ddl-auto: update`.** Move to Flyway or Liquibase migrations before production.
- **Registration reveals whether an email exists** (409 response). Accepted to keep sign-up usable.
- The project has had automated checks only, not an independent penetration test.
