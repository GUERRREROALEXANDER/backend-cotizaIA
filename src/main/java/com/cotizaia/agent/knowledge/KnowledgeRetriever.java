package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.agent.embedding.EmbeddingPurpose;
import com.cotizaia.domain.ChunkKind;
import com.cotizaia.domain.KnowledgeChunk;
import com.cotizaia.domain.KnowledgeDocument;
import com.cotizaia.repository.KnowledgeChunkRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class KnowledgeRetriever {

    private final EmbeddingClient embeddings;
    private final VectorStore store;
    private final KnowledgeChunkRepository chunks;

    protected KnowledgeRetriever(EmbeddingClient embeddings, VectorStore store, KnowledgeChunkRepository chunks) {
        this.embeddings = embeddings;
        this.store = store;
        this.chunks = chunks;
    }

    public List<RetrievedChunk> retrieve(long agencyId, String query, String requirementType, int k) {
        List<float[]> vectors = embeddings.embed(List.of(query), EmbeddingPurpose.QUERY);
        float[] queryVector = vectors.isEmpty() ? new float[0] : vectors.get(0);
        VectorQuery vectorQuery = new VectorQuery(agencyId, kind(), typeFilter(requirementType), queryVector, k);
        List<VectorMatch> matches = store.search(vectorQuery);
        List<RetrievedChunk> result = new ArrayList<>();
        for (VectorMatch match : matches) {
            Optional<KnowledgeChunk> chunkOpt = chunks.findById(match.chunkId());
            if (chunkOpt.isEmpty()) {
                continue;
            }
            KnowledgeChunk chunk = chunkOpt.get();
            KnowledgeDocument document = chunk.getDocument();
            result.add(new RetrievedChunk(chunk.getId(), document.getId(), document.getName(), chunk.getPage(),
                    chunk.getText(), chunk.getRequirementType(), chunk.getHours(), match.similarity()));
        }
        return result;
    }

    protected abstract ChunkKind kind();

    protected String typeFilter(String requested) {
        return requested;
    }
}