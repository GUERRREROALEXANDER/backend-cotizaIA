package com.cotizaia.api;

import java.math.BigDecimal;

public record KnowledgeUploadItemResponse(Long chunkId, String requirementType, BigDecimal hours, int page) {
    public static KnowledgeUploadItemResponse from(com.cotizaia.agent.knowledge.IngestedItem item) {
        return new KnowledgeUploadItemResponse(item.chunkId(), item.requirementType(), item.hours(), item.page());
    }
}