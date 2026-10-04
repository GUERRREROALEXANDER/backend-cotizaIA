package com.cotizaia.ingest;

import java.time.Instant;

/**
 * The one canonical shape every input channel is reduced to. {@code rawText} is
 * the normalized brief text the pipeline consumes; {@code receivedAt} is an
 * optional channel-provided timestamp (null means "use ingest time").
 */
public record NormalizedBrief(String rawText, Instant receivedAt) {

    public NormalizedBrief(String rawText) {
        this(rawText, null);
    }
}
