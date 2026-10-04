package com.cotizaia.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks that blocking findings stop the downstream chain after deduplication. */
class AmbiguityCheckHandlerTests {

    @Test
    void blocksWhenScopeIsMissing() {
        PipelineContext context = PipelineTestFixture.context();
        context.getFindings().add(new AmbiguityFinding("NO_SCOPE", "Scope?", true));
        context.getFindings().add(new AmbiguityFinding("NO_SCOPE", "Scope?", true));
        HandlerResult result = new AmbiguityCheckHandler(new AmbiguityDetector(new ObjectMapper()))
                .process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.HALT);
        assertThat(context.getFindings()).extracting(AmbiguityFinding::code)
                .containsOnlyOnce("NO_SCOPE");
    }

    @Test
    void nonBlockingFindingsFlagButDoNotHalt() {
        PipelineContext context = PipelineTestFixture.context();
        HandlerResult result = new AmbiguityCheckHandler(new AmbiguityDetector(new ObjectMapper()))
                .process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.FLAG_FOR_HUMAN);
        assertThat(context.getFindings()).allMatch(finding -> !finding.blocking());
    }

    @Test
    void removesResolvedCodesFromUpstreamAndDetectedFindings() {
        PipelineContext original = PipelineTestFixture.context();
        PipelineContext context = new PipelineContext(original.getBrief(), 1L, "Agency", "Client",
                List.of(new Clarification("NO_SCOPE", "Scope?", "Web"),
                        new Clarification("DEADLINE_MISSING", "Deadline?", "To be confirmed")));
        context.getFindings().add(new AmbiguityFinding("NO_SCOPE", "Scope?", true));
        HandlerResult result = new AmbiguityCheckHandler(new AmbiguityDetector(new ObjectMapper()))
                .process(context);
        assertThat(result.decision()).isEqualTo(HandlerDecision.CONTINUE);
        assertThat(context.getFindings()).isEmpty();
    }
}
