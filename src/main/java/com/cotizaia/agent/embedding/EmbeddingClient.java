package com.cotizaia.agent.embedding;

import java.util.List;

/** Port that turns texts into fixed-size vectors, preserving the input order. */
public interface EmbeddingClient {

    /**
     * Embeds every text, returning the same number of vectors in the same order.
     */
    List<float[]> embed(List<String> texts, EmbeddingPurpose purpose);

    String provider();
}
