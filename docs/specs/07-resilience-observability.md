# Spec 07 - Provider limits and diagnosable failures

## Why
The first real eval (Groq + Voyage free tiers) exposed two production gaps: (1) the retry waited 0.5 s / 1 s while
the providers limit per minute and say how long to wait (`retry-after`; Groq 8K tokens/min, Voyage 3 requests/min),
so two quotations in a row failed with 503; (2) a failed run left no trace in the logs: which step, which cause.

## Judge (tests already written, do not edit them)
- `src/test/java/com/cotizaia/agent/llm/RetryAfterTests.java`
- `src/test/java/com/cotizaia/agent/PipelineRunRecorderTests.java` (new test `failedRunIsLoggedWith...`)
- and the **full suite**: 0 failures, 0 errors.

## Contract
- `com.cotizaia.agent.llm.RetryAfter` (final utility): `static Optional<Duration> parse(String headerValue)`:
  seconds as integer or decimal (`"12"`, `"1.5"`); null/blank/garbage -> empty. (HTTP-date form may be ignored.)
- `LlmException`: new ctor `(String message, boolean retryable, Duration retryAfter, Throwable cause)` (retryAfter
  nullable); `Optional<Duration> retryAfter()`; existing ctors keep working (empty retryAfter).
  Same additions on `EmbeddingException`.
- `AbstractHttpLlmClient.mapFailure(RestClientResponseException)` and `VoyageEmbeddingClient.mapFailure(...)` read the
  `Retry-After` response header into the exception.
- `LlmProperties.Retry` gains `maxBackoff` (default 60s); `llm.retry.max-attempts` default becomes 4.
- `RetryLlmClient` and `RetryEmbeddingClient`: delay before retry n = `min(max(backoff_n, retryAfter), maxBackoff)`
  where `backoff_n = initialBackoff * multiplier^(n-1)`.
- `PipelineRunRecorder`: when a run fails, log once at WARN:
  `Pipeline run failed execution=<id> handler=<failing handler name> cause=<ExceptionClass>: <message>`.
  Never log brief text, prompts or model output (client data).

## Out of scope
Prompts, pipeline behavior, frontend. Do not edit anything under `src/test/`.
