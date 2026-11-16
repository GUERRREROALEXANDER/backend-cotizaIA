package com.cotizaia.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Adapter for any OpenAI-compatible chat API (Groq, Google's Gemini endpoint): same wire format, different name. */
public class OpenAiCompatibleClient extends AbstractHttpLlmClient {

    private final String name;

    private final String keyVariable;

    private final String apiKey;

    private final String reasoningEffort;

    public OpenAiCompatibleClient(RestClient.Builder builder, String name, String keyVariable,
            LlmProperties.Groq settings, LlmProperties.Timeout timeout) {
        super(builder, settings.getBaseUrl(), settings.getModel(), timeout);
        this.name = name;
        this.keyVariable = keyVariable;
        this.apiKey = settings.getApiKey();
        this.reasoningEffort = settings.getReasoningEffort();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException(keyVariable + " is required for " + name, false);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", messages(request));
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort);
        }
        if (request.jsonOutput()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        Instant start = Instant.now();
        try {
            JsonNode root = client.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey).body(body).retrieve().body(JsonNode.class);
            return response(root, "/choices/0/message/content", start, request.jsonOutput());
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception);
        } catch (ResourceAccessException exception) {
            throw mapFailure(exception);
        }
    }

    @Override
    public String provider() {
        return name;
    }
}
