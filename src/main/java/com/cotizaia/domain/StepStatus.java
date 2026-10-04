package com.cotizaia.domain;

/**
 * Outcome of a single {@link AgentStep} in the Chain (project.txt section 6
 * pattern 10: each link decides whether the brief passes, halts, or is marked
 * for the human). Stored in {@code agent_steps.status} and constrained by
 * {@code ck_agent_steps_status}.
 */
public enum StepStatus {
    SUCCEEDED,
    FAILED,
    SKIPPED
}
