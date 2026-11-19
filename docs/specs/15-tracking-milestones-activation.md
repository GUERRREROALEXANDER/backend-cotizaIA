# Spec 15 - "Vista" tracking, schedule milestones, project activation

## Why
project.txt promises status tracking "enviada, vista, negociando, aceptada", a schedule with "hitos", and
"Proyecto activado" after the deposit. None exists.

## Contract
- Migration `V29__proposal_viewed_at.sql` (check the vendor folders like earlier migrations): nullable `viewed_at`
  timestamp on `proposals`. Not encrypted (plain timestamp, like `approved_at`).
- `POST /api/proposals/{id}/viewed` -> `200 ProposalDetailResponse`. Allowed in `SENT` or `NEGOTIATING`, else `409`.
  Idempotent: the first call sets `viewedAt`, later calls keep it. Agency scoped (404 otherwise). It does NOT change
  `ProposalStatus` (no new enum value, no state-machine change); it is a tracked fact, shown as "vista".
- `ProposalDetailResponse` gains `viewedAt` (ISO or null) and `projectActive` (true iff the proposal's contract is
  `ISSUED` and a deposit payment exists; derived, not stored).
- `ScheduleResponse` gains `milestones: [{"name": string, "week": int}]`, derived: one per phase = "Entrega: <phase name>"
  at that phase's `endWeek`, plus a final "Lanzamiento" at the last end week. Pure derivation in the response mapper.
- `AcceptanceResponse` gains `projectActive` (true after a successful accept).
- `docs/API` / README table updated with the three new fields.

## Judge
Tests: viewed sets the time once, 409 in IN_REVIEW, 404 foreign agency, status unchanged; milestones are one per phase
plus launch with the right weeks (use a 3-phase fixture); projectActive false before accept, true after; migration
applies on H2 and on real PostgreSQL (Testcontainers) like the existing ones. Then full suite 0 failures.
Out of scope: new ProposalStatus values, client-facing public view links, email tracking pixels.
