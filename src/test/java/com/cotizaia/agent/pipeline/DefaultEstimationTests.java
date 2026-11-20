package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cotizaia.domain.RequirementType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DefaultEstimationTests {

    @Test
    void alwaysAnswersWithTheConfiguredDefaultHours() {
        PipelineProperties properties = new PipelineProperties();
        properties.setDefaultHours(12);
        RequirementDraft draft = new RequirementDraft(mock(RequirementType.class), "Blog", BigDecimal.ONE);

        Estimate estimate = new DefaultEstimation(properties).estimate(draft).orElseThrow();

        assertThat(estimate.hours()).isEqualByComparingTo("12");
        assertThat(estimate.source()).isEqualTo(EstimateSource.DEFAULT);
        assertThat(estimate.citations()).isEmpty();
    }

    @Test
    void flagsTheDraftSoTheReviewerSeesTheGuess() {
        RequirementDraft draft = new RequirementDraft(mock(RequirementType.class), "Blog", BigDecimal.ONE);

        new DefaultEstimation(new PipelineProperties()).estimate(draft);

        assertThat(draft.isDefaultHoursUsed()).isTrue();
    }
}
