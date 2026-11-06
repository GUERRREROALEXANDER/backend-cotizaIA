package com.cotizaia.agent.pipeline;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Last strategy: a configured default when neither memory nor catalog can answer.
 * It marks the draft so the pipeline still surfaces the fallback for human review.
 */
@Component
@Order(3)
public class DefaultEstimation implements EstimationStrategy {

    private final PipelineProperties properties;

    public DefaultEstimation(PipelineProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Estimate> estimate(RequirementDraft draft) {
        draft.setDefaultHoursUsed(true);
        return Optional.of(new Estimate(BigDecimal.valueOf(properties.getDefaultHours()), EstimateSource.DEFAULT,
                List.of()));
    }
}
