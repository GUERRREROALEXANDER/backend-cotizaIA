package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.domain.ChunkKind;
import com.cotizaia.repository.KnowledgeChunkRepository;
import org.springframework.stereotype.Component;

@Component
public class PassageRetriever extends KnowledgeRetriever {

    public PassageRetriever(EmbeddingClient embeddings, VectorStore store, KnowledgeChunkRepository chunks) {
        super(embeddings, store, chunks);
    }

    @Override
    protected ChunkKind kind() {
        return ChunkKind.PASSAGE;
    }

    @Override
    protected String typeFilter(String requested) {
        return null;
    }
}