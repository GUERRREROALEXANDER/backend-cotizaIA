package com.cotizaia.agent.llm;

/** Reports model output and timing so callers can record each AI step. */
public record LlmResponse(String content, String provider, String model, long latencyMs) {
}
