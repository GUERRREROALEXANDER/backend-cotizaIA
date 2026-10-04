package com.cotizaia.domain;

/**
 * Lifecycle of one {@link AgentExecution} (project.txt section 2: the pipeline
 * runs live and the panel shows how it ended). Stored in
 * {@code agent_executions.status} and constrained by
 * {@code ck_agent_executions_status}.
 *
 * <p>A run is created {@code RUNNING} and, because the log must exist "even on
 * failure", is always driven to a terminal status: {@code SUCCEEDED} on a clean
 * pass or {@code FAILED} when a Chain handler throws or halts the run.
 */
public enum ExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
