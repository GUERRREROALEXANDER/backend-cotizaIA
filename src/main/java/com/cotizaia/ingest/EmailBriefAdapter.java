package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adapter for the email channel. An email is a structured envelope (subject,
 * from, body); only the body is the client's brief text, so subject and sender
 * stay in the raw payload for audit while the body becomes the normalized
 * brief.
 *
 * <p>An optional ISO-8601 {@code receivedAt} (the Gmail intake sends the
 * message's internalDate) becomes the brief's received time; a missing or
 * unparseable value falls back to ingest time instead of rejecting the brief.
 */
@Component
public class EmailBriefAdapter implements BriefSource {

    @Override
    public BriefChannel channel() {
        return BriefChannel.EMAIL;
    }

    @Override
    public NormalizedBrief normalize(Map<String, Object> payload) {
        return new NormalizedBrief(PayloadSupport.requireText(payload, "body", PayloadSupport.MAX_TYPED_TEXT_CHARS),
                receivedAt(payload.get("receivedAt")));
    }

    private static Instant receivedAt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
