# Spec 08 - Rate limits and production hardening

## Goal
Brute force on login and cost abuse on the LLM endpoints are cut off with `429` + `Retry-After`, and production
exposes no API docs or internals unless explicitly enabled.

## Judge (tests already written, do not edit them)
- `src/test/java/com/cotizaia/config/RateLimitTests.java`
- `src/test/java/com/cotizaia/config/ProductionProfileTests.java`
- and the **full suite**: 0 failures, 0 errors.

## Rate limiting (`com.cotizaia.config`)
- `RateLimitProperties` (`@ConfigurationProperties("security.rate-limit")`, JavaBean): `enabled` (true),
  `auth.capacity` (10) + `auth.window` (PT15M), `ai.capacity` (30) + `ai.window` (PT1M).
- `RateLimiter`: fixed-window counter per key, thread-safe, in memory (`ConcurrentHashMap`), with an injectable
  `java.time.Clock` bean or constructor arg so windows are testable; `Decision tryAcquire(String key, int capacity,
  Duration window)` -> allowed / retry-after seconds (until the window ends, at least 1).
  Expired windows are evicted lazily (no unbounded growth from one-off keys).
- `RateLimitFilter` (`OncePerRequestFilter`, created inside the security chain, NOT a `@Bean`/`@Component` Filter,
  same reason as the cookie filter), placed after authentication:
  - **auth bucket**, key `ip:<client ip>`: `POST /api/auth/login`, `POST /api/auth/register`, `POST /api/auth/refresh`.
    Client IP = `request.getRemoteAddr()` (prod sets `server.forward-headers-strategy: framework`, so it is the real
    client behind Render's proxy; never parse `X-Forwarded-For` by hand).
  - **ai bucket**, key `agency:<agencyId>` from the JWT (`agencyId` claim): `POST /api/quotations`,
    `POST /api/briefs/upload`, `POST /api/briefs/{id}/process`, `POST /api/briefs/{id}/questions/{qid}/answer`,
    `POST /api/knowledge/documents`, `GET /api/knowledge/search`, `POST /api/proposals/{id}/revise`.
  - Over the limit: status 429, header `Retry-After: <seconds>`, JSON `{"error":"Too many requests"}`;
    the request never reaches the controller.
- Documented limitation (README): in-memory counters are per instance; with more than one instance use a shared
  store (Redis/Bucket4j). Render free runs one instance.

## Production profile (`application-prod.yml`)
- `springdoc.api-docs.enabled: ${SWAGGER_ENABLED:false}` and `springdoc.swagger-ui.enabled: ${SWAGGER_ENABLED:false}`.
- `management.endpoints.web.exposure.include: health`, `management.endpoint.health.show-details: never`.
- `server.error.include-stacktrace: never`, `server.error.include-message: never`.

## Out of scope
Frontend, agent package. Do not edit anything under `src/test/`.
