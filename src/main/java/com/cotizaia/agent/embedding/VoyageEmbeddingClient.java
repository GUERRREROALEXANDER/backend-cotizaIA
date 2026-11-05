package com.cotizaia.agent.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Adapter that translates the agent's requests to Voyage AI's embeddings API. */
public class VoyageEmbeddingClient implements EmbeddingClient {

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final int dimensions;
    private final int batchSize;

    public VoyageEmbeddingClient(RestClient.Builder builder, EmbeddingProperties properties) {
        this.client = builder.baseUrl(properties.getVoyage().getBaseUrl()).build();
        this.apiKey = properties.getVoyage().getApiKey();
        this.model = properties.getVoyage().getModel();
        this.dimensions = properties.getDimensions();
        this.batchSize = properties.getBatchSize();
    }

    @Override
    public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmbeddingException("VOYAGE_API_KEY is required for Voyage", false);
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        float[][] result = new float[texts.size()][];
        String inputType = purpose == EmbeddingPurpose.QUERY ? "query" : "document";
        for (int offset = 0; offset < texts.size(); offset += batchSize) {
            List<String> batch = texts.subList(offset, Math.min(offset + batchSize, texts.size()));
            try {
                JsonNode root = client.post().uri("/embeddings")
                        .header("Authorization", "Bearer " + apiKey)
                        .body(requestBody(batch, inputType))
                        .retrieve()
                        .body(JsonNode.class);
                storeBatch(root, batch.size(), offset, result);
            } catch (RestClientResponseException exception) {
                throw mapFailure(exception);
            } catch (ResourceAccessException exception) {
                throw mapFailure(exception);
            }
        }
        return List.of(result);
    }

    private Map<String, Object> requestBody(List<String> batch, String inputType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", batch);
        body.put("model", model);
        body.put("input_type", inputType);
        body.put("output_dimension", dimensions);
        return body;
    }

    /** Places each vector at batch start + its reported index, so the caller's order is preserved. */
    private void storeBatch(JsonNode root, int batchCount, int batchOffset, float[][] result) {
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray()) {
            throw new EmbeddingException("Voyage response has no data", true);
        }
        for (JsonNode item : data) {
            int index = item.path("index").asInt(batchOffset);
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new EmbeddingException("Voyage response has no embedding", true);
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = embedding.get(i).floatValue();
            }
            result[batchOffset + index] = vector;
        }
        for (int i = 0; i < batchCount; i++) {
            if (result[batchOffset + i] == null) {
                throw new EmbeddingException("Voyage response is missing an embedding", true);
            }
        }
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
        return "voyage";
    }
}
