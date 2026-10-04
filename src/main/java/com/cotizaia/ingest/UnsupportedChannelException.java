package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;

/**
 * Thrown when a channel has no registered {@link BriefSource} adapter. Mapped to
 * HTTP 400 by the API advice.
 */
public class UnsupportedChannelException extends RuntimeException {

    public UnsupportedChannelException(BriefChannel channel) {
        super("No BriefSource adapter registered for channel: " + channel);
    }
}
