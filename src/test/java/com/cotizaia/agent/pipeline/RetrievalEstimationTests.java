package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.agent.knowledge.RetrievedChunk;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Strategy: hours from the agency's own past items, only when there is enough similar evidence. */
class RetrievalEstimationTests {

    private final RetrievalProperties properties = new RetrievalProperties();

    private final RetrievalEstimation strategy = new RetrievalEstimation(properties);

    @Test
    void usesTheSimilarityWeightedMedianAndCitesTheNeighbors() {
        RequirementDraft draft = draftWith(hit(1, 0.90, "40"), hit(2, 0.80, "70"), hit(3, 0.30, "200"));
        Estimate estimate = strategy.estimate(draft).orElseThrow();
        // sorted by hours: 40 (0.9), 70 (0.8), 200 (0.3); half of 2.0 = 1.0 is reached at 70
        assertThat(estimate.hours()).isEqualByComparingTo("70");
        assertThat(estimate.source()).isEqualTo(EstimateSource.MEMORY);
        assertThat(estimate.citations()).extracting(Citation::chunkId).containsExactly(1L, 2L, 3L);
        assertThat(estimate.citations().get(0).documentName()).isEqualTo("P1.pdf");
        assertThat(estimate.citations().get(0).similarity()).isEqualTo(0.9);
    }

    @Test
    void ignoresNeighborsBelowTheSimilarityFloorOrWithoutHours() {
        RequirementDraft draft = draftWith(hit(1, 0.90, "40"), hit(2, 0.10, "70"), hit(3, 0.95, null));
        assertThat(strategy.estimate(draft)).isEmpty();
    }

    @Test
    void needsAtLeastTheMinimumNumberOfNeighbors() {
        assertThat(strategy.estimate(draftWith(hit(1, 0.99, "40")))).isEmpty();
        assertThat(strategy.estimate(draftWith())).isEmpty();
        properties.setMinNeighbors(1);
        assertThat(strategy.estimate(draftWith(hit(1, 0.99, "40")))).isPresent();
    }

    @Test
    void roundsCitationSimilarityToThreeDecimals() {
        properties.setMinSimilarity(0.1);
        Estimate estimate = strategy.estimate(draftWith(hit(1, 0.123456, "10"), hit(2, 0.98765, "12")))
                .orElseThrow();
        assertThat(estimate.citations()).extracting(Citation::similarity).containsExactly(0.988, 0.123);
    }

    private RequirementDraft draftWith(RetrievedChunk... hits) {
        RequirementDraft draft = PipelineTestFixture.requirement(new BigDecimal("30"));
        draft.setEvidence(List.of(hits));
        return draft;
    }

    private RetrievedChunk hit(long id, double similarity, String hours) {
        return new RetrievedChunk(id, 100 + id, "P" + id + ".pdf", 1, "Diseno responsive: item " + id,
                "Diseno responsive", hours == null ? null : new BigDecimal(hours), similarity);
    }
}
