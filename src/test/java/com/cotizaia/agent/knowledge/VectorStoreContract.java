package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.ChunkKind;
import com.cotizaia.domain.KnowledgeChunk;
import com.cotizaia.domain.KnowledgeDocument;
import com.cotizaia.domain.KnowledgeDocumentKind;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.KnowledgeChunkRepository;
import com.cotizaia.repository.KnowledgeDocumentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The Bridge implementor contract (spec 03 part B). Every {@link VectorStore} must pass it unchanged, which is
 * what lets the retrievers above it ignore whether vectors live in pgvector or in a portable table.
 */
abstract class VectorStoreContract {

    @Autowired
    protected VectorStore store;

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private KnowledgeDocumentRepository documents;

    @Autowired
    private KnowledgeChunkRepository chunks;

    private Agency agency;

    private KnowledgeDocument document;

    @BeforeEach
    void createAgencyAndDocument() {
        agency = agencies.saveAndFlush(new Agency("Agencia vectores"));
        document = documents.saveAndFlush(new KnowledgeDocument(agency, "P01-propuesta.pdf",
                KnowledgeDocumentKind.PROPOSAL_PDF));
    }

    @Test
    void returnsNearestFirstWithCosineSimilarity() {
        long far = item(document, "Tienda en linea", 3, 0, 0, 1);
        long near = item(document, "Tienda en linea", 2, 0.8f, 0.6f, 0);
        long same = item(document, "Tienda en linea", 1, 1, 0, 0);

        List<VectorMatch> matches = store.search(query(agency, ChunkKind.ITEM, null, 10, 1, 0, 0));

        assertThat(matches).extracting(VectorMatch::chunkId).containsExactly(same, near, far);
        assertThat(matches.get(0).similarity()).isCloseTo(1.0, within(1e-4));
        assertThat(matches.get(1).similarity()).isCloseTo(0.8, within(1e-4));
        assertThat(matches.get(2).similarity()).isCloseTo(0.0, within(1e-4));
    }

    @Test
    void neverReturnsAnotherAgencysVectors() {
        Agency other = agencies.saveAndFlush(new Agency("Otra agencia"));
        KnowledgeDocument foreign = documents.saveAndFlush(new KnowledgeDocument(other, "ajeno.pdf",
                KnowledgeDocumentKind.PROPOSAL_PDF));
        long foreignChunk = item(foreign, "Tienda en linea", 1, 1, 0, 0);
        long mine = item(document, "Tienda en linea", 2, 0, 1, 0);

        assertThat(store.search(query(agency, ChunkKind.ITEM, null, 10, 1, 0, 0)))
                .extracting(VectorMatch::chunkId).containsExactly(mine).doesNotContain(foreignChunk);
    }

    @Test
    void filtersByKindAndRequirementType() {
        long shop = item(document, "Tienda en linea", 1, 1, 0, 0);
        long site = item(document, "Sitio web informativo", 2, 1, 0, 0);
        long passage = passage(document, 3, 1, 0, 0);

        assertThat(store.search(query(agency, ChunkKind.ITEM, "Tienda en linea", 10, 1, 0, 0)))
                .extracting(VectorMatch::chunkId).containsExactly(shop);
        assertThat(store.search(query(agency, ChunkKind.ITEM, null, 10, 1, 0, 0)))
                .extracting(VectorMatch::chunkId).containsExactlyInAnyOrder(shop, site);
        assertThat(store.search(query(agency, ChunkKind.PASSAGE, null, 10, 1, 0, 0)))
                .extracting(VectorMatch::chunkId).containsExactly(passage);
    }

    @Test
    void limitsResultsToK() {
        item(document, "Tienda en linea", 1, 1, 0, 0);
        item(document, "Tienda en linea", 2, 0.9f, 0.1f, 0);
        item(document, "Tienda en linea", 3, 0.5f, 0.5f, 0);
        assertThat(store.search(query(agency, ChunkKind.ITEM, null, 2, 1, 0, 0))).hasSize(2);
    }

    @Test
    void upsertReplacesAChunksVector() {
        long chunk = item(document, "Tienda en linea", 1, 0, 1, 0);
        store.upsert(List.of(entry(chunk, ChunkKind.ITEM, "Tienda en linea", 1, 0, 0)));
        List<VectorMatch> matches = store.search(query(agency, ChunkKind.ITEM, null, 10, 1, 0, 0));
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).similarity()).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void deletingADocumentRemovesOnlyItsVectors() {
        KnowledgeDocument second = documents.saveAndFlush(new KnowledgeDocument(agency, "P02.pdf",
                KnowledgeDocumentKind.PROPOSAL_PDF));
        item(document, "Tienda en linea", 1, 1, 0, 0);
        long kept = item(second, "Tienda en linea", 1, 1, 0, 0);

        store.deleteDocument(agency.getId(), document.getId());

        assertThat(store.search(query(agency, ChunkKind.ITEM, null, 10, 1, 0, 0)))
                .extracting(VectorMatch::chunkId).containsExactly(kept);
    }

    @Test
    void deleteIsScopedToTheAgency() {
        long mine = item(document, "Tienda en linea", 1, 1, 0, 0);
        store.deleteDocument(agency.getId() + 999_999, document.getId());
        assertThat(store.search(query(agency, ChunkKind.ITEM, null, 10, 1, 0, 0)))
                .extracting(VectorMatch::chunkId).containsExactly(mine);
    }

    private long item(KnowledgeDocument owner, String type, int ordinal, float... vector) {
        KnowledgeChunk chunk = chunks.saveAndFlush(new KnowledgeChunk(owner, ChunkKind.ITEM, type + ": item " + ordinal,
                1, type, new BigDecimal("40"), ordinal));
        store.upsert(List.of(new VectorEntry(chunk.getId(), owner.getAgency().getId(), owner.getId(), ChunkKind.ITEM,
                type, vector)));
        return chunk.getId();
    }

    private long passage(KnowledgeDocument owner, int ordinal, float... vector) {
        KnowledgeChunk chunk = chunks.saveAndFlush(new KnowledgeChunk(owner, ChunkKind.PASSAGE, "passage " + ordinal,
                1, null, null, ordinal));
        store.upsert(List.of(new VectorEntry(chunk.getId(), owner.getAgency().getId(), owner.getId(),
                ChunkKind.PASSAGE, null, vector)));
        return chunk.getId();
    }

    private VectorEntry entry(long chunkId, ChunkKind kind, String type, float... vector) {
        return new VectorEntry(chunkId, agency.getId(), document.getId(), kind, type, vector);
    }

    private VectorQuery query(Agency owner, ChunkKind kind, String type, int k, float... vector) {
        return new VectorQuery(owner.getId(), kind, type, vector, k);
    }
}
