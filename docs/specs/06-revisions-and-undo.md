# Spec 06 - Proposal revisions (Prototype) and undoable hour edits (Command)

## Goal
1. When a client negotiates, the agency issues **revision v2** of the sent proposal instead of mutating the
   version the client already has: v1 stays frozen for audit, v2 is a deep copy that goes back to review.
2. Every hour adjustment in the approval queue is a recorded **command** that can be undone step by step,
   with the schedule cascading back and the approval cleared, like any edit.

## Judge (tests already written, do not edit them)
- `src/test/java/com/cotizaia/domain/ProposalPrototypeTests.java`
- `src/test/java/com/cotizaia/service/AdjustHoursCommandTests.java`
- `src/test/java/com/cotizaia/api/ProposalRevisionAndUndoFlowTests.java`
- and the **full suite**: 0 failures, 0 errors.

## Prototype (`com.cotizaia.domain.Proposal`)
- `V27__proposal_revisions_and_commands.sql`: `proposals.revision_number INT NOT NULL DEFAULT 1`,
  `proposals.revision_of_id BIGINT NULL` (FK proposals), and table `proposal_commands` (below).
- `Proposal.copy()` -> a **new, unsaved** `Proposal` (`id` null) for the same brief with: status `IN_REVIEW`,
  no approval, same pricing model, valid-until, terms/schedule/extras text and `draftedContent`;
  **new** `QuotedItem`s (same requirement, hours, unit price), **new** `ProposalExtra`s, and a **new** schedule
  with the same hours-per-week, the same phases (name, share, order) and the same dependencies between the
  copied phases, re-derived from the copied items; empty status history;
  `revisionNumber = this.revisionNumber + 1`, `revisionOf = this`. Totals equal the original's.
  Getters `getRevisionNumber()`, `getRevisionOf()`.
- `POST /api/proposals/{id}/revise` (agency-scoped; foreign/missing -> 404): only from `NEGOTIATING`
  (otherwise `InvalidStateTransitionException` -> 422). Saves `copy()`, moves the original to `EXPIRED` through
  the normal lifecycle path (status history + observers), generates the revision's three PDFs, returns **201**
  with the revision's `ProposalDetailResponse` (which gains `revisionNumber` and `revisionOf` (id or null)).

## Command (`com.cotizaia.service.command`)
- `ProposalCommand`: `void execute(Proposal)`, `void undo(Proposal)`, `String description()`, `String type()`,
  `String payloadJson()`.
- `AdjustHoursCommand(long requirementId, BigDecimal before, BigDecimal after)`: execute/undo call
  `Proposal.editHours` on the line whose **requirement id** matches (the line object is replaced on every edit,
  so item ids are not stable); missing line -> `IllegalStateException`. Description e.g.
  `"Horas de Sistema de reservas: 32 -> 50"`.
- `ProposalCommandRecord` entity -> table `proposal_commands(id, proposal_id FK NOT NULL, command_type VARCHAR(40),
  payload TEXT NOT NULL, description TEXT NOT NULL, executed_at TIMESTAMPTZ NOT NULL, undone_at TIMESTAMPTZ NULL)`;
  `payload` and `description` **encrypted**.
- `CommandHistory` (`@Service`): `run(Proposal, ProposalCommand)` executes and records;
  `undoLast(Proposal)` undoes the most recent not-undone record (rebuilt from type + payload) and stamps
  `undone_at`; none left -> `IllegalStateException` (409). `list(proposalId)`.
- `ApprovalQueueService.adjustHours` goes through `CommandHistory.run(new AdjustHoursCommand(...))` (same API and
  response as today). New `POST /api/proposals/{id}/undo` (only while quote edits are allowed for the state,
  else 422; nothing to undo -> 409) returns the proposal detail; `GET /api/proposals/{id}/commands` ->
  `[{type, description, executedAt, undoneAt}]`, oldest first.

## Out of scope
Frontend, agent package, crypto internals. Do not edit anything under `src/test/`.
