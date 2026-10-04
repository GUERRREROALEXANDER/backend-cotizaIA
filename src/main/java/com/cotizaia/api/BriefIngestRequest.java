package com.cotizaia.api;

import com.cotizaia.domain.BriefChannel;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Raw ingest request as received over HTTP. The envelope is the same for every
 * channel; only {@code payload} differs by channel shape, which is exactly what
 * the {@code BriefSource} adapters exist to absorb.
 */
public record BriefIngestRequest(
        @NotNull BriefChannel source,
        @NotNull Long clientId,
        @NotNull Map<String, Object> payload) {
}
