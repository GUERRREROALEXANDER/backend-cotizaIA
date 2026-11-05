package com.cotizaia.agent.pipeline;

import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Second strategy: the agency's fixed catalog hours for the requirement type. */
@Component
@Order(2)
public class CatalogEstimation implements EstimationStrategy {

    @Override
    public Optional<Estimate> estimate(RequirementDraft draft) {
        if (draft.getRequirementType() == null || draft.getRequirementType().getEstimatedHours() == null) {
            return Optional.empty();
        }
        return Optional.of(new Estimate(draft.getRequirementType().getEstimatedHours(), EstimateSource.CATALOG,
                java.util.List.of()));
    }
}
