package com.cotizaia.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adapter that translates the agent's requests to Ollama's chat API. Works against the local daemon
 * (no credentials) and Ollama Cloud (bearer token) because both speak the same {@code /api/chat} contract.
 */
public class OllamaClient extends AbstractHttpLlmClient {

    private final String apiKey;

    public OllamaClient(RestClient.Builder builder, LlmProperties properties) {
        super(builder, properties.getOllama().getBaseUrl(), properties.getOllama().getModel(), properties.getTimeout());
        this.apiKey = properties.getOllama().getApiKey();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", messages(request));
        body.put("options", Map.of("temperature", 0));
        if (request.jsonOutput()) {
            body.put("format", "json");
        }
        Instant start = Instant.now();
        try {
            RestClient.RequestBodySpec spec = client.post().uri("/api/chat");
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + apiKey);
            }
            JsonNode root = spec.body(body).retrieve().body(JsonNode.class);
            return response(root, "/message/content", start, request.jsonOutput());
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception);
        } catch (ResourceAccessException exception) {
            throw mapFailure(exception);
        }
    }

    @Override
    public String provider() {
        return "ollama";
    }
}
