# Spec 13 - Live pipeline progress

## Why
The run is one transaction (`PipelineRunRecorder`), so other requests see no steps until it commits and the UI
fakes progress with a timer. The professor asked for "ver el pipeline ejecutarse en vivo".

## Contract
`GET /api/briefs/{id}/progress` -> `200 {"running": boolean, "steps": [{"name": string, "status": "SUCCEEDED"|"FAILED"}]}`.
- Scoped by the caller's agency: a brief of another agency or an unknown id answers 404 (same as other brief endpoints).
- No run recorded in memory for that brief -> `{"running": false, "steps": []}`.
- Source = a small in-memory registry (`ConcurrentHashMap<briefId, ...>`, bean in `agent`), updated by
  `PipelineRunRecorder` after every step and marked finished (running=false) when the run succeeds, halts or fails.
  A finished entry is kept for 10 minutes then dropped (lazy cleanup on access, no scheduler). One instance only
  (Render free): say so in a class comment.
- Step names are the handler names already recorded in `agent_steps`. No client text, summaries or ids in the response.

## Judge
New tests pass: registry unit tests (start, step, finish, fail, expiry with an injected `Clock`), recorder test that a
blocking second step shows the first step through the registry while running, controller test 404 for a foreign brief
and 200 shape. Then the full suite (`./mvnw -q test`, sum `target/surefire-reports/*.txt`) has 0 failures.
Out of scope: websockets/SSE, persisting progress, new migrations.
