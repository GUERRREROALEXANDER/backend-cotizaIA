# Spec 02 - Tokens out of JavaScript's reach

## Goal
After login nothing in the browser that JavaScript can read (body, localStorage, readable cookies) holds a
credential. A stolen access token dies in 15 minutes; a replayed refresh token kills its whole session family.

## Judge (tests already written, do not edit them)
- `src/test/java/com/cotizaia/api/AuthCookieTests.java`
- and the **full suite** `./mvnw -B test`: 0 failures, 0 errors.

## Contract
| Endpoint | Behavior |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | 201 / 200. Body = `AuthResponse` **without any token field** (`expiresAt`, `agencyId`, `userId`, `role` stay). Sets two cookies (below). Exempt from CSRF. |
| `POST /api/auth/refresh` | Reads `cz_refresh`. Valid -> 200, new access + new refresh cookie, old refresh revoked (`replaced_by` set). Unknown/expired -> 401. **Revoked token presented again -> 401 and every token of that family is revoked** (reuse detection). CSRF required. |
| `POST /api/auth/logout` | Revokes the presented refresh token's family, clears both cookies (`Max-Age=0`), 204. CSRF required. |
| `GET /api/auth/csrf` | Public. 204; issues the `XSRF-TOKEN` cookie (the SPA echoes it in `X-XSRF-TOKEN`). |

Cookies (built with `ResponseCookie`):
- `cz_access` = the JWT. `HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=<access ttl seconds>`
- `cz_refresh` = 32 random bytes, base64url. `HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=<refresh ttl seconds>`
- Only the SHA-256 of the refresh token is stored (`refresh_tokens.token_hash`), never the token.

Access token TTL default `PT15M` (`JWT_TTL`), refresh TTL default `P7D` (`security.jwt.refresh-ttl`, env `JWT_REFRESH_TTL`).

## Resource server
- A `BearerTokenResolver` reads the JWT from the `Authorization: Bearer` header **or**, if absent, from the
  `cz_access` cookie. Header-authenticated API clients keep working (scripts, Swagger, tests).
- CSRF: enabled with `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler`
  (plain token in header, no XOR masking, so SPA double-submit works). Protection required for state-changing methods on requests that **carry a `cz_access` or `cz_refresh`
  cookie** (a browser attaches cookies by itself, so those are the forgeable ones). Requests authenticated by an
  `Authorization` header and requests without session cookies are not CSRF-checked (implement a custom
  `RequestMatcher`). `/api/auth/login`, `/api/auth/register` exempt.
- `/api/auth/refresh`, `/api/auth/logout` and `/api/auth/csrf` are `permitAll` at the filter level: refresh and
  logout authenticate with the refresh cookie, not the access token. Spring Security 6 writes the CSRF cookie
  lazily, so `/api/auth/csrf` must take `CsrfToken` as a parameter and call `getToken()`.
- CORS: `allowCredentials(true)`, allowed headers add `X-XSRF-TOKEN`. Origins stay an explicit list.

## Data
`V22__refresh_tokens.sql`: `refresh_tokens(id BIGSERIAL PK, user_id FK users NOT NULL, family_id VARCHAR(36) NOT NULL,
token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ NULL,
replaced_by BIGINT NULL, created_at TIMESTAMPTZ NOT NULL)` + index on `family_id`. Must run on H2 and PostgreSQL.

## Out of scope
Frontend, rate limiting, the encryption package, `agent/`. Do not edit anything under `src/test/`.
