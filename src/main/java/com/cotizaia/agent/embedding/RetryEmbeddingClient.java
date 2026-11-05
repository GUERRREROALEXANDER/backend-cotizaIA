package com.cotizaia.agent.embedding;

import com.cotizaia.agent.llm.Sleeper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Decorator that retries only transient embedding failures with bounded exponential backoff. */
public class RetryEmbeddingClient implements EmbeddingClient {

    private final EmbeddingClient delegate;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double multiplier;
    private final Duration maxBackoff;
    private final Sleeper sleeper;

    public RetryEmbeddingClient(EmbeddingClient delegate, EmbeddingProperties.Retry retry, Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate);
        this.maxAttempts = retry.getMaxAttempts();
        this.initialBackoff = retry.getInitialBackoff();
        this.multiplier = retry.getMultiplier();
        this.maxBackoff = retry.getMaxBackoff();
        this.sleeper = Objects.requireNonNull(sleeper);
        if (maxAttempts < 1 || initialBackoff.isNegative() || multiplier < 1.0 || maxBackoff.isNegative()) {
            throw new IllegalArgumentException("Invalid embedding retry configuration");
        }
    }

    @Override
    public List<float[]> embed(List<String> texts, EmbeddingPurpose purpose) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.embed(texts, purpose);
            } catch (EmbeddingException exception) {
                if (!exception.isRetryable() || attempt >= maxAttempts) {
                    throw exception;
                }
                long delay = retryDelay(attempt, exception);
                try {
                    sleeper.sleep(Duration.ofMillis(delay));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new EmbeddingException("Embedding retry interrupted", false, interrupted);
                }
            }
        }
    }

    /** Waits for the provider hint when it is longer than our backoff, but never past the cap. */
    private long retryDelay(int attempt, EmbeddingException exception) {
        long backoffMillis = (long) (initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1));
        long delayMillis = Math.max(backoffMillis, exception.retryAfter().map(Duration::toMillis).orElse(0L));
        return Math.min(delayMillis, maxBackoff.toMillis());
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    public EmbeddingClient delegate() {
        return delegate;
    }
}
