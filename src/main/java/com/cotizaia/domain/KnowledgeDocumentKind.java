package com.cotizaia.domain;

/**
 * Where a knowledge document came from: the raw proposal PDF as delivered, or
 * the client-approved version (whose hours are ground truth for retrieval).
 */
public enum KnowledgeDocumentKind {
    PROPOSAL_PDF,
    APPROVED_PROPOSAL
}
