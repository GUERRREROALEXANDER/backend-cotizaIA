package com.cotizaia.ingest;

import com.cotizaia.domain.BriefChannel;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adapter for the web-form channel. A form is a set of named fields instead of
 * free text; the free-text {@code description} is the brief, while the
 * structured fields (project type, budget...) stay in the raw payload.
 */
@Component
public class WebFormBriefAdapter implements BriefSource {

    @Override
    public BriefChannel channel() {
        return BriefChannel.WEB_FORM;
    }

    @Override
    public NormalizedBrief normalize(Map<String, Object> payload) {
        String description = PayloadSupport.requireText(payload, "description");
        String projectType = optionalText(payload, "projectType");
        String text = projectType == null ? description : projectType + ": " + description;
        return new NormalizedBrief(text);
    }

    private String optionalText(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null || value.toString().isBlank()
                ? null
                : PayloadSupport.normalizeWhitespace(value.toString());
    }
}
