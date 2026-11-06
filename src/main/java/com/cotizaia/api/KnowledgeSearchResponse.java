package com.cotizaia.api;

import com.cotizaia.agent.knowledge.RetrievedChunk;
import java.math.BigDecimal;

public record KnowledgeSearchResponse(Long chunkId, Long documentId, String documentName, Integer page, String text,
        String requirementType, BigDecimal hours, double similarity) {

    public static KnowledgeSearchResponse from(RetrievedChunk hit) {
        return new KnowledgeSearchResponse(hit.chunkId(), hit.documentId(), hit.documentName(), hit.page(), hit.text(),
                hit.requirementType(), hit.hours(), hit.similarity());
    }
}