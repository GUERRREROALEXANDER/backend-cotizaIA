package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.document.DocumentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks that composition exposes an immutable draft to the later facade. */
class ComposeHandlerTests {

    @Test
    void assemblesDraftFromContext() {
        PipelineContext context = PipelineTestFixture.context();
        context.setProjectType("WEB_SITE");
        context.setSummary("Summary");
        HandlerResult result = new ComposeHandler().process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(context.getDraft().projectType()).isEqualTo("WEB_SITE");
        assertThat(context.getDraft().summary()).isEqualTo("Summary");
    }

    @Test
    void composedDraftProtectsEstimatesAndPdfBytesFromLaterMutation() {
        PipelineContext context = PipelineTestFixture.context();
        RequirementDraft requirement = PipelineTestFixture.requirement(BigDecimal.TEN);
        requirement.setEstimatedHours(BigDecimal.TEN);
        byte[] pdf = {1, 2, 3};
        context.setRequirements(List.of(requirement));
        context.setDocuments(Map.of(DocumentType.PROPOSAL, pdf));
        new ComposeHandler().process(context);
        requirement.setEstimatedHours(BigDecimal.ONE);
        pdf[0] = 9;
        context.getDraft().requirements().get(0).setEstimatedHours(BigDecimal.ZERO);
        context.getDraft().documents().get(DocumentType.PROPOSAL)[0] = 8;
        assertThat(context.getDraft().requirements().get(0).getEstimatedHours()).isEqualByComparingTo("10");
        assertThat(context.getDraft().documents().get(DocumentType.PROPOSAL)).containsExactly(1, 2, 3);
    }
}
