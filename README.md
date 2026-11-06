# backend-cotizaIA

Backend + database for **CotizaIA**, an AI quoting agent for agencies and freelancers. It turns a messy client
brief (WhatsApp, email, web form or RFP PDF) into a priced proposal, contract and schedule, **estimated from the
agency's own past projects** and left in an approval queue for a human.

> Brief → classify → extract requirements → **retrieve similar past items (RAG)** → estimate hours from them,
> with citations → price with the agency's rates → AI-drafted proposal grounded in past passages, with numbered
> sources → human review (adjust, undo, approve) → send → every sent proposal becomes new memory.

**Key rule: the AI proposes, the human approves.** The model never sets a price: hours come from the agency's own
history (or its catalog), prices from its own rates through a pricing Strategy, and a validator stops any draft
that mentions money, hours or numbers the client never gave.

## Results

| | Value |
|---|---|
| Tests | 540+ (JUnit 5, Spring Boot Test, Testcontainers PostgreSQL 16 + pgvector, MockRestServiceServer) |
| Estimation error, offline eval (stub models) | **10.0 h MAE vs 21.0 h** for the fixed catalog: **52% lower** ([eval/report-stub.md](eval/report-stub.md)) |
| Estimation error, real models (Groq gpt-oss-120b + Voyage voyage-4-lite) | **9.5 h MAE vs 21.0 h**: **55% lower**, 13/13 items estimated from memory with citations ([eval/report-groq.md](eval/report-groq.md)) |
| Plaintext client data in a raw database dump | none (test scans every column of every table) |
| Credentials readable from browser JavaScript | none (HttpOnly cookies; verified by a Playwright test in the frontend repo) |

The eval corpus is **synthetic** (30 past proposals of a fictional agency, [seed/](seed/README.md)): it proves the
mechanism end to end through the public API, not the accuracy for a real agency. Pass line fixed before building:
agent MAE ≤ 0.70 × catalog MAE.

## Stack

| Layer | Choice |
|---|---|
| Language / framework | Java 17, Spring Boot 3.3 (Maven) |
| Database | PostgreSQL 16 + **pgvector** on Supabase; H2 in unit tests, real PostgreSQL in Testcontainers |
| Migrations | Flyway `V1`…`V27`, vendor-specific folder for pgvector; Hibernate only validates |
| AI | `LlmClient` (Groq / Ollama / offline stub) + `EmbeddingClient` (Voyage / Ollama / stub), one provider family per deploy |
| Security | Spring Security, JWT in HttpOnly cookies, CSRF double submit, rotating refresh tokens, AES-256-GCM field encryption, rate limits |
| PDF | OpenPDF (generate), PDFBox (read) |
| API docs | springdoc-openapi (off in production unless `SWAGGER_ENABLED=true`) |
| CI | GitHub Actions: full suite, pgvector tests must not be skipped, gitleaks |
| Deploy | Docker → Render (free), Supabase (free) |

## How the AI is used

```
                    ┌──────────── agency memory (pgvector, per agency) ────────────┐
past proposal PDFs ─┤ grounded item extraction: an item counts only if its hours     │
sent proposals ─────┤ literally appear on its page; passages for drafting          │
(Observer)          └──────────────────────────────────────────────────────────────┘
                                   ▲ retrieve (Voyage embeddings, cosine)
brief ─▶ Classify ─▶ Extract ─▶ Retrieve ─▶ Estimate ─▶ Price ─▶ AmbiguityCheck ─▶ Generate ─▶ Compose
         (LLM)       (LLM)                 memory →     rates     rules               LLM draft
                                           catalog →    (no AI)                       + validator
                                           default                                    + citations
```

- **Estimation from memory (RAG).** For each requirement the agent builds a query from the brief sentences that
  mention it, retrieves the k most similar past items of the same type in the agency's memory and takes their
  **similarity-weighted median** of approved hours. Fewer than 2 similar items → the catalog value; no catalog
  value → a default flagged for review. Every estimate stores its source and citations
  (document, page, similarity, hours) and the approval queue shows them.
