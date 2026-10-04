package com.cotizaia.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Shares transport setup and vendor error classification across the HTTP adapters. */
abstract class AbstractHttpLlmClient implements LlmClient {

    protected final RestClient client;
    protected final String model;

    AbstractHttpLlmClient(RestClient.Builder builder, String baseUrl, String model, LlmProperties.Timeout timeout) {
        this.client = builder.baseUrl(baseUrl).build();
        this.model = model;
    }

    protected List<Map<String, String>> messages(LlmRequest request) {
        return List.of(Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt()));
    }

    protected LlmResponse response(JsonNode root, String pointer, Instant start) {
        JsonNode content = root == null ? null : root.at(pointer);
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new LlmException("LLM response has no content", true);
        }
        return new LlmResponse(content.asText(), provider(), model, Duration.between(start, Instant.now()).toMillis());
    }

    protected LlmException mapFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return new LlmException(provider() + " HTTP " + status, status == 429 || status >= 500, exception);
    }

    protected LlmException mapFailure(ResourceAccessException exception) {
        return new LlmException(provider() + " network failure", true, exception);
    }
}
