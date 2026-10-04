package com.cotizaia.api;

import com.cotizaia.domain.AgentExecution;
import com.cotizaia.domain.ExecutionStatus;
import java.time.Instant;
import java.util.List;

/**
 * One pipeline run with its full step timeline, as returned by
 * {@code GET /api/executions/{id}}. The steps are already ordered by the
 * service, so this record is the live-demo feed in its final shape.
 */
public record AgentExecutionResponse(
        Long id,
        Long briefId,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        List<AgentStepResponse> steps) {

    public static AgentExecutionResponse from(AgentExecution execution, List<AgentStepResponse> steps) {
        return new AgentExecutionResponse(
                execution.getId(),
                execution.getBrief().getId(),
                execution.getStatus(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getErrorMessage(),
                steps);
    }
}
