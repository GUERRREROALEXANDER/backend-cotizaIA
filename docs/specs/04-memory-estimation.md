# Spec 04 - Estimate from memory, learn from approvals

## Goal
Each extracted requirement is estimated from the agency's own most similar past items (with citations), falls
back to the catalog only when memory has too little evidence, and every proposal a human approves and sends
becomes new memory with the human-corrected hours. Price stays deterministic: rates x hours, never the model.

## Judge (tests already written, do not edit them)
- `src/test/java/com/cotizaia/agent/pipeline/{EvidenceQueryBuilderTests,RetrievalEstimationTests,RetrieveHandlerTests}.java`
- `src/test/java/com/cotizaia/agent/knowledge/KnowledgeObserverTests.java`
- `src/test/java/com/cotizaia/eval/EstimationEvalTests.java` (holdout eval on the seed corpus; writes `target/eval/report.md`)
- and the **full suite**: 0 failures, 0 errors.

## Chain: Classify -> Extract -> **Retrieve** -> Estimate -> Price -> AmbiguityCheck -> Generate -> Compose
`AgentPipeline` constructor gains `RetrieveHandler` right after `ExtractHandler`:
`AgentPipeline(ClassifyHandler, ExtractHandler, RetrieveHandler, EstimateHandler, PriceHandler, AmbiguityCheckHandler, GenerateHandler, ComposeHandler, PipelineRunRecorder)`.

**`RetrievalProperties`** (`@ConfigurationProperties("pipeline.retrieval")`, JavaBean): `enabled` (true), `k` (5),
`minSimilarity` (0.15), `minNeighbors` (2).

**`EvidenceQueryBuilder`** (`@Component`, package `agent.pipeline`): `String query(String briefText, RequirementType type)`.
Keywords = the type name plus the comma-separated terms of its description, normalized (accents stripped,
lowercase, trimmed, blanks dropped). Split the brief into sentences on `.`, `!`, `?` and line breaks. Keep the
sentences containing any keyword (keywords of 4+ chars as substring, shorter ones as whole words). Result:
`"<type name>: " + kept sentences joined by a space`; if none matched, `"<type name>: " + brief` cut to 1000 chars.

**`RetrieveHandler`** (CoR, `name()` = `"Retrieve"`): ctor `(PastItemRetriever, EvidenceQueryBuilder, RetrievalProperties)`.
For each `RequirementDraft`: query = builder(context brief text = `context.evidenceText()`, draft type);
`retriever.retrieve(agencyId, query, typeName, k)`; store the hits on the draft (`setEvidence(List<RetrievedChunk>)`).
Disabled -> `proceed("memory disabled")`. **Any exception from memory -> every draft gets empty evidence and the
handler returns `flag("memory unavailable")`; it never throws** (the agent degrades to the catalog).
Otherwise `proceed("<n>/<m> requirements have past evidence")`.

**Estimation = Strategy.** `EstimationStrategy`: `Optional<Estimate> estimate(RequirementDraft draft)`.
`Estimate(BigDecimal hours, EstimateSource source, List<Citation> citations)`;
`enum EstimateSource {MEMORY, CATALOG, DEFAULT}`;
`Citation(long chunkId, String documentName, Integer page, double similarity, BigDecimal hours)`.
- `RetrievalEstimation(RetrievalProperties)`: neighbors = evidence with `hours != null` and `similarity >= minSimilarity`;
  fewer than `minNeighbors` -> empty. Hours = **similarity-weighted median** of the neighbors' hours (sort by hours,
  first value whose cumulative weight reaches half the total). Citations = the neighbors, most similar first,
  similarity rounded to 3 decimals.
- `CatalogEstimation`: the type's `estimatedHours` when not null.
- `DefaultEstimation(PipelineProperties)`: `defaultHours`, and marks `draft.setDefaultHoursUsed(true)`.
- `EstimateHandler`: primary ctor `(List<EstimationStrategy>)` used by Spring in the order
  Retrieval(@Order 1), Catalog(2), Default(3); keep the convenience ctor `EstimateHandler(PipelineProperties)` =
  Catalog + Default (existing tests use it). First present estimate wins; sets hours, source and citations on the
  draft. `flag` if any DEFAULT (as today), message counts per source, e.g. `"memory 2, catalog 1, default 0"`.

**Persistence.** `V25__requirement_estimate_source.sql`: `extracted_requirements.estimate_source VARCHAR(10) NULL`
and `estimate_citations TEXT NULL`. `ExtractedRequirement` gets `estimateSource` (enum, plaintext) and
`citations` (JSON array string, **encrypted** with `EncryptedStringConverter`). The facade copies them from drafts.
`ProposalDetailResponse.ItemResponse` gains `requirementType`, `estimateSource`,
`citations: [{chunkId, documentName, page, similarity, hours}]` (empty list when none).

## Learning loop
- `IngestionSource` gains `List<ApprovedItem> approvedItems` (last component; empty for PDFs), record
  `ApprovedItem(String requirementType, String text, BigDecimal hours)`. Update existing callers.
- `QuotedItemChunker`: one ITEM `ChunkDraft` per approved item (`text` = the item text, `page` null).
- `ApprovedProposalIngestor` (**second Factory Method subclass**): same five ctor dependencies as the PDF one;
  `createChunker()` returns `QuotedItemChunker`. `@Component`.
- `KnowledgeObserver implements ProposalObserver` (`agent.knowledge`, `channel()` = `"MEMORY"`), on a transition
  **to SENT** (a human approved it first): for each quoted item build
  `ApprovedItem(typeName, EvidenceQueryBuilder.query(briefText, type), item.getHours())` - the same text shape the
  queries use; document name `"Propuesta #<proposalId> (aprobada)"`, kind `APPROVED_PROPOSAL`,
  `sourceProposalId` set; an older document with the same `sourceProposalId` is deleted first (idempotent).
  Runs **after the surrounding transaction commits** (`TransactionSynchronization#afterCommit`, in its own
  `REQUIRES_NEW` transaction; immediately if no transaction is active). **Any exception is logged and swallowed:
  memory never blocks or rolls back a send.** Writes no notification rows.

## Out of scope
Grounded drafting of documents (spec 05), the frontend, auth/crypto. Never edit `src/test/`.
