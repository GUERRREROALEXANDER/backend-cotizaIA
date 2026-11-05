package com.cotizaia.agent.embedding;

import java.time.Duration;
import java.util.Optional;

/** Distinguishes transient vendor failures from requests that a retry cannot repair. */
public class EmbeddingException extends RuntimeException {

    private final boolean retryable;
    private final Duration retryAfter;

    public EmbeddingException(String message, boolean retryable) {
        this(message, retryable, (Duration) null, null);
    }

    public EmbeddingException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, (Duration) null, cause);
    }

    /** @param retryAfter provider hint for the next attempt, or {@code null} when absent */
    public EmbeddingException(String message, boolean retryable, Duration retryAfter, Throwable cause) {
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
