package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.agent.embedding.EmbeddingPurpose;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.KnowledgeChunk;
import com.cotizaia.domain.KnowledgeDocument;
import com.cotizaia.domain.KnowledgeDocumentKind;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.KnowledgeChunkRepository;
import com.cotizaia.repository.KnowledgeDocumentRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory Method: the ingest algorithm is fixed; subclasses decide which
 * {@link Chunker} to create.
 */
public abstract class KnowledgeIngestor {

    protected final AgencyRepository agencies;
    protected final KnowledgeDocumentRepository documents;
    protected final KnowledgeChunkRepository chunks;
    protected final EmbeddingClient embeddings;
    protected final VectorStore store;

    public KnowledgeIngestor(AgencyRepository agencies, KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks, EmbeddingClient embeddings, VectorStore store) {
        this.agencies = agencies;
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.store = store;
    }

    public final IngestionResult ingest(IngestionSource source) {
        Chunker chunker = createChunker();
        List<ChunkDraft> drafts = chunker.chunk(source);
        Agency agency = agencies.findById(source.agencyId())
                .orElseThrow(() -> new IllegalArgumentException("Agency not found: " + source.agencyId()));
        KnowledgeDocument document = new KnowledgeDocument(agency, source.name(), source.kind());
        document.setPageCount(source.pages() == null ? 0 : source.pages().size());
        document.setSourceProposalId(source.sourceProposalId());
        documents.save(document);

        List<KnowledgeChunk> chunkEntities = new ArrayList<>();
        int ordinal = 0;
        for (ChunkDraft draft : drafts) {
            KnowledgeChunk chunk = new KnowledgeChunk(document, draft.kind(), draft.text(), draft.page(),
                    draft.requirementType(), draft.hours(), ordinal++);
            chunks.save(chunk);
            chunkEntities.add(chunk);
        }

        List<String> texts = drafts.stream().map(ChunkDraft::text).toList();
        List<float[]> vectors = texts.isEmpty() ? List.of() : embeddings.embed(texts, EmbeddingPurpose.DOCUMENT);

        List<VectorEntry> entries = new ArrayList<>();
        int passageCount = 0;
        List<IngestedItem> items = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            KnowledgeChunk chunk = chunkEntities.get(i);
            if (draft.kind() == com.cotizaia.domain.ChunkKind.PASSAGE) {
                passageCount++;
            } else if (draft.kind() == com.cotizaia.domain.ChunkKind.ITEM) {
                items.add(new IngestedItem(chunk.getId(), draft.requirementType(), draft.hours(),
                        draft.page() == null ? 0 : draft.page()));
            }
            if (i < vectors.size()) {
                entries.add(new VectorEntry(chunk.getId(), source.agencyId(), document.getId(), draft.kind(),
                        draft.requirementType(), vectors.get(i)));
            }
        }
        if (!entries.isEmpty()) {
            store.upsert(entries);
        }

        return new IngestionResult(document.getId(), passageCount, items, chunker.rejected());
    }

    protected abstract Chunker createChunker();
}