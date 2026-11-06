package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.agent.embedding.EmbeddingClient;
import com.cotizaia.agent.embedding.EmbeddingPurpose;
import com.cotizaia.domain.Agency;
import com.cotizaia.domain.ChunkKind;
import com.cotizaia.domain.KnowledgeChunk;
import com.cotizaia.domain.KnowledgeDocument;
import com.cotizaia.domain.KnowledgeDocumentKind;
import com.cotizaia.repository.KnowledgeChunkRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Bridge: the retriever abstractions (past items, passages) run on any {@link VectorStore} implementor. Here the
 * implementor is a test double, which is the point: nothing in the retriever knows which store it talks to.
 */
class KnowledgeRetrieverTests {

    private final RecordingStore store = new RecordingStore();

    private final RecordingEmbeddings embeddings = new RecordingEmbeddings();

    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);

    @Test
    void pastItemRetrieverQueriesItemsOfOneTypeAndCitesTheSource() {
        KnowledgeChunk chunk = chunk(11L, ChunkKind.ITEM, "Tienda en linea: 600 productos", "Tienda en linea", "89");
        when(chunks.findById(11L)).thenReturn(Optional.of(chunk));
        store.matches = List.of(new VectorMatch(11L, 0.91), new VectorMatch(99L, 0.5));

        List<RetrievedChunk> result = new PastItemRetriever(embeddings, store, chunks)
                .retrieve(7L, "tienda 600 productos", "Tienda en linea", 3);

        assertThat(store.queries).singleElement().satisfies(query -> {
            assertThat(query.agencyId()).isEqualTo(7L);
            assertThat(query.kind()).isEqualTo(ChunkKind.ITEM);
            assertThat(query.requirementType()).isEqualTo("Tienda en linea");
            assertThat(query.k()).isEqualTo(3);
        });
        assertThat(embeddings.purposes).containsExactly(EmbeddingPurpose.QUERY);
        assertThat(result).singleElement().satisfies(hit -> {
            assertThat(hit.chunkId()).isEqualTo(11L);
            assertThat(hit.documentName()).isEqualTo("P21-propuesta.pdf");
            assertThat(hit.page()).isEqualTo(1);
            assertThat(hit.hours()).isEqualByComparingTo("89");
            assertThat(hit.similarity()).isEqualTo(0.91);
            assertThat(hit.text()).contains("600 productos");
        });
    }

    @Test
    void passageRetrieverIgnoresTheRequirementType() {
        KnowledgeChunk chunk = chunk(12L, ChunkKind.PASSAGE, "Supuestos: el cliente entrega fotos", null, null);
        when(chunks.findById(12L)).thenReturn(Optional.of(chunk));
        store.matches = List.of(new VectorMatch(12L, 0.7));

        List<RetrievedChunk> result = new PassageRetriever(embeddings, store, chunks)
                .retrieve(7L, "fotos", "Tienda en linea", 5);

        assertThat(store.queries.get(0).kind()).isEqualTo(ChunkKind.PASSAGE);
        assertThat(store.queries.get(0).requirementType()).isNull();
        assertThat(result).extracting(RetrievedChunk::chunkId).containsExactly(12L);
    }

    private KnowledgeChunk chunk(long id, ChunkKind kind, String text, String type, String hours) {
        KnowledgeDocument document = new KnowledgeDocument(new Agency("A"), "P21-propuesta.pdf",
                KnowledgeDocumentKind.PROPOSAL_PDF);
        ReflectionTestUtils.setField(document, "id", 5L);
        KnowledgeChunk chunk = new KnowledgeChunk(document, kind, text, 1, type,
                hours == null ? null : new BigDecimal(hours), 0);
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }

    static class RecordingStore implements VectorStore {

        final List<VectorQuery> queries = new ArrayList<>();

        List<VectorMatch> matches = List.of();

        @Override
        public void upsert(List<VectorEntry> entries) {
        }

        @Override
        public List<VectorMatch> search(VectorQuery query) {
            queries.add(query);
            return matches;
        }

        @Override
        public void deleteDocument(long agencyId, long documentId) {
        }

        @Override
        public String name() {
            return "recording";
        }
    }

    static class RecordingEmbeddings implements EmbeddingClient {

        final List<EmbeddingPurpose> purposes = new ArrayList<>();

        @Override
        public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
            purposes.add(purpose);
            return texts.stream().map(text -> new float[] {1f, 0f}).toList();
        }

        @Override
        public String provider() {
            return "recording";
        }
    }
}
