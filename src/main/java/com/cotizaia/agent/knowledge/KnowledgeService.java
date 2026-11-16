package com.cotizaia.agent.knowledge;

import com.cotizaia.agent.knowledge.IngestionResult;
import com.cotizaia.agent.knowledge.IngestionSource;
import com.cotizaia.domain.KnowledgeDocumentKind;
import com.cotizaia.agent.knowledge.PastItemRetriever;
import com.cotizaia.agent.knowledge.ProposalPdfIngestor;
import com.cotizaia.agent.knowledge.RetrievedChunk;
import com.cotizaia.agent.knowledge.VectorStore;
import com.cotizaia.domain.ChunkKind;
import com.cotizaia.domain.KnowledgeDocument;
import com.cotizaia.domain.RequirementType;
import com.cotizaia.ingest.PdfBriefReader;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.KnowledgeChunkRepository;
import com.cotizaia.repository.KnowledgeChunkRepository.KindCount;
import com.cotizaia.repository.KnowledgeDocumentRepository;
import com.cotizaia.repository.RequirementTypeRepository;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class KnowledgeService {

    private final AgencyRepository agencies;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final RequirementTypeRepository requirementTypes;
    private final PdfBriefReader reader;
    private final ProposalPdfIngestor ingestor;
    private final PastItemRetriever retriever;
    private final VectorStore store;

    public KnowledgeService(AgencyRepository agencies, KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks, RequirementTypeRepository requirementTypes,
            PdfBriefReader reader, ProposalPdfIngestor ingestor, PastItemRetriever retriever,
            VectorStore store) {
        this.agencies = agencies;
        this.documents = documents;
        this.chunks = chunks;
        this.requirementTypes = requirementTypes;
        this.reader = reader;
        this.ingestor = ingestor;
        this.retriever = retriever;
        this.store = store;
    }

    public IngestionResult upload(Long agencyId, String name, byte[] bytes) {
        List<String> pages = reader.readPages(bytes);
        List<String> catalogNames = requirementTypes.findByServiceCatalogAgencyIdOrderByIdAsc(agencyId)
                .stream().map(RequirementType::getName).toList();
        String documentName = (name == null || name.isBlank()) ? "document.pdf" : name;
        IngestionSource source = new IngestionSource(agencyId, documentName, KnowledgeDocumentKind.PROPOSAL_PDF,
                pages, null, catalogNames, List.of());
        return ingestor.ingest(source);
    }

    public void delete(Long agencyId, Long documentId) {
        KnowledgeDocument document = documents.findByIdAndAgencyId(documentId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Knowledge document not found: " + documentId));
        store.deleteDocument(agencyId, documentId);
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(Long agencyId, String query, String type, Integer k) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("q must not be blank");
        }
        int kk = (k == null) ? 5 : k;
        if (kk < 1 || kk > 20) {
            throw new IllegalArgumentException("k must be between 1 and 20");
        }
        String typeFilter = (type == null || type.isBlank()) ? null : type;
        return retriever.retrieve(agencyId, query, typeFilter, kk);
    }

    /** The agency's documents with their item and passage totals, counted in one grouped query. */
    @Transactional(readOnly = true)
    public List<DocumentSummary> listWithCounts(Long agencyId) {
        List<KnowledgeDocument> found = documents.findByAgencyIdOrderByIdAsc(agencyId);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<ChunkKind, Long>> counts = new HashMap<>();
        for (KindCount row : chunks.countByDocumentIds(found.stream().map(KnowledgeDocument::getId).toList())) {
            counts.computeIfAbsent(row.getDocumentId(), id -> new EnumMap<>(ChunkKind.class))
                    .put(row.getKind(), row.getCount());
        }
        return found.stream().map(document -> {
            Map<ChunkKind, Long> byKind = counts.getOrDefault(document.getId(), Map.of());
            return new DocumentSummary(document, byKind.getOrDefault(ChunkKind.ITEM, 0L).intValue(),
                    byKind.getOrDefault(ChunkKind.PASSAGE, 0L).intValue());
        }).toList();
    }

    public record DocumentSummary(KnowledgeDocument document, int items, int passages) {
    }
}