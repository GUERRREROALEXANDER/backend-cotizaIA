package com.cotizaia.agent.pipeline;

import com.cotizaia.domain.StepStatus;

/**
 * Reports chain observations (project.txt section 2) through the existing recorder's persistence boundary.
 * Handlers can report timing and outcomes without receiving or writing execution repositories themselves.
 */
@FunctionalInterface
public interface StepLog {

    void record(String handler, String inputSummary, String outputSummary, Long durationMs, StepStatus status);
}
