package com.cotizaia.agent.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Adapter that translates the agent's requests to Ollama's local embeddings API. */
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient client;
    private final String model;

    public OllamaEmbeddingClient(RestClient.Builder builder, EmbeddingProperties properties) {
        this.client = builder.baseUrl(properties.getOllama().getBaseUrl()).build();
        this.model = properties.getOllama().getModel();
    }

    @Override
    public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        try {
            JsonNode root = client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .body(JsonNode.class);
            return toVectors(root);
        } catch (RestClientResponseException exception) {
            throw mapFailure(exception);
        } catch (ResourceAccessException exception) {
            throw mapFailure(exception);
        }
    }

    private List<float[]> toVectors(JsonNode root) {
        JsonNode embeddings = root == null ? null : root.get("embeddings");
        if (embeddings == null || !embeddings.isArray()) {
            throw new EmbeddingException("Ollama response has no embeddings", true);
        }
        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (JsonNode embedding : embeddings) {
            if (!embedding.isArray()) {
                throw new EmbeddingException("Ollama response is missing an embedding", true);
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private EmbeddingException mapFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return new EmbeddingException(provider() + " HTTP " + status, status == 429 || status >= 500, exception);
    }

    private EmbeddingException mapFailure(ResourceAccessException exception) {
        return new EmbeddingException(provider() + " network failure", true, exception);
    }

    @Override
    public String provider() {
        return "ollama";
    }
}
