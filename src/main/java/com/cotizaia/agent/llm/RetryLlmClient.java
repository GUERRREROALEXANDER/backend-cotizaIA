package com.cotizaia.agent.llm;

import java.time.Duration;
import java.util.Objects;

/** Decorator that retries only transient LLM failures with bounded exponential backoff. */
public class RetryLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double multiplier;
    private final Sleeper sleeper;

    public RetryLlmClient(LlmClient delegate, LlmProperties.Retry retry, Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate);
        this.maxAttempts = retry.getMaxAttempts();
        this.initialBackoff = retry.getInitialBackoff();
        this.multiplier = retry.getMultiplier();
        this.sleeper = Objects.requireNonNull(sleeper);
        if (maxAttempts < 1 || initialBackoff.isNegative() || multiplier < 1.0) {
            throw new IllegalArgumentException("Invalid LLM retry configuration");
        }
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.complete(request);
            } catch (LlmException exception) {
                if (!exception.isRetryable() || attempt >= maxAttempts) {
                    throw exception;
                }
                long delay = (long) (initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1));
                try {
                    sleeper.sleep(Duration.ofMillis(delay));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new LlmException("LLM retry interrupted", false, interrupted);
                }
            }
        }
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    public LlmClient delegate() {
        return delegate;
    }
}
