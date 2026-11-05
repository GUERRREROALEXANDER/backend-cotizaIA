package com.cotizaia.agent.knowledge;

import com.cotizaia.domain.ChunkKind;

/**
 * One embedding to persist, with the metadata the retriever filters on. The
 * vector is shared with the caller, not copied: the store treats it as
 * read-only and embedder output is final at this point.
 */
public record VectorEntry(long chunkId, long agencyId, long documentId, ChunkKind kind, String requirementType,
        float[] vector) {
}
