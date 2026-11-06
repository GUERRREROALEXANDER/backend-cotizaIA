package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.KnowledgeChunkRepository;
import com.cotizaia.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Component;

/**
 * Second Factory Method subclass (spec 04): the same ingest algorithm, but the chunks come from the line items a
 * human approved and sent instead of from a past proposal PDF.
 */
@Component
public class ApprovedProposalIngestor extends KnowledgeIngestor {

    public ApprovedProposalIngestor(AgencyRepository agencies, KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks, EmbeddingClient embeddings, VectorStore store) {
        super(agencies, documents, chunks, embeddings, store);
    }

    @Override
    protected Chunker createChunker() {
        return new QuotedItemChunker();
    }
}
