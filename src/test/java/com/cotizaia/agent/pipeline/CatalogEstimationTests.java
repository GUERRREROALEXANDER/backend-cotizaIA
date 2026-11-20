package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.RequirementType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CatalogEstimationTests {

    private final CatalogEstimation estimation = new CatalogEstimation();

    @Test
    void usesTheHoursFromTheCatalog() {
        RequirementType type = mock(RequirementType.class);
        when(type.getEstimatedHours()).thenReturn(new BigDecimal("16"));
        RequirementDraft draft = new RequirementDraft(type, "Carrito", BigDecimal.ONE);

        Estimate estimate = estimation.estimate(draft).orElseThrow();

        assertThat(estimate.hours()).isEqualByComparingTo("16");
        assertThat(estimate.source()).isEqualTo(EstimateSource.CATALOG);
        assertThat(draft.isDefaultHoursUsed()).isFalse();
    }

    @Test
    void aCatalogEntryWithoutHoursPassesToTheNextStrategy() {
        RequirementType type = mock(RequirementType.class);
        when(type.getName()).thenReturn("Carrito");

        assertThat(estimation.estimate(new RequirementDraft(type, "Carrito", BigDecimal.ONE))).isEmpty();
    }
}
