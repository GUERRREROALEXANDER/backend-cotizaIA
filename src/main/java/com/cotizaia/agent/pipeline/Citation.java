package com.cotizaia.agent.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;

/**
 * The exact past item that justified an estimate, so a human can open the source proposal.
 * Similarity is rounded for display; hours are the approved hours of that item.
 */
public record Citation(long chunkId, String documentName, Integer page, double similarity, BigDecimal hours) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Serializes citations for storage; null becomes null so the column stays nullable. */
    public static String toJson(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(citations);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode estimate citations", exception);
        }
    }

    /** Reads stored citations; unreadable or empty storage degrades to no citations. */
    public static List<Citation> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Citation> parsed = MAPPER.readValue(json, new TypeReference<List<Citation>>() {
            });
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
