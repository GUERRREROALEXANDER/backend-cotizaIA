package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adapter for the WhatsApp channel. A WhatsApp delivery is a flat chat message
 * with a sender; {@code message} is the brief text.
 */
@Component
public class WhatsappBriefAdapter implements BriefSource {

    @Override
    public BriefChannel channel() {
        return BriefChannel.WHATSAPP;
    }

    @Override
    public NormalizedBrief normalize(Map<String, Object> payload) {
        return new NormalizedBrief(PayloadSupport.requireText(payload, "message"));
    }
}
