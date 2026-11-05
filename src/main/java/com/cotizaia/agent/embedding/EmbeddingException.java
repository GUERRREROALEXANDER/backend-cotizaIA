package com.cotizaia.agent.embedding;

/** Distinguishes transient vendor failures from requests that a retry cannot repair. */
public class EmbeddingException extends RuntimeException {

    private final boolean retryable;

    public EmbeddingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmbeddingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
