package com.cotizaia.agent.llm;

import java.util.Objects;

/** Carries a validated task and its prompts across the vendor-neutral client boundary. */
public record LlmRequest(LlmTask task, String systemPrompt, String userPrompt, boolean jsonOutput) {

    public LlmRequest {
        Objects.requireNonNull(task, "task");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt must not be blank");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }
    }
}
