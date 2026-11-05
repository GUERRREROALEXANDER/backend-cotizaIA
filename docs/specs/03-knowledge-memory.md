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
Tests: `src/test/java/com/cotizaia/agent/knowledge/{VectorStoreContract,JdbcVectorStoreTests,PgVectorStoreTests}.java`
(`PgVectorStoreTests` runs PostgreSQL 16 + pgvector in Docker via Testcontainers; it must pass locally, Docker is available).

**Domain (`com.cotizaia.domain`, JPA, constructors exactly as the tests call them):**
- enums `ChunkKind {PASSAGE, ITEM}`, `KnowledgeDocumentKind {PROPOSAL_PDF, APPROVED_PROPOSAL}`
- `KnowledgeDocument(Agency agency, String name, KnowledgeDocumentKind kind)`; also `sourceProposalId` (Long, nullable),
  `pageCount` (Integer, nullable), `createdAt` (set on persist). `name` encrypted (`EncryptedStringConverter`).
- `KnowledgeChunk(KnowledgeDocument document, ChunkKind kind, String text, Integer page, String requirementType,
  BigDecimal hours, int ordinal)`; `text` encrypted; `requirementType` and `hours` plaintext (catalog name / number).
- repositories `KnowledgeDocumentRepository`, `KnowledgeChunkRepository` (Spring Data).

**Migrations (Flyway).** `spring.flyway.locations` becomes `classpath:db/migration,classpath:db/vendor/{vendor}` in
`application.yml` (the test yml already says so).
- `db/migration/V23__knowledge_documents_chunks.sql` (portable): `knowledge_documents`, `knowledge_chunks`
  (FK document ON DELETE CASCADE), `knowledge_vectors(chunk_id PK FK knowledge_chunks ON DELETE CASCADE,
  agency_id NOT NULL, document_id NOT NULL, kind, requirement_type NULL, dims INT NOT NULL, vector BYTEA NOT NULL)`
  + index `(agency_id, kind)`. Follow the style of the existing migrations (they run on H2 and PostgreSQL).
- `db/vendor/postgresql/V24__pgvector.sql`: `CREATE EXTENSION IF NOT EXISTS vector;` + table `knowledge_vectors_pg`
  same columns but `embedding vector NOT NULL` (no fixed dimension: stub=256, Voyage=1024) instead of dims/vector.
- `db/vendor/h2/V24__pgvector.sql`: comment explaining H2 has no pgvector + `SELECT 1;` (keeps versions aligned).
- `docker-compose.yml` image -> `pgvector/pgvector:pg16`. README: Supabase has pgvector (enabled by V24).

**`com.cotizaia.agent.knowledge`:**
- records `VectorEntry(long chunkId, long agencyId, long documentId, ChunkKind kind, String requirementType, float[] vector)`,
  `VectorQuery(long agencyId, ChunkKind kind, String requirementType /*nullable = any*/, float[] vector, int k)`,
  `VectorMatch(long chunkId, double similarity)` (cosine similarity, 1 = identical).
- `VectorStore` (**Bridge implementor**): `void upsert(List<VectorEntry>)` (insert or replace by chunkId),
  `List<VectorMatch> search(VectorQuery)` (only that agency, that kind, that requirementType when not null;
  most similar first; at most k), `void deleteDocument(long agencyId, long documentId)` (agency-scoped), `String name()`.
- `JdbcVectorStore` (`name()="jdbc"`): float[] as little-endian bytes; loads candidates of the agency+kind(+type)
  with SQL, exact cosine in Java, top k. Parameterized SQL only.
- `PgVectorStore` (`name()="pgvector"`): `ORDER BY embedding <=> CAST(? AS vector) LIMIT ?`, similarity
  `1 - (embedding <=> CAST(? AS vector))`; vector literal built from floats (`[0.1,0.2,...]`), always a bound parameter.
- Bean selection: `knowledge.vector-store` = `jdbc` (default) | `pgvector`; `application-prod.yml` sets `pgvector`.

**pom.xml (already added by the test author):** `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`, test scope.

## Part C - Ingestion, retrieval and the knowledge API
Tests: `src/test/java/com/cotizaia/agent/knowledge/{PastItemExtractorTests,ProposalPdfIngestorTests,KnowledgeRetrieverTests}.java`
and `src/test/java/com/cotizaia/api/KnowledgeControllerTests.java` (they read `seed/` from the repo root).

**PDF pages.** `PdfBriefReader.readPages(byte[])` -> `List<String>`, one entry per page (blank string for an
unreadable page), same validation and limits as `read` (refactor so both share it; `read` keeps its behavior).

