package com.cotizaia.domain;

/**
 * Tracks clarification resolution (project.txt section 2) independently of an agent run.
 * Resolved questions retain their answers for subsequent analysis.
 */
public enum QuestionStatus {
    OPEN,
    RESOLVED
}
