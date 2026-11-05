package com.cotizaia.agent.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proxy over a real embedder: identical texts are embedded once per purpose, under an LRU bound. */
public class CachingEmbeddingClient implements EmbeddingClient {

    private final EmbeddingClient delegate;
    private final int cacheSize;
    private final Map<String, float[]> cache;

    public CachingEmbeddingClient(EmbeddingClient delegate, int cacheSize) {
        this.delegate = delegate;
        this.cacheSize = cacheSize;
        // accessOrder = true makes the eldest entry the least recently used one, hits included.
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > CachingEmbeddingClient.this.cacheSize;
            }
        };
    }

    @Override
    public synchronized List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> result = new ArrayList<>(texts.size());
        List<Integer> misses = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            float[] cached = cache.get(key(purpose, texts.get(i)));
            if (cached != null) {
                result.add(cached);
            } else {
                result.add(null);
                misses.add(i);
            }
        }
        if (!misses.isEmpty()) {
            List<String> missedTexts = misses.stream().map(texts::get).distinct().toList();
            List<float[]> outstanding = delegate.embed(missedTexts, purpose);
            if (outstanding.size() != missedTexts.size()) {
                throw new EmbeddingException(delegate.provider() + " returned wrong vector count", true);
            }
            for (int slot = 0; slot < misses.size(); slot++) {
                int position = misses.get(slot);
                String text = texts.get(position);
                float[] vector = outstanding.get(missedTexts.indexOf(text));
                cache.put(key(purpose, text), vector);
                result.set(position, vector);
            }
        }
        return result;
    }

    private String key(EmbeddingPurpose purpose, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return purpose + "|" + HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is part of every JDK; no caller can reach this branch.
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    @Override
    public String provider() {
        return delegate.provider();
    }
}
