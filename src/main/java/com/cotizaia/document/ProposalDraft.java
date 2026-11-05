package com.cotizaia.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * The proposal text the model (or the safe template) produced, detached from the agent package so it
 * survives persistence and later PDF regeneration. Citations are 1-based numbers into {@link #sources()}.
 */
public record ProposalDraft(String summary, List<DraftSection> sections, List<SourceRef> sources,
        DraftOrigin origin) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ProposalDraft {
        sections = sections == null ? List.of() : List.copyOf(sections);
        sources = sources == null ? List.of() : List.copyOf(sources);
        origin = origin == null ? DraftOrigin.TEMPLATE : origin;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode proposal draft", exception);
        }
    }

    public static ProposalDraft fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ProposalDraft.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to decode proposal draft", exception);
        }
    }
}
