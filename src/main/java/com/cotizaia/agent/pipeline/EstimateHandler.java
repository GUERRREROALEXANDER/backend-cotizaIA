package com.cotizaia.agent.pipeline;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Applies catalog estimates (project.txt section 2) so prices depend on deterministic hours rather than model guesses.
 * A fallback estimate remains visible through both a review decision and the requirement's default-hours flag.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler enriches extracted requirements;
 * {@link PipelineHandler} forwards them to the pricing successor even when fallback hours require review.
 */
@Component
public class EstimateHandler extends PipelineHandler {

    private final PipelineProperties properties;

    public EstimateHandler(PipelineProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "Estimate";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        boolean fallback = false;
        for (RequirementDraft draft : context.getRequirements()) {
            BigDecimal hours = draft.getRequirementType().getEstimatedHours();
            draft.setDefaultHoursUsed(hours == null);
            if (hours == null) {
                hours = BigDecimal.valueOf(properties.getDefaultHours());
                fallback = true;
            }
            draft.setEstimatedHours(hours);
        }
        return fallback ? HandlerResult.flag("estimated with default hours")
                : HandlerResult.proceed("hours estimated");
    }
}
