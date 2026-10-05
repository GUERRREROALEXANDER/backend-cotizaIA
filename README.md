# backend-cotizaIA

Backend + database for **CotizaIA** — an autonomous sales agent for agencies and freelancers that turns a messy client brief into a priced proposal (PDF), a draft contract, and a project schedule, ready for human review.

> Pipeline: messy brief → classified → requirements extracted → hours estimated → price calculated → proposal PDF + contract + schedule generated → approval queue (human approves in 5 min, not 3 hours).

This repo is **backend + DB only**. Frontend lives elsewhere (see [Connecting a frontend](#connecting-a-frontend)).

## Stack

| Layer | Choice |
|---|---|
| Language / framework | Java 17 + Spring Boot 3.3 (Maven) |
| Database | PostgreSQL 16 on **Supabase** (free tier, permanent); H2 in tests |
| Migrations | Flyway (`V1`…`V19`), Hibernate only validates |
| Security | Spring Security, stateless JWT (HS256), BCrypt |
| AI (dev) | Ollama local, or the offline `stub` provider |
| AI (prod) | Groq API (free) — same `LlmClient` interface, switched by `LLM_PROVIDER` |
| PDF generation | OpenPDF |
| API docs | springdoc-openapi (Swagger UI) |
| Deploy | Docker → Render (free tier, $0) |

Why Supabase Postgres over Render/Railway Postgres: Render's free database is **deleted after 30 days**; Railway has **no free tier**. Supabase free is permanent (500 MB, pauses after ~7 days idle, restorable). App hosts on Render free; data lives on Supabase.

## Architecture

```
[Email / WhatsApp / Web form] ← Adapters normalize to Brief
                │
                ▼
     API REST (thin controllers + DTOs, JWT, agency-scoped)
                │
                ▼
        QuotationFacade          ← Facade: processBrief()
                │
   ┌────────────┼──────────────┐
   ▼            ▼              ▼
AgentPipeline Pricing       Documents
(Chain of    (Strategy +   (Factory +
 Resp.)      Decorator)    Template Method)
   │            │              │
   └────────────┴──────────────┘
                ▼
        PostgreSQL (Supabase)
                │
                ▼
   ProposalSubject (Observer) → owner email (simulated) / dashboard / analytics
                +
         LlmClient (Adapter)
           ↙     ↓      ↘
       Ollama   Groq   Stub
```

`QuotationFacade.processBrief()` is the facade the original plan called `CotizacionFacade.procesarBrief()`.

Key rule: **the AI proposes, the human approves.** Hours come from the agency's own catalog and the price from its own rates through a pricing Strategy; the agent never sets a price and never closes a deal.

**Never half-write.** The agent run log (`agent_executions` + `agent_steps`) commits in its own transaction, so a failed run stays visible; business rows (requirements, quote, schedule, contract draft, PDFs, questions) are written in a separate short transaction only after the chain succeeds.

## Domain model (PostgreSQL)

Table and column names are in **English**; content rows may be in Spanish.

```
Agency (pricing_model)
 ├── users                (AppUser: owner / staff, login_email + BCrypt password_hash)
 ├── roles → rates        (role → COP per hour, effective dating)
 ├── service_catalog → requirement_types   (keywords + estimated hours)
 ├── clients
 │     └── briefs
 │           ├── brief_questions           (ambiguity questions, OPEN / RESOLVED)
 │           ├── extracted_requirements
 │           ├── agent_executions → agent_steps   (pipeline run log)
 │           └── proposals (status, pricing_model, approved_at)
 │                  ├── quoted_items
 │                  ├── proposal_extras         (urgency +25%, discount −10%, warranty…)
 │                  ├── proposal_status_changes (audit trail of the State machine)
 │                  ├── proposal_documents      (3 PDFs: proposal, contract, schedule)
 │                  ├── schedules → phases → phase_dependencies
 │                  ├── contracts → clauses
 │                  └── payments                (50% deposit, SIMULATED)
 └── notifications        (one row per observer per state change)
```

Proposal lifecycle (`State` pattern — illegal transitions return **422**):

```
RECEIVED → ANALYZING → QUOTED → IN_REVIEW → SENT → NEGOTIATING → ACCEPTED → CONTRACT_ISSUED
                                     ↘ REJECTED      ↘ REJECTED
            (EXPIRED reachable from every open state)
```

When the human edits hours in the approval queue, the schedule **recalculates in cascade** and the approval is cleared until the quote is approved again.

## Design patterns (implemented)

| Category | Pattern | Where (real classes) |
|---|---|---|
| Creational | Builder | `domain.Proposal.Builder` |
| Creational | Singleton | `config.RateConfiguration` (shared rate cache) |
| Creational | Factory | `document.DocumentFactory` — returns the generator for a `DocumentType` (EnumMap, no branching) |
| Structural | Facade | `facade.QuotationFacade.processBrief()` |
| Structural | Adapter | Input channels `ingest.*BriefAdapter`; LLM `agent.llm.OllamaClient` / `GroqClient` / `StubLlmClient` behind `LlmClient`; `payment.SimulatedPaymentGateway` behind `PaymentGateway` |
| Structural | Decorator | Price extras `domain.UrgencyDecorator` / `DiscountDecorator` / `ExtendedWarrantyDecorator`; `agent.llm.RetryLlmClient` (retries around any `LlmClient`) |
| Behavioral | State | `domain.state.ProposalState` + 10 concrete states; `Proposal` is the context |
| Behavioral | Observer | `notification.ProposalSubject` → `OwnerEmailObserver`, `DashboardObserver`, `AnalyticsObserver` |
| Behavioral | Strategy | `pricing.PricingStrategy` → `HourlyStrategy`, `FixedPriceStrategy`, `PhasedStrategy`, chosen by `PricingStrategyResolver` |
| Behavioral | Chain of Responsibility | `agent.pipeline.PipelineHandler` → Classify → Extract → Estimate → Price → AmbiguityCheck → Generate → Compose |
| Behavioral | Template Method | `document.AbstractDocumentTemplate` (shared header/footer, per-document sections); `agent.PipelineRunRecorder` (run-log skeleton) |

## Run locally

```bash
# 1. Database (Docker) — or point SPRING_DATASOURCE_* at Supabase
docker compose up -d

# 2. Run with the offline AI stub (no internet, deterministic answers)
LLM_PROVIDER=stub ./mvnw spring-boot:run          # PowerShell: $env:LLM_PROVIDER='stub'; .\mvnw.cmd spring-boot:run

# Optional: real local AI
#   ollama pull llama3.1  &&  LLM_PROVIDER=ollama ./mvnw spring-boot:run
```

- API: `http://localhost:8080` · Swagger UI: `http://localhost:8080/swagger-ui.html` · Health: `/actuator/health`
- Flyway creates the schema on startup. Without `JWT_SECRET` a random dev key is generated (tokens die on restart).
- Tests (H2, stub AI, no external services): `./mvnw test`
- All variables are listed in [`.env.example`](.env.example).

## API

All endpoints except auth, health and Swagger require `Authorization: Bearer <token>` and only see the caller's agency.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create agency + owner, returns token |
| POST | `/api/auth/login` | Email + password → token |
| GET | `/api/auth/me` | Current user / agency |
| POST | `/api/quotations` | **Star path**: ingest a brief and run the whole pipeline in one call |
| POST | `/api/briefs` | Ingest a brief only (EMAIL / WHATSAPP / WEB_FORM payloads) |
| GET | `/api/briefs/{id}` | Brief + ambiguity questions + latest proposal + executions |
| POST | `/api/briefs/{id}/process` | Run (or re-run) the pipeline on a brief |
| POST | `/api/briefs/{id}/questions/{questionId}/answer` | Answer a question; the chain re-runs |
| GET | `/api/proposals` (`?status=`) | Agency proposals |
| GET | `/api/proposals/queue` | Approval queue (IN_REVIEW, oldest first) |
| GET | `/api/proposals/{id}` | Detail: items, totals, schedule, history, documents, payment |
| PUT | `/api/proposals/{id}/items/{itemId}` | Adjust hours (schedule cascades) |
| POST | `/api/proposals/{id}/approve` | Human approval → final PDFs regenerated |
| POST | `/api/proposals/{id}/send` · `/negotiate` · `/reject` · `/expire` | Lifecycle moves (State pattern) |
| POST | `/api/proposals/{id}/accept` | Accept → simulated 50% deposit → contract issued |
| GET | `/api/proposals/{id}/documents` · `/documents/{type}` | List / download PDFs (PROPOSAL, CONTRACT, SCHEDULE) |
| CRUD | `/api/catalog`, `/api/catalog/{id}/requirement-types` | Service catalog |
| CRUD | `/api/roles`, `/api/roles/{id}/rates`, `/api/rates/{id}` | Roles and hourly rates |
| GET/PUT | `/api/agency/pricing-model` | Default pricing Strategy (FIXED / HOURLY / PHASED) |
| CRUD | `/api/clients` | Clients |
| GET | `/api/analytics` | Avg response time, acceptance rate, proposals by status |
| GET | `/api/notifications` | Dashboard feed |
| GET | `/api/executions?briefId=` · `/api/executions/{id}` | Agent run log (live pipeline view) |
| GET | `/actuator/health` | Health check (public) |

The full, always-current contract is the OpenAPI document at `/v3/api-docs`.

## Connecting a frontend

1. **Auth:** `POST /api/auth/register` or `/api/auth/login` → store `token`; send `Authorization: Bearer <token>` on every call. Tokens expire after `JWT_TTL` (default 8 h) → re-login on 401.
2. **CORS:** set `CORS_ALLOWED_ORIGINS` to the exact frontend origins, comma-separated (e.g. `http://localhost:5173,https://cotizaia.vercel.app`). No wildcard is allowed.
3. **Client generation:** point OpenAPI Generator / Orval at `/v3/api-docs` to get typed clients.
4. **Errors:** always JSON `{"error": "...", "fields": {...}}` (`fields` only on validation).

| Status | Meaning |
|---|---|
| 400 | Validation failed / malformed JSON |
| 401 | Missing, invalid or expired token; wrong credentials |
| 404 | Not found **or belongs to another agency** |
| 409 | Conflict (duplicate email, data in use, not approved yet) |
| 422 | Illegal lifecycle move (includes `from` and `to`) |
| 503 | Agent pipeline failed (includes `executionId` to inspect the run log) |

5. **PDFs:** `GET /api/proposals/{id}/documents/{type}` returns `application/pdf` with `Content-Disposition` (exposed by CORS).

## Database

- Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs `ddl-auto: validate`, so the app refuses to start against an out-of-date schema.
- **Supabase:** Project settings → Database → Connection pooling. Use the JDBC form:
  `SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require`,
  `SPRING_DATASOURCE_USERNAME=postgres.<project-ref>`, `SPRING_DATASOURCE_PASSWORD=<password>`.
- **Local:** `docker compose up -d` (Postgres 16, user/db/password `cotizaia`, matching `application.yml`).

## Deploy (free)

1. Push to GitHub, then in Render: **New → Blueprint** and select this repo (`render.yaml`).
2. Fill the secret variables Render asks for: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `GROQ_API_KEY`, `JWT_SECRET` (≥ 32 random chars), `CORS_ALLOWED_ORIGINS`.
3. Render builds the `Dockerfile` (profile `prod`, heap capped for 512 MB) and probes `/actuator/health`.

**Live URL:** _pending first deploy_

### Defense-day checklist

- Render free **sleeps after ~15 min idle**: the first request takes ~30–60 s. Open `/actuator/health` a few minutes before presenting.
- Supabase free **pauses after ~7 days idle**: open the Supabase dashboard the day before and restore the project if paused.
- Keep `LLM_PROVIDER=stub` as a fallback if Groq is unreachable — the demo still runs end to end.

## Demo script (star path, issue #21)

```bash
BASE=http://localhost:8080
# 1. Register → token
TOKEN=$(curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"agencyName":"Agencia Demo","ownerFullName":"Ana","email":"ana@demo.co","password":"secret123"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

# 2. Setup: client, catalog (keywords drive extraction), roles + rates
curl -s -X POST $BASE/api/clients -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"Restaurante El Sabor"}'
curl -s -X POST $BASE/api/catalog -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"Desarrollo web"}'
curl -s -X POST $BASE/api/catalog/1/requirement-types -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Sistema de reservas","description":"reserva, reservas, agenda","estimatedHours":32}'
curl -s -X POST $BASE/api/roles -H "$AUTH" -H 'Content-Type: application/json' -d '{"name":"Developer"}'
curl -s -X POST $BASE/api/roles/1/rates -H "$AUTH" -H 'Content-Type: application/json' -d '{"copPerHour":100000}'

# 3. One call: brief → quoted proposal + 3 PDFs + ambiguity questions
curl -s -X POST $BASE/api/quotations -H "$AUTH" -H 'Content-Type: application/json' -d '{"source":"WEB_FORM","clientId":1,
 "payload":{"description":"Tengo un restaurante y necesito una pagina web donde vean el menu y hagan reservas en linea"}}'
#   → proposalStatus IN_REVIEW, total > 0, documents [PROPOSAL, CONTRACT, SCHEDULE],
#     questions PAGE_COUNT_MISSING + DEADLINE_MISSING (non-blocking)

# 4. Human review: queue → approve → send → accept
curl -s $BASE/api/proposals/queue -H "$AUTH"
curl -s -X POST $BASE/api/proposals/1/approve -H "$AUTH"
curl -s -X POST $BASE/api/proposals/1/send -H "$AUTH"
curl -s -X POST $BASE/api/proposals/1/accept -H "$AUTH"     # → CONTRACT_ISSUED, deposit = 50% total, simulated: true
curl -s $BASE/api/analytics -H "$AUTH"                      # → sentCount 1, acceptedCount 1, acceptanceRate 1.0000
```

(With the default `stub` provider the extraction is keyword-based and fully reproducible.)

## Simulated payments

**No real money moves.** Accepting a proposal records a 50% deposit through `SimulatedPaymentGateway`, which never contacts any processor. Database `CHECK` constraints force `provider = 'SIMULATED'` and `simulated = TRUE`, and every PDF footer states it.

## Course requirements covered

- Backend in **Java** (Spring Boot), AI isolated in `agent/` with a swappable provider (a test enforces that `domain`, `pricing`, `service`, `document`, `notification` and `payment` never import it)
- **11 design patterns** implemented in real behavior (table above), including the investigated Chain of Responsibility and Template Method
- Real-life case study: interview a local agency/freelancer for before/after quoting-time numbers
- Deployable to production at $0 (Render + Supabase + Groq)
