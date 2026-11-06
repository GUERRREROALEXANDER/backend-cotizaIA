package com.cotizaia.agent.knowledge;

import com.cotizaia.domain.ChunkKind;

/**
 * A similarity search restricted to one agency, one chunk kind and, when
 * {@code requirementType} is not null, one requirement type; it never spans
 * agencies because that would leak one client's pricing to another.
 */
public record VectorQuery(long agencyId, ChunkKind kind, String requirementType, float[] vector, int k) {

    public VectorQuery {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("query vector is required");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
    }
}
