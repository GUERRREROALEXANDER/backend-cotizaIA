package com.cotizaia.agent.llm;

import java.time.Duration;
import java.util.Optional;

/** Distinguishes transient vendor failures from requests that retry cannot repair. */
public class LlmException extends RuntimeException {

    private final boolean retryable;
    private final Duration retryAfter;

    public LlmException(String message, boolean retryable) {
        this(message, retryable, (Duration) null, null);
    }

    public LlmException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, (Duration) null, cause);
    }

    /** @param retryAfter provider hint for the next attempt, or {@code null} when absent */
    public LlmException(String message, boolean retryable, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