- **Learning from corrections.** When a human approves and sends a proposal, an Observer indexes the final,
  human-corrected hours after the transaction commits. The next similar brief is estimated from them.
- **Grounded drafting.** The proposal body is drafted by the model from the brief and the agency's past passages.
  Each paragraph cites numbered sources. `ProposalDraftValidator` rejects money words, hour figures, numbers absent
  from the brief, and citations outside the retrieved set; one retry with the violations, then a safe template.
- **Guardrails against prompt injection.** Briefs, PDFs and passages are wrapped in tags and declared data, model
  output is schema-validated, grounded and never rendered as HTML, and nothing the model writes reaches a price.
- **Degrades instead of failing.** A memory outage flags the run and estimates from the catalog; provider rate
  limits are retried honoring `Retry-After`; a failed run is logged with its execution id, step and cause, never
  with client text.

What the real-model runs taught (each fix is in the history with its evidence):
1. The first run tied the catalog: a schema-only prompt made the model list every catalog entry with `"hours":0`;
   the grounding check rejected all 240 invented items instead of storing them. The prompt now states the task.
2. Free-tier limits (Groq 8K tokens/min, Voyage 3 requests/min) turned into 503s: retries now honor `Retry-After`,
   and a failed run logs its step and cause.
3. Groq's JSON mode sometimes answers 400 `json_validate_failed`: retried as the sampling accident it is.
4. gpt-oss hidden reasoning burned the 200K tokens/day budget: reasoning effort is now configurable (`low`), which
   keeps extraction exact at ~1K tokens per call.

## Security

| Threat | Control | Proven by |
|---|---|---|
| Database dump / backup leak | AES-256-GCM on every client and agency content column (key id inside the ciphertext for rotation); HMAC blind index for email lookups | `EncryptionAtRestTests` (raw JDBC scan of all tables) |
| Token theft from the browser (XSS, devtools) | JWT (15 min) and refresh token (7 days, stored as SHA-256) only in `HttpOnly; Secure; SameSite=Strict` cookies; no token in any response body | `AuthCookieTests`, frontend Playwright test |
| CSRF | Double-submit `XSRF-TOKEN` on every cookie-authenticated write | `AuthCookieTests` |
| Refresh token replay | Rotation with reuse detection: a replayed token revokes its whole family | `AuthCookieTests` |
| Brute force / cost abuse | 429 + `Retry-After`: per client IP on auth, per agency on every LLM endpoint | `RateLimitTests` |
| Cross-tenant access | Every query and vector search filtered by the caller's agency; foreign ids answer 404 | controller tests, `VectorStoreContract` |
| Leaking internals | No stack traces or exception messages in responses; docs and actuator details off in production | `ProductionProfileTests` |
| Secrets in git | Everything from the environment; gitleaks in CI; app refuses to start without its keys | CI, `CryptoConfigurationTests` |

**Data classification.** Encrypted: agency and user names and emails, client name/email/company, brief text and
payload, uploaded files, questions and answers, extracted requirements, agent step summaries, proposal texts,
drafts and PDFs, command history, notifications, knowledge passages and document names. Plaintext on purpose:
ids, foreign keys, statuses, dates, hours and amounts (aggregated in SQL by analytics and pricing), catalog/role
names (agency configuration looked up by name), contract clause templates, embedding vectors (similarity search
needs them; mitigated by per-agency filtering and database credentials).

Limitation: rate-limit counters live in memory, per instance. With more than one instance they need a shared store
(Redis / Bucket4j). Render free runs one.

## Design patterns

17 GoF patterns, each in real behavior and each with a test that checks its defining property.

