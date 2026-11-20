package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimateSourceTests {

    @Test
    void storedNamesReadBackAsSources() {
        assertThat(EstimateSource.fromStored("MEMORY")).isEqualTo(EstimateSource.MEMORY);
        assertThat(EstimateSource.fromStored("CATALOG")).isEqualTo(EstimateSource.CATALOG);
        assertThat(EstimateSource.fromStored("DEFAULT")).isEqualTo(EstimateSource.DEFAULT);
    }

    @Test
    void unknownOrEmptyNamesReadAsNoSource() {
        assertThat(EstimateSource.fromStored(null)).isNull();
        assertThat(EstimateSource.fromStored("")).isNull();
        assertThat(EstimateSource.fromStored("memory")).isNull();
        assertThat(EstimateSource.fromStored("RAG")).isNull();
    }

    @Test
    void anEstimateWithoutCitationsHasAnEmptyList() {
        assertThat(new Estimate(BigDecimal.TEN, EstimateSource.CATALOG, null).citations()).isEmpty();
    }

    @Test
    void anEstimateCopiesItsCitations() {
        List<Citation> citations = new ArrayList<>(List.of(new Citation(1L, "P.pdf", 1, 0.9, BigDecimal.ONE)));
        Estimate estimate = new Estimate(BigDecimal.ONE, EstimateSource.MEMORY, citations);
        citations.clear();

        assertThat(estimate.citations()).hasSize(1);
    }
}
