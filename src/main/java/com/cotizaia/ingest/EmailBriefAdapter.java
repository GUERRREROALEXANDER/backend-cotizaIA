package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adapter for the email channel. An email is a structured envelope (subject,
 * from, body); only the body is the client's brief text, so subject and sender
 * stay in the raw payload for audit while the body becomes the normalized
 * brief.
 */
@Component
public class EmailBriefAdapter implements BriefSource {

    @Override
    public BriefChannel channel() {
        return BriefChannel.EMAIL;
    }

    @Override
    public NormalizedBrief normalize(Map<String, Object> payload) {
        return new NormalizedBrief(PayloadSupport.requireText(payload, "body"));
    }
}
