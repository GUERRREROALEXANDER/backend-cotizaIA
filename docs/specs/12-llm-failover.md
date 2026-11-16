# Spec 12 - LLM failover chain (Gemma 4 on Google, then Groq)

## Why
Free LLM quotas run out (Ollama Cloud answered `HTTP 402` and every quotation stalled in `ANALYZING`). One provider
is a single point of failure for the demo. A chain tries the next provider when the first one is out of quota or down,
each provider on its own account and its own free plan. No key rotation across accounts: Groq's acceptable-use policy
forbids that.

## Judge
`FallbackLlmClientTests`, `OpenAiCompatibleClientTests`, `LlmProviderSwitchTests` and the full suite.

## Contract
- **Off by default.** `llm.chain` empty = today's behavior (`llm.provider` picks one family). No existing property
  changes meaning.
- **`LLM_CHAIN=gemini,groq`** (comma list, order = priority). Allowed names: `gemini`, `groq`. Unknown or empty
  entry fails at startup, like an unknown `llm.provider`.
- **Chain of Responsibility.** `FallbackLlmClient` calls each client in order and returns the first answer. It moves
  on only for `LlmException` (quota, 5xx, network, bad key, payment). Any other exception is a bug and propagates.
  If every client fails it throws the last `LlmException` with the earlier ones attached as suppressed.
- **Fast failover.** Each client keeps its own `RetryLlmClient`, but every client except the last uses at most 2
  attempts and a 3 s backoff cap, so a provider that answers 429 with a long `Retry-After` does not hold the
  request for minutes before the next one is tried. The last client keeps the configured retry.
- **Gemini = OpenAI-compatible.** `llm.gemini.*` (`GEMINI_BASE_URL`, `GEMINI_API_KEY`, `GEMINI_MODEL`,
  `GEMINI_REASONING_EFFORT`) defaults to `https://generativelanguage.googleapis.com/v1beta/openai` and
  `gemma-4-31b-it`. It reuses the Groq wire code through `OpenAiCompatibleClient`; `GroqClient` keeps its name,
  behavior and error text.
- **Traceability.** `LlmResponse.provider()` says which client answered (`gemini` or `groq`); a failover logs one
  WARN line with the failed provider and the reason.
- **Embeddings untouched.** `llm.provider` still selects the embedding family (Voyage). With a chain set, use
  `LLM_PROVIDER=groq`.

## Files
| file | change |
|---|---|
| `agent/llm/FallbackLlmClient` | new: the chain |
| `agent/llm/OpenAiCompatibleClient` | new: Groq wire code, named and parametrised |
| `agent/llm/GroqClient` | edit: thin subclass |
| `agent/llm/LlmProperties` | edit: `chain`, `gemini` |
| `agent/llm/LlmClientConfiguration` | edit: builds the chain |
| `application.yml`, `.env.example` | edit: `LLM_CHAIN`, `GEMINI_*` |

## Out of scope
Per-request provider choice, health checks or circuit breakers, load balancing, more than the two named providers,
Ollama (pay-as-you-go now), several keys per provider.

## Operations
Google AI Studio free tier: quota is per Google Cloud project, not per key, and the exact numbers are shown in
https://aistudio.google.com/rate-limit (about 30 RPM and 16K input tokens per minute for Gemma 4 at the time of
writing). Tokens per minute is the limit that bites. Free-tier prompts may be used by Google for training; the demo
data is fictional.
