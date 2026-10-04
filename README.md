# backend-cotizaIA

Backend + database for **CotizaIA** — an autonomous sales agent for agencies and freelancers that turns a messy client brief into a priced proposal (PDF), a draft contract, and a project schedule, ready for human review.

> Pipeline: messy brief → classified → requirements extracted → hours estimated → price calculated → proposal PDF + contract + schedule generated → approval queue (human approves in 5 min, not 3 hours).

This repo is **backend + DB only**. Frontend lives elsewhere.

## Stack

| Layer | Choice |
|---|---|
| Language / framework | Java 17 + Spring Boot 3 (Maven) |
| Database | PostgreSQL 16 on **Supabase** (free tier, permanent) |
| Migrations | Flyway |
| AI (dev) | Ollama local, via `LlmClient` |
| AI (prod) | Groq API (free), via `LlmClient` — same interface, switch by config property |
| PDF generation | OpenPDF / iText |
| Deploy | Docker → Render (free tier, $0) |
| Java version | 17 LTS |

Why Supabase Postgres over Render/Railway Postgres: Render's free database is **deleted after 30 days**; Railway has **no free tier** (one-time $5 credit). Supabase free is permanent (500 MB, pauses after ~7 days idle, restorable). App hosts on Render free; data lives on Supabase.

## Architecture

```
[Email / WhatsApp / Web form] ← Adapters normalize to Brief
                │
                ▼
         API REST (Spring Boot)
                │
                ▼
        CotizacionFacade          ← Facade: procesarBrief()
                │
   ┌────────────┼──────────────┐
   ▼            ▼              ▼
Pipeline     Pricing       Documents
(Chain of    (Strategy +   (Factory / Builder /
 Resp.)      Decorator)    Template Method)
   │            │              │
   └────────────┴──────────────┘
                ▼
        PostgreSQL (Supabase)
                │
                ▼
            Observer → Email to owner / Dashboard / Analytics
                +
         LlmClient (Adapter)
           ↙          ↘
       Ollama         Groq
```

Key rule: **the AI proposes, the human approves.** Price comes from rules over the user's own rates; the agent never closes deals. Commercial responsibility stays with the owner.

## Domain model (PostgreSQL)

Table and column names are in **English** (course requirement); content rows may be in Spanish.

```
Agency
 ├── users               (AppUser: owner / staff, role)
 ├── rates               (Role → usd_per_hour)
 ├── service_catalog → requirement_types
 ├── clients
 ├── briefs
 │     ├── extracted_requirements
 │     ├── agent_executions → agent_steps   (pipeline run log)
 │     └── proposals
 │            ├── quoted_items
 │            ├── extras                    (urgency +25%, discount −10%, warranty…)
 │            ├── schedules → phases → dependencies
 │            ├── contracts → clauses
 │            └── payments                  (50% deposit, SIMULATED)
 └── notifications
```

Proposal lifecycle (`State` pattern — illegal transitions rejected):

```
RECEIVED → ANALYZING → QUOTED → IN_REVIEW → SENT → NEGOTIATING → ACCEPTED → CONTRACT_ISSUED
                                                              ↘ REJECTED / EXPIRED
```

When the human edits hours at approval time, the schedule **recalculates in cascade**.

Simulated payment flow (no real money — academic demo):

```
Proposal ACCEPTED → 50% deposit → [ PAY ] (simulated)
✓ Payment recorded   ✓ Final contract issued   ✓ Project activated
```

## Design patterns (11)

| Category | Pattern | Lives here |
|---|---|---|
| Creational | Factory Method | `document/` — creates proposal / contract / schedule generators |
| Creational | Builder | `Proposal` aggregate (many optional fields) |
| Creational | Singleton | `RateConfiguration` — one shared rates cache |
| Structural | Facade | `CotizacionFacade.procesarBrief()` hides the whole pipeline |
| Structural | Adapter | Input channels (email/WhatsApp/form → `Brief`); `LlmClient` (Ollama/Groq) |
| Structural | Decorator | `UrgencyDecorator` (+25%), `DiscountDecorator` (−10%), warranty |
| Behavioral | Observer | Proposal state change → email + dashboard + analytics |
| Behavioral | Strategy | Pricing: fixed vs hourly vs per-phase |
| Behavioral | State | Proposal lifecycle (diagram above) |
| Investigated | Chain of Responsibility | **The agent itself**: Classify → Extract → Estimate → Price → Generate |
| Investigated | Template Method | Shared PDF skeleton (header/sections/agency footer) per generator |

The 7 load-bearing patterns: Facade, Adapter, Chain, Strategy, State, Builder, Observer. The rest are documented extensions.

## Package layout (~80 classes target)

```
com.cotizaia
├── domain/      (~25) entities: Agency, AppUser, Client, Brief, Requirement,
│                      ServiceCatalog, RequirementType, Rate, Role, Proposal,
│                      QuotedItem, Extra, Schedule, Phase, Contract, Clause,
│                      Notification, Payment, AgentExecution, AgentStep…
├── repository/  (~12) one Spring Data JPA repo per aggregate
├── service/     (~10) BriefService, PricingService, ScheduleService,
│                      NotificationService…
├── agent/       (~16) LlmClient, OllamaClient, GroqClient, PromptBuilder +
│                      7 pipeline handlers (Chain)
├── document/    (~8)  DocumentFactory, 3 Template-Method PDF generators
├── api/         (~21) 6 controllers + DTOs
└── config/      (~5)  security, Flyway, LLM provider switch, Jackson…
```

## Run locally

```bash
# 1. Database: create a free project at supabase.com, copy the connection string
# 2. AI (dev): install ollama, pull a model  →  ollama pull llama3.1
# 3. Configure:
cp src/main/resources/application-example.yml src/main/resources/application-local.yml
# edit: spring.datasource.url / username / password, llm.provider=ollama
# 4. Run (Flyway migrates automatically):
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Switch to Groq in production with one property: `llm.provider=groq` (+ `GROQ_API_KEY` env var). No code changes.

## Deploy (free)

```
docker build -t cotizaia-backend .
# Render: new Web Service from this repo, env vars:
#   DATABASE_URL (Supabase pooler), GROQ_API_KEY, LLM_PROVIDER=groq
```

## API (planned)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/briefs` | Ingest brief (triggers `procesarBrief`) |
| GET | `/api/briefs/{id}` | Brief + pipeline status + ambiguity questions |
| GET/PUT | `/api/proposals/{id}` | Review / adjust hours (schedule recalculates) |
| POST | `/api/proposals/{id}/approve` | Approve → generates PDFs |
| POST | `/api/proposals/{id}/send` | Mark sent to client |
| POST | `/api/proposals/{id}/accept` | Accept → simulated deposit → contract issued |
| CRUD | `/api/catalog`, `/api/rates`, `/api/clients` | Agency setup |
| GET | `/api/analytics` | Avg response time, acceptance rate |

## Course requirements covered

- Backend in **Java** (Spring Boot), AI integrated as a separate `agent/` component with swappable provider
- **5+ in-class patterns + 2 investigated** (Chain of Responsibility, Template Method)
- Real-life case study: interview a local agency/freelancer for before/after quoting-time numbers
- Deployed to production, $0
