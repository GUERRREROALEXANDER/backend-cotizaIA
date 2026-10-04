package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the explicit review flag when an estimate uses fallback hours. */
class EstimateHandlerTests {

    @Test
    void defaultsMissingHoursAndFlagsReview() {
        PipelineContext context = PipelineTestFixture.context();
        RequirementDraft draft = PipelineTestFixture.requirement(null);
        context.setRequirements(List.of(draft));
        HandlerResult result = new EstimateHandler(new PipelineProperties()).process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(draft.getEstimatedHours()).isEqualByComparingTo(new BigDecimal("8"));
        assertThat(draft.isDefaultHoursUsed()).isTrue();
    }

    @Test
    void preservesCatalogHoursAndDoesNotFlag() {
        PipelineContext context = PipelineTestFixture.context();
        RequirementDraft draft = PipelineTestFixture.requirement(new BigDecimal("24.00"));
        context.setRequirements(List.of(draft));
        HandlerResult result = new EstimateHandler(new PipelineProperties()).process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(draft.getEstimatedHours()).isEqualByComparingTo("24.00");
        assertThat(draft.isDefaultHoursUsed()).isFalse();
    }
}
