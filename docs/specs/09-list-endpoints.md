# Spec 09 - List endpoints so the browser stores nothing

## Goal
The frontend kept the agency's briefs and last agent run in `localStorage` because the API could not list them.
Business data must live only on the server (encrypted), so the API lists them.

## Judge
`src/test/java/com/cotizaia/api/ListEndpointsTests.java` and the full suite.

## Contract
- `GET /api/briefs?limit=` -> the caller agency's briefs as `BriefResponse` (same shape as `POST /api/briefs`),
  newest first (by id), `limit` default 35, allowed 1..100 (else 400). Only the caller's agency.
- `GET /api/executions` with `briefId` now **optional**: without it -> the caller agency's latest executions as
  `AgentExecutionResponse`, newest first, `limit` default 10, allowed 1..50. With `briefId` -> unchanged behavior.
- Repository queries are agency-scoped in SQL (no loading everything and filtering in Java).

Do not edit anything under `src/test/`.
