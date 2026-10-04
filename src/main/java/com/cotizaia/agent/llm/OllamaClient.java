package com.cotizaia.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Adapter that translates the agent's requests to Ollama's local chat API. */
public class OllamaClient extends AbstractHttpLlmClient {

    public OllamaClient(RestClient.Builder builder, LlmProperties properties) {
        super(builder, properties.getOllama().getBaseUrl(), properties.getOllama().getModel(), properties.getTimeout());
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
            JsonNode root = client.post().uri("/api/chat").body(body).retrieve().body(JsonNode.class);
            return response(root, "/message/content", start);
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
