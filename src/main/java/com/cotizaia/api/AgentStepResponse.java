package com.cotizaia.api;

import com.cotizaia.domain.AgentStep;
import com.cotizaia.domain.StepStatus;

/**
 * One row of the ordered step timeline. {@code stepOrder} is included so the
 * client can render the chain position without relying on JSON array order.
 */
public record AgentStepResponse(
        Long id,
        int stepOrder,
        String handler,
        String inputSummary,
        String outputSummary,
        Long durationMs,
        StepStatus status) {

    public static AgentStepResponse from(AgentStep step) {
        return new AgentStepResponse(
                step.getId(),
                step.getStepOrder(),
                step.getHandler(),
                step.getInputSummary(),
                step.getOutputSummary(),
                step.getDurationMs(),
                step.getStatus());
    }
}
