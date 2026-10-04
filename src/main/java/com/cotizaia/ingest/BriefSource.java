package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.util.Map;

/**
 * Adapter pattern (project.txt section 6 pattern 5a): input channels.
 *
 * <p>Each channel delivers a different payload shape (email fields, a WhatsApp
 * message, web-form fields). An implementation of this port owns the knowledge
 * of its channel's shape and translates it into the shared {@link NormalizedBrief},
 * so the ingest pipeline only ever sees one normalized Brief.
 *
 * <p>Extension rule (acceptance criterion): a new channel is one new class that
 * implements this port and is registered as a Spring bean. No existing adapter,
 * nor the registry, service or controller, needs to change — they depend on the
 * port, never on a concrete channel.
 */
public interface BriefSource {

    /** The channel this adapter knows how to read. */
    BriefChannel channel();

    /**
     * Reduces this channel's payload to the canonical brief text.
     *
     * @throws IllegalArgumentException when a required field is missing/blank
     *         or the payload does not match this channel's shape.
     */
    NormalizedBrief normalize(Map<String, Object> payload);
}
