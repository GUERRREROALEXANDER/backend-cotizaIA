package com.cotizaia.agent.knowledge;

import java.util.List;

/**
 * The Bridge abstraction of spec 03 part B: how embeddings are physically
 * stored and searched is the one variation point, so the retrievers (Part C)
 * can ask for "the k most similar past items" without knowing whether the
 * answer comes from an exact scan or from a pgvector index.
 */
public interface VectorStore {

    /** Insert or replace the vector of each entry, keyed by chunk id. */
    void upsert(List<VectorEntry> entries);

    /** Most similar candidates first, at most k, always scoped to the agency. */
    List<VectorMatch> search(VectorQuery query);

    /** Removes every vector of a document, but only if the agency owns it. */
    void deleteDocument(long agencyId, long documentId);

    /** Implementor name, for logging and configuration ("jdbc", "pgvector"). */
    String name();
}
