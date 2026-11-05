package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.KnowledgeChunkRepository;
import com.cotizaia.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Component;

@Component
public class ProposalPdfIngestor extends KnowledgeIngestor {

    private final PastItemExtractor extractor;

    public ProposalPdfIngestor(AgencyRepository agencies, KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks, EmbeddingClient embeddings, VectorStore store,
            PastItemExtractor extractor) {
        super(agencies, documents, chunks, embeddings, store);
        this.extractor = extractor;
    }

    @Override
    protected Chunker createChunker() {
        return new ProposalPdfChunker(extractor);
    }
}