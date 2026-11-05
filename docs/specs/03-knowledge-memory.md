# Spec 03 - Knowledge memory (RAG foundation)

## Goal
The agency's past proposals become searchable memory: each PDF is split into passages and priced line items,
embedded, and stored per agency. Retrieval returns the most similar past items with their approved hours and a
citation (document, page, similarity). Spec 04 wires it into the estimate; this spec builds the memory.

Delivered in three parts, one per run. Each part's judge = its listed tests **and the full suite**.

## Part A - Embeddings (`com.cotizaia.agent.embedding`)
Tests: `src/test/java/com/cotizaia/agent/embedding/*Tests.java`

| Type | Contract |
|---|---|
| `EmbeddingPurpose` | enum `QUERY`, `DOCUMENT` |
| `EmbeddingClient` | `List<float[]> embed(List<String> texts, EmbeddingPurpose purpose)` (same order, same size), `String provider()` |
| `EmbeddingException` | unchecked, `boolean isRetryable()` (429 and 5xx retryable; 401/403/400 not) |
| `EmbeddingProperties` | JavaBean like `LlmProperties` (no-arg ctor, getters/setters, nested `Voyage` and `Ollama` classes), `@ConfigurationProperties("embedding")`: `voyage.base-url` (default `https://api.voyageai.com/v1`), `voyage.api-key` (`${VOYAGE_API_KEY:}`), `voyage.model` (default `voyage-4-lite`), `dimensions` (default 1024), `batch-size` (default 64), `cache-size` (default 10000), `ollama.base-url` (default `http://localhost:11434`), `ollama.model` (default `nomic-embed-text`) |
| `VoyageEmbeddingClient` | **Adapter**. ctor `(RestClient.Builder, EmbeddingProperties)`. `POST {base-url}/embeddings`, `Authorization: Bearer <key>`, body `{"input":[...],"model":...,"input_type":"query"\|"document","output_dimension":<dimensions>}`. Response `{"data":[{"embedding":[...],"index":i}]}`, re-ordered by `index`. Splits input into requests of at most `batch-size` texts. Blank key -> `EmbeddingException` before any HTTP call. `provider()` = `"voyage"`. Empty input list -> empty result, no HTTP. |
| `OllamaEmbeddingClient` | **Adapter**. `POST {ollama.base-url}/api/embed` body `{"model":...,"input":[...]}` -> `{"embeddings":[[...]]}`. `provider()` = `"ollama"`. |
| `StubEmbeddingClient` | Offline, deterministic. 256 dims. Normalize text (strip accents, lowercase), tokens `[a-z]+|[0-9]+`, drop letter tokens shorter than 3 chars. Feature hashing: index `floorMod(token.hashCode(), 256)`, sign `+1` if `floorMod(token.hashCode() / 256, 2) == 0` else `-1`, weight `1 + ln(count)`. L2-normalized; text without tokens -> all zeros. `provider()` = `"stub"`. |
| `CachingEmbeddingClient` | **Proxy**: same interface, wraps a delegate. Cache key = purpose + SHA-256(text). Only cache misses go to the delegate, in one call, order preserved. LRU bounded by `cache-size`. `provider()` = delegate's. Thread-safe. |
| `AiProviderFactory` | **Abstract Factory**: `LlmClient createLlmClient()`, `EmbeddingClient createEmbeddingClient()`, `String family()`. |
| `StubAiFactory` / `CloudAiFactory` / `OllamaAiFactory` | families: stub = `StubLlmClient` + `StubEmbeddingClient`; cloud = `GroqClient` + `VoyageEmbeddingClient`; ollama = `OllamaClient` + `OllamaEmbeddingClient`. |

`LlmClientConfiguration` (refactor): `llm.provider` (`stub`\|`groq`\|`ollama`, unchanged values) picks ONE factory;
beans `LlmClient` (non-stub wrapped in the existing `RetryLlmClient`, as today) and `EmbeddingClient`
(always wrapped in `CachingEmbeddingClient`). Unknown provider -> startup failure (as today).
`.env.example` / `render.yaml` gain `VOYAGE_API_KEY` (`sync: false`).

## Part B - Knowledge tables and vector stores
Added after Part A lands.

## Part C - Ingestion and retrieval
Written after Part B lands.

## Out of scope (all parts)
Pipeline handlers, estimation, the frontend, auth/crypto internals. Never edit `src/test/`.
