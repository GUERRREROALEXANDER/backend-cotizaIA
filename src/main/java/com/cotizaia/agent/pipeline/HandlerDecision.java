package com.cotizaia.agent.pipeline;

/**
 * Describes each link's routing decision (project.txt section 6) independently of execution success or failure.
 * Flags preserve downstream work, whereas a halt records skipped successors and returns clarification questions.
 */
public enum HandlerDecision {
    CONTINUE,
    FLAG_FOR_HUMAN,
    HALT
}
