package com.cotizaia.agent.knowledge;

/**
 * One retrieval hit: the chunk behind the matched vector and its cosine
 * similarity to the query (1 = identical). Chunk metadata (text, page, hours)
 * is resolved afterwards through the chunk repository.
 */
public record VectorMatch(long chunkId, double similarity) {
}
