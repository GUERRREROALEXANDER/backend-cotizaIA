package com.cotizaia.agent.pipeline;

import java.util.Optional;

/**
 * Strategy: each implementation knows one source of hours (agency memory, catalog, default).
 * The estimate handler asks them in priority order and takes the first answer.
 */
public interface EstimationStrategy {

    Optional<Estimate> estimate(RequirementDraft draft);
}
