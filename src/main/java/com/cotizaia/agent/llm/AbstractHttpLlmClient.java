package com.cotizaia.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
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
        Duration retryAfter = retryAfter(exception);
        return new LlmException(provider() + " HTTP " + status,
                status == 429 || status >= 500 || isJsonSamplingFailure(status, exception), retryAfter, exception);
    }

    /**
     * In JSON mode Groq (and other OpenAI-compatible APIs) answer 400 {@code json_validate_failed} when the model
     * happened to sample invalid JSON. A new sample usually succeeds, so it is retryable; other 400s are not.
     */
    private static boolean isJsonSamplingFailure(int status, RestClientResponseException exception) {
        return status == 400 && exception.getResponseBodyAsString().contains("json_validate_failed");
    }

    private Duration retryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        return RetryAfter.parse(value).orElse(null);
    }

    protected LlmException mapFailure(ResourceAccessException exception) {
        return new LlmException(provider() + " network failure", true, exception);
    }
}
