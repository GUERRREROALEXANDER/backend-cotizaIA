package com.cotizaia.ingest;

import java.util.Map;

/**
 * Shared payload helpers so every adapter normalizes text identically. Keeping
 * whitespace normalization in one place is what makes "different channel
 * payloads, identical Brief" hold across adapters.
 */
final class PayloadSupport {

    /**
     * Maximum characters accepted for typed-channel brief text. Typed input is
     * written by a human in one box, so an unbounded body is a mistake, not a
     * brief; extracted PDF text has its own (higher) cap in the reader.
     */
    static final int MAX_TYPED_TEXT_CHARS = 5_000;

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

    /**
     * Reads a required text field with an upper length bound, so one pasted
     * novel cannot become a brief. Exactly the limit is still accepted.
     */
    static String requireText(Map<String, Object> payload, String key, int maxLength) {
        String text = requireText(payload, key);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Field " + key + " exceeds " + maxLength + " characters");
        }
        return text;
    }

    static String normalizeWhitespace(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }
}
