package com.cotizaia.api;

import com.cotizaia.agent.knowledge.IngestionResult;
import java.util.List;

public record KnowledgeUploadResponse(Long documentId, String name, int passages,
        List<KnowledgeUploadItemResponse> items, int rejectedItems) {

    public static KnowledgeUploadResponse from(String name, IngestionResult result) {
        return new KnowledgeUploadResponse(result.documentId(), name, result.passages(),
                result.items().stream().map(KnowledgeUploadItemResponse::from).toList(),
                result.rejectedItems());
    }
}