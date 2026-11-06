# Spec 10 - Gemma 4 through Ollama Cloud

## Why
Groq's free tier caps tokens per day per model (200K), which a single eval exhausts. Ollama Cloud serves Gemma 4
(`gemma4:31b`) behind the same `/api/chat` contract the `OllamaClient` already speaks, plus an API key. Measured on
the seed corpus through the local daemon: 4/4 past-proposal pages extracted exactly, ~1 s per call. It ignores
`format: json` and wraps JSON in a markdown fence, which the strict handlers would reject.

## Judge
`src/test/java/com/cotizaia/agent/llm/OllamaCloudTests.java` and the full suite.

## Contract
- `LlmProperties.Ollama` gains `apiKey` (`llm.ollama.api-key: ${OLLAMA_API_KEY:}`). When non-blank, `OllamaClient`
  sends `Authorization: Bearer <key>`; when blank (local daemon) it sends no Authorization header.
- `AbstractHttpLlmClient.response(...)` gets the request's `jsonOutput` flag; when true and the content is wrapped
  in one markdown code fence (```` ``` ```` or ```` ```json ````, optional surrounding whitespace), the adapter
  returns only the inner text. Plain-text responses (jsonOutput false) are never altered. Applies to every
  HTTP provider (normalizing vendor quirks is the Adapter's job).
- New provider family (**Abstract Factory**) `OllamaCloudAiFactory`: `family()` = `"ollama-cloud"`,
  `createLlmClient()` = `OllamaClient`, `createEmbeddingClient()` = `VoyageEmbeddingClient` (Ollama Cloud hosts no
  embedding model). `llm.provider=ollama-cloud` selects it; the LLM is wrapped in `RetryLlmClient` like any network
  provider. Existing families unchanged.
- `.env.example` documents `OLLAMA_API_KEY`, and for Ollama Cloud: `LLM_PROVIDER=ollama-cloud`,
  `OLLAMA_BASE_URL=https://ollama.com`, `OLLAMA_MODEL=gemma4:31b`.

Do not edit anything under `src/test/`.
