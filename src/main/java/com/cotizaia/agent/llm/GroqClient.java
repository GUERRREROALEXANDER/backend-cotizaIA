package com.cotizaia.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Adapter that translates the agent's requests to Groq's OpenAI-compatible chat API. */
public class GroqClient extends AbstractHttpLlmClient {

    private final String apiKey;

    public GroqClient(RestClient.Builder builder, LlmProperties properties) {
        super(builder, properties.getGroq().getBaseUrl(), properties.getGroq().getModel(), properties.getTimeout());
        this.apiKey = properties.getGroq().getApiKey();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("GROQ_API_KEY is required for Groq", false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", messages(request));
        if (request.jsonOutput()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        Instant start = Instant.now();
        try {
            JsonNode root = client.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey).body(body).retrieve().body(JsonNode.class);
            return response(root, "/choices/0/message/content", start);
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception);
        } catch (ResourceAccessException exception) {
            throw mapFailure(exception);
        }
    }

    @Override
    public String provider() {
        return "groq";
    }
}
