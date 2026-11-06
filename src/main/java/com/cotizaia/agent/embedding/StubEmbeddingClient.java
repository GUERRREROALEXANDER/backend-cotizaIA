package com.cotizaia.agent.embedding;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, deterministic embedder: token feature hashing into 256 dimensions, so tests and CI can run
 * retrieval without network or an API key.
 */
public class StubEmbeddingClient implements EmbeddingClient {

    static final int DIMENSIONS = 256;
    private static final Pattern TOKENS = Pattern.compile("[a-z]+|[0-9]+");

    @Override
    public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embedOne(text));
        }
        return vectors;
    }

    private float[] embedOne(String text) {
        Map<String, Integer> counts = tokens(text);
        if (counts.isEmpty()) {
            return new float[DIMENSIONS];
        }
        float[] vector = new float[DIMENSIONS];
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int hash = entry.getKey().hashCode();
            int index = Math.floorMod(hash, DIMENSIONS);
            float sign = Math.floorMod(hash / DIMENSIONS, 2) == 0 ? 1f : -1f;
            vector[index] += sign * (1f + (float) Math.log(entry.getValue()));
        }
        float norm = 0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] /= norm;
        }
        return vector;
    }

    /** Strips accents, lowercases and counts `[a-z]+|[0-9]+` tokens, dropping short letter tokens. */
    private Map<String, Integer> tokens(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = TOKENS.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 3 && !token.matches("[0-9]+")) {
                continue;
            }
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

    @Override
    public String provider() {
        return "stub";
    }
}
