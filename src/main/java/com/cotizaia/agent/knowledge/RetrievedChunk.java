package com.cotizaia.agent.knowledge;

import java.math.BigDecimal;

public record RetrievedChunk(long chunkId, long documentId, String documentName, Integer page, String text,
        String requirementType, BigDecimal hours, double similarity) {
}