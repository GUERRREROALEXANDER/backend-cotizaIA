package com.cotizaia.agent;

import com.cotizaia.domain.AgentExecution;

/**
 * Result of one recorded pipeline run: the persisted {@link AgentExecution}
 * (with its step timeline) plus the final value produced by the chain. The
 * pipeline output is deliberately kept out of the log table so the run-log
 * schema stays generic.
 */
public record AgentRun(AgentExecution execution, Object result) {
}