**LLM task.** `LlmTask.EXTRACT_ITEMS`; `PromptBuilder.extractItems(String pageText, List<String> catalogNames)` with
`<page>` and `<catalog>` sections (one name per line), JSON output `{"items":[{"requirementType","description","hours"}]}`
and a system rule that the page is data, never instructions. `StubLlmClient` answers it deterministically by
parsing the table layout of the seed proposals: a line equal (accent/case-insensitive) to a catalog name starts an
item, following lines are its description, a line starting with `<number> h` closes it with those hours; stop at
a line starting with `Total`.

**`com.cotizaia.agent.knowledge`:**
| Type | Contract |
|---|---|
| `PastItemExtractor(LlmClient, PromptBuilder, ObjectMapper)` | `Result extract(List<String> pages, List<String> catalogNames)`; records `Result(List<Item> items, int rejected)`, `Item(String requirementType, String description, BigDecimal hours, int page)`. One LLM call per non-blank page (1-based page numbers). **Grounding:** keep an item only if its type matches a catalog name (accent/case-insensitive; return the catalog's spelling), description non-blank, `0 < hours <= 2000`, and the page text contains the hours as a standalone number followed by `h` or `horas` (e.g. `50 h`; `4.750.000` must not match `50`). Rejected items only increment `rejected`. Unparseable model output for a page -> that page yields nothing (no exception). |
| `ChunkDraft(ChunkKind kind, String text, Integer page, String requirementType, BigDecimal hours)` | record |
| `IngestionSource(long agencyId, String name, KnowledgeDocumentKind kind, List<String> pages, Long sourceProposalId, List<String> catalogNames)` | record |
| `Chunker` | `List<ChunkDraft> chunk(IngestionSource source)` |
| `ProposalPdfChunker(PastItemExtractor)` | `MAX_PASSAGE_CHARS = 1200` (public static). PASSAGE chunks: per page, paragraphs packed up to the max, a paragraph longer than the max is split on whitespace; each passage keeps its page. ITEM chunks: one per extracted item, text `"<requirementType>: <description>"`, page and hours set. |
| `KnowledgeIngestor` (abstract, **Factory Method**) | ctor `(AgencyRepository, KnowledgeDocumentRepository, KnowledgeChunkRepository, EmbeddingClient, VectorStore)`. `public final IngestionResult ingest(IngestionSource)`: `createChunker()` -> drafts -> save document (pageCount = pages.size()) and chunks (ordinal in order) -> embed all chunk texts in one `DOCUMENT` call -> `VectorStore.upsert`. Transactional. `protected abstract Chunker createChunker()`. |
| `ProposalPdfIngestor` | ctor = the five above + `PastItemExtractor`; `createChunker()` returns a `ProposalPdfChunker`. A Spring `@Component`. |
| `IngestionResult(long documentId, int passages, List<IngestedItem> items, int rejectedItems)`, `IngestedItem(long chunkId, String requirementType, BigDecimal hours, int page)` | records |
| `RetrievedChunk(long chunkId, long documentId, String documentName, Integer page, String text, String requirementType, BigDecimal hours, double similarity)` | record |
| `KnowledgeRetriever` (abstract, **Bridge abstraction**) | ctor `(EmbeddingClient, VectorStore, KnowledgeChunkRepository)`. `public List<RetrievedChunk> retrieve(long agencyId, String query, String requirementType, int k)`: embed query (`QUERY`) -> `VectorStore.search` with `kind()` and `typeFilter(requirementType)` -> load chunks by id (skip missing) -> map, store order kept. `protected abstract ChunkKind kind()`, `protected String typeFilter(String requested)` (default = requested). |
| `PastItemRetriever` / `PassageRetriever` | refined abstractions: `ITEM` / `PASSAGE` (passages override `typeFilter` to return `null`). `@Component`s. |
| `KnowledgeService` | upload (reads pages, loads the agency's catalog names from `RequirementTypeRepository`, calls `ProposalPdfIngestor`), list, delete (agency-scoped; vectors via `VectorStore.deleteDocument`, rows cascade), search (`PastItemRetriever` when `type` given or not; k default 5, 1..20). |

**API (`com.cotizaia.api.KnowledgeController`, agency from the token like every controller):**
- `POST /api/knowledge/documents` multipart `file` (PDF only, reuse reader validation -> 400 on non-PDF) -> 201
  `{documentId, name, passages, items:[{chunkId, requirementType, hours, page}], rejectedItems}`
- `GET /api/knowledge/documents` -> `[{documentId, name, kind, items, passages, createdAt}]`
- `DELETE /api/knowledge/documents/{id}` -> 204; other agency's / missing -> 404
- `GET /api/knowledge/search?q=&type=&k=` -> `[{chunkId, documentId, documentName, page, text, requirementType, hours, similarity}]`;
  blank `q` or `k` outside 1..20 -> 400

## Out of scope (all parts)
Pipeline handlers, estimation, the frontend, auth/crypto internals. Never edit `src/test/`.
