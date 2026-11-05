package com.cotizaia.api;

import com.cotizaia.domain.KnowledgeDocument;
import java.time.Instant;

public record KnowledgeDocumentListResponse(Long documentId, String name, String kind, int items, int passages,
        Instant createdAt) {

    public static KnowledgeDocumentListResponse from(KnowledgeDocument document, int items, int passages) {
        return new KnowledgeDocumentListResponse(document.getId(), document.getName(), document.getKind().name(),
                items, passages, document.getCreatedAt());
    }
}