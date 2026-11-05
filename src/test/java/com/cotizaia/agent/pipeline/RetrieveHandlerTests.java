package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cotizaia.agent.embedding.EmbeddingException;
import com.cotizaia.agent.knowledge.PastItemRetriever;
import com.cotizaia.agent.knowledge.RetrievedChunk;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The retrieval step feeds evidence to estimation and degrades to the catalog instead of failing the run. */
class RetrieveHandlerTests {

    private final RetrievalProperties properties = new RetrievalProperties();

    @Test
    void attachesEvidenceToEachRequirementUsingItsOwnTypeAndQuery() {
        List<String> seen = new ArrayList<>();
        PastItemRetriever retriever = mock(PastItemRetriever.class);
        when(retriever.retrieve(anyLong(), anyString(), anyString(), anyInt())).thenAnswer(call -> {
            seen.add(call.getArgument(0) + "|" + call.getArgument(2) + "|" + call.getArgument(3) + "|"
                    + call.getArgument(1));
            return List.of(new RetrievedChunk(9L, 1L, "P9.pdf", 1, "x", call.getArgument(2), BigDecimal.TEN, 0.8));
        });
        PipelineContext context = PipelineTestFixture.context();
        RequirementDraft draft = PipelineTestFixture.requirement(new BigDecimal("24"));
        context.setRequirements(List.of(draft));

        HandlerResult result = new RetrieveHandler(retriever, new EvidenceQueryBuilder(), properties).process(context);

        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(seen).singleElement().asString().startsWith("1|Diseno responsive|5|Diseno responsive: ");
        assertThat(draft.getEvidence()).extracting(RetrievedChunk::chunkId).containsExactly(9L);
        assertThat(result.summary()).contains("1/1");
    }

    @Test
    void memoryOutageFlagsAndContinuesWithEmptyEvidence() {
        PastItemRetriever broken = mock(PastItemRetriever.class);
        when(broken.retrieve(anyLong(), anyString(), anyString(), anyInt()))
                .thenThrow(new EmbeddingException("voyage down", true));
        PipelineContext context = PipelineTestFixture.context();
        RequirementDraft draft = PipelineTestFixture.requirement(new BigDecimal("24"));
        context.setRequirements(List.of(draft));

        HandlerResult result = new RetrieveHandler(broken, new EvidenceQueryBuilder(), properties).process(context);

        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(result.summary()).contains("memory unavailable");
        assertThat(draft.getEvidence()).isEmpty();
    }

    @Test
    void disabledRetrievalSkipsMemory() {
        properties.setEnabled(false);
        PastItemRetriever untouched = mock(PastItemRetriever.class);
        PipelineContext context = PipelineTestFixture.context();
        context.setRequirements(List.of(PipelineTestFixture.requirement(new BigDecimal("24"))));
        assertThat(new RetrieveHandler(untouched, new EvidenceQueryBuilder(), properties).process(context).summary())
                .contains("memory disabled");
        verifyNoInteractions(untouched);
    }

    @Test
    void estimateHandlerPrefersMemoryThenCatalogThenDefault() {
        RequirementDraft remembered = PipelineTestFixture.requirement(new BigDecimal("24"));
        remembered.setEvidence(List.of(
                new RetrievedChunk(1L, 1L, "P1.pdf", 1, "a", "Diseno responsive", new BigDecimal("40"), 0.9),
                new RetrievedChunk(2L, 2L, "P2.pdf", 1, "b", "Diseno responsive", new BigDecimal("44"), 0.8)));
        RequirementDraft catalogOnly = PipelineTestFixture.requirement(new BigDecimal("24"));
        RequirementDraft unknown = PipelineTestFixture.requirement(null);
        PipelineContext context = PipelineTestFixture.context();
        context.setRequirements(List.of(remembered, catalogOnly, unknown));

        EstimateHandler handler = new EstimateHandler(List.of(new RetrievalEstimation(properties),
                new CatalogEstimation(), new DefaultEstimation(new PipelineProperties())));
        HandlerResult result = handler.process(context);

        assertThat(remembered.getEstimatedHours()).isEqualByComparingTo("40");
        assertThat(remembered.getEstimateSource()).isEqualTo(EstimateSource.MEMORY);
        assertThat(remembered.getCitations()).hasSize(2);
        assertThat(catalogOnly.getEstimatedHours()).isEqualByComparingTo("24");
        assertThat(catalogOnly.getEstimateSource()).isEqualTo(EstimateSource.CATALOG);
        assertThat(catalogOnly.getCitations()).isEmpty();
        assertThat(unknown.getEstimateSource()).isEqualTo(EstimateSource.DEFAULT);
        assertThat(unknown.isDefaultHoursUsed()).isTrue();
        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(result.summary()).isEqualTo("memory 1, catalog 1, default 1");
    }
}