| Category | Pattern | Where | Test |
|---|---|---|---|
| Creational | Singleton | `config.RateConfiguration` (holder idiom, shared rate cache) | `RateConfigurationSingletonTests` |
| Creational | Builder | `domain.Proposal.Builder` | `ProposalBuilderTests` |
| Creational | Factory Method | `agent.knowledge.KnowledgeIngestor.createChunker()` → PDF / approved-proposal chunkers | `ProposalPdfIngestorTests` |
| Creational | Abstract Factory | `agent.embedding.AiProviderFactory` → `{LlmClient, EmbeddingClient}` families (stub / cloud / ollama) | `AiProviderFactoryTests` |
| Creational | Prototype | `domain.Proposal.copy()`: revision v2 of a negotiated proposal, deep copy, v1 frozen | `ProposalPrototypeTests` |
| Structural | Adapter | `GroqClient` / `OllamaClient` behind `LlmClient`; `VoyageEmbeddingClient` behind `EmbeddingClient`; brief channel adapters; `SimulatedPaymentGateway` | `GroqClientTests`, `VoyageEmbeddingClientTests`, `BriefSourceAdapterTests` |
| Structural | Bridge | `KnowledgeRetriever` (past items / passages) × `VectorStore` (pgvector / portable JDBC) | `KnowledgeRetrieverTests`, `VectorStoreContract` on H2 and real PostgreSQL |
| Structural | Composite | `document.DocumentNode`: section / paragraph / bullet list tree of the drafted PDF | `DocumentNodeTests` |
| Structural | Decorator | Price extras (urgency, discount, warranty); `RetryLlmClient`; `RetryEmbeddingClient` | `ProposalPricingDecoratorTests`, `RetryLlmClientTests`, `RetryEmbeddingClientTests` |
| Structural | Facade | `facade.QuotationFacade.processBrief()` | `QuotationFacadeIntegrationTests` |
| Structural | Proxy | `agent.embedding.CachingEmbeddingClient` (LRU cache in front of the paid embedder) | `CachingEmbeddingClientTests` |
| Behavioral | Chain of Responsibility | 8 pipeline handlers, Classify → … → Compose | `PipelineHandlerTests`, `AgentPipelineIntegrationTests` |
| Behavioral | Command | `service.command.AdjustHoursCommand` + `CommandHistory` (undo hour edits) | `AdjustHoursCommandTests`, `ProposalRevisionAndUndoFlowTests` |
| Behavioral | Observer | `notification.ProposalSubject` → email, dashboard, analytics and `KnowledgeObserver` (learning loop) | `ProposalSubjectTests`, `KnowledgeObserverTests` |
| Behavioral | State | `domain.state.*`: 10 proposal states, illegal moves → 422 | `ProposalStateTransitionTests` |
| Behavioral | Strategy | Pricing (hourly / fixed / phased); estimation (memory → catalog → default) | `PricingStrategyTests`, `RetrievalEstimationTests` |
| Behavioral | Template Method | `AbstractDocumentTemplate`; `PipelineRunRecorder`; `KnowledgeIngestor.ingest()` | `DocumentGeneratorTests`, `PipelineRunRecorderTests` |

`document.DocumentFactory` is a Simple Factory (a registry), labelled as such.

## Run locally

```bash
# 1. PostgreSQL 16 + pgvector
docker compose up -d

# 2. Keys (no defaults: the app refuses to start without them)
export DATA_ENCRYPTION_KEY=$(openssl rand -base64 32) BLIND_INDEX_KEY=$(openssl rand -base64 32)

# 3. Offline, deterministic AI (no network)
LLM_PROVIDER=stub ./mvnw spring-boot:run
# Real models: LLM_PROVIDER=groq with GROQ_API_KEY and VOYAGE_API_KEY
```

- Tests: `./mvnw test` (Docker needed for the PostgreSQL + pgvector tests; they are skipped without it locally,
  and CI fails if they are skipped).
- Real-model eval (~20 min on free tiers): `GROQ_API_KEY=… VOYAGE_API_KEY=… ./mvnw test -Dtest=EstimationEvalTests -Deval.provider=groq`
  → `target/eval/report.md`.
- Seed corpus: `python3 seed/tools/gen_manifest.py && python3 seed/tools/check_content.py && python3 seed/tools/render_pdfs.py`.
- All variables: [`.env.example`](.env.example). Specs for every feature: [`docs/specs/`](docs/specs/).

