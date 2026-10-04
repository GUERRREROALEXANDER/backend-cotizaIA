package com.cotizaia.api;

import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import java.time.Instant;

/**
 * Normalized brief returned to the caller, including the audit payload so the
 * operator can confirm exactly what was stored.
 */
public record BriefResponse(
        Long id,
        Long clientId,
        BriefChannel source,
        String rawText,
        String rawPayload,
        Instant receivedAt) {

    public static BriefResponse from(Brief brief) {
        return new BriefResponse(
                brief.getId(),
                brief.getClient().getId(),
                brief.getSource(),
                brief.getRawText(),
                brief.getRawPayload(),
                brief.getReceivedAt());
    }
}
