package com.cotizaia.ingest;

import java.util.Map;

/**
 * Shared payload helpers so every adapter normalizes text identically. Keeping
 * whitespace normalization in one place is what makes "different channel
 * payloads, identical Brief" hold across adapters.
 */
final class PayloadSupport {

    private PayloadSupport() {
    }

    /**
     * Reads a required text field, trims it and collapses inner whitespace runs
     * to a single space, so the same message typed with different spacing
     * normalizes to the same Brief.
     */
    static String requireText(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required payload field: " + key);
        }
        return normalizeWhitespace(value.toString());
    }

    static String normalizeWhitespace(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