## API

Every endpoint except auth, health and docs needs a session and only sees the caller's agency. Browsers use the
cookies; scripts may send `Authorization: Bearer <token>` instead (no CSRF needed then).

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` · `/login` | Create agency + owner / log in. Sets `cz_access` + `cz_refresh` HttpOnly cookies; the body has no token |
| POST | `/api/auth/refresh` · `/logout` | Rotate / revoke the session (CSRF required) |
| GET | `/api/auth/csrf` · `/me` | Issue the `XSRF-TOKEN` cookie / current identity |
| POST | `/api/quotations` | Star path: ingest a brief and run the whole agent in one call |
| GET · POST | `/api/briefs` | List (newest first) / ingest a brief (EMAIL, WHATSAPP, WEB_FORM) |
| POST | `/api/briefs/upload` | Brief from an RFP PDF |
| GET | `/api/briefs/{id}` · `/{id}/attachment` | Detail with questions and executions / original PDF |
| POST | `/api/briefs/{id}/process` · `/{id}/questions/{qid}/answer` | Run (again) / answer a question and re-run |
| POST · GET · DELETE | `/api/knowledge/documents` | Upload a past proposal PDF as memory / list / delete |
| GET | `/api/knowledge/search?q=&type=&k=` | Search the agency's memory (with citations) |
| GET | `/api/proposals` · `/queue` · `/{id}` | List / approval queue / detail with items, estimate sources, citations, draft, history, documents |
| PUT | `/api/proposals/{id}/items/{itemId}` | Adjust hours (schedule cascades; recorded as an undoable command) |
| POST | `/api/proposals/{id}/undo` · GET `/{id}/commands` | Undo the last edit / edit history |
| POST | `/api/proposals/{id}/approve` · `/send` · `/negotiate` · `/accept` · `/reject` · `/expire` | Lifecycle (State pattern) |
| POST | `/api/proposals/{id}/revise` | Revision v2 of a negotiated proposal (Prototype) |
| GET | `/api/proposals/{id}/documents` · `/documents/{type}` | PROPOSAL, CONTRACT, SCHEDULE PDFs |
| CRUD | `/api/catalog`, `/api/roles`, `/api/rates`, `/api/clients`, `/api/agency/pricing-model` | Agency configuration |
| GET | `/api/analytics` · `/api/notifications` · `/api/executions` | Dashboard, feed, agent run log |
| GET | `/actuator/health` | Health check (public, no details) |

Errors are always JSON `{"error": "..."}`: 400 validation, 401 no session, 403 missing CSRF token, 404 not found
or another agency's, 409 conflict, 413 file too large, 422 illegal lifecycle move, 429 rate limited (with
`Retry-After`), 503 agent run failed (with `executionId`).

## Deploy (free)

1. Supabase: create the project; `V24` enables `vector`. **Start from an empty database**: rows written before
   encryption at rest cannot be decrypted.
2. Render → New → Blueprint with this repo (`render.yaml`). Secrets: `SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` (≥ 32 chars), `DATA_ENCRYPTION_KEY`,
   `BLIND_INDEX_KEY` (`openssl rand -base64 32` each), `GROQ_API_KEY`, `VOYAGE_API_KEY`, `CORS_ALLOWED_ORIGINS`.
3. The frontend static site proxies `/api/*` to the backend (same origin, so `SameSite=Strict` cookies work) and
   sends a strict CSP and security headers.
4. Seed the demo memory: upload the PDFs in `seed/pdf/train/` through `POST /api/knowledge/documents`.

Defense day: Render free sleeps after ~15 min idle (open `/actuator/health` a few minutes before); Supabase free
pauses after ~7 idle days; `LLM_PROVIDER=stub` keeps the demo running without network.

## Simulated payments

No real money moves. Accepting a proposal records a 50% deposit through `SimulatedPaymentGateway`; database
`CHECK` constraints force `provider = 'SIMULATED'`, and every PDF footer says so.
