package com.cotizaia.agent.pipeline;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Applies the best available estimate to every extracted requirement (project.txt section 2) so prices depend on
 * deterministic hours rather than model guesses.
 *
 * <p>Design pattern - <b>Strategy</b>: the order of strategies encodes trust. Agency memory wins when it has enough
 * similar evidence, the fixed catalog is the normal fallback, and the configured default is the last resort that
 * always requires human review.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler enriches extracted requirements;
 * {@link PipelineHandler} forwards them to the pricing successor even when fallback hours require review.
 */
@Component
public class EstimateHandler extends PipelineHandler {

    private final List<EstimationStrategy> strategies;

    /** Order comes from Spring's {@code @Order} on the strategy beans: memory, catalog, default. */
    @Autowired
    public EstimateHandler(List<EstimationStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /** Convenience constructor for callers without Spring context: catalog plus default only. */
    public EstimateHandler(PipelineProperties properties) {
        this(List.of(new CatalogEstimation(), new DefaultEstimation(properties)));
    }

    @Override
    public String name() {
        return "Estimate";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        int memory = 0;
        int catalog = 0;
        int defaults = 0;
        for (RequirementDraft draft : context.getRequirements()) {
            Estimate estimate = strategies.stream()
                    .map(strategy -> strategy.estimate(draft))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No estimation strategy answered"));
            draft.setEstimatedHours(estimate.hours());
            draft.setEstimateSource(estimate.source());
            draft.setCitations(estimate.citations());
            draft.setDefaultHoursUsed(estimate.source() == EstimateSource.DEFAULT);
            switch (estimate.source()) {
                case MEMORY -> memory++;
                case CATALOG -> catalog++;
                case DEFAULT -> defaults++;
            }
        }
        String summary = "memory " + memory + ", catalog " + catalog + ", default " + defaults;
        return defaults > 0 ? HandlerResult.flag(summary) : HandlerResult.proceed(summary);
    }
}
