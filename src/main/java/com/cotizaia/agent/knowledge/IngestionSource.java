package com.cotizaia.agent.knowledge;

import com.cotizaia.domain.KnowledgeDocumentKind;
import java.util.List;

/** Everything a chunker needs to turn a source document into chunk drafts. */
public record IngestionSource(long agencyId, String name, KnowledgeDocumentKind kind, List<String> pages,
        Long sourceProposalId, List<String> catalogNames) {
}