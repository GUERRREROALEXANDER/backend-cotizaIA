package com.cotizaia.agent.llm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Circuit Breaker (Nygard, "Release It!") in front of one LLM provider of the failover chain.
 *
 * <ul>
 *   <li>CLOSED: calls pass through; consecutive {@link LlmException}s are counted.</li>
 *   <li>OPEN: after {@code failureThreshold} consecutive failures the provider is skipped for {@code openDuration}:
 *       calls fail at once with a non-retryable {@link LlmException}, so the surrounding {@link RetryLlmClient} gives
 *       up immediately and {@link FallbackLlmClient} moves to the next provider without waiting on backoff.</li>
 *   <li>HALF_OPEN: once the open window ends, a single trial call is let through; success closes the circuit,
 *       failure opens it again for another window.</li>
 * </ul>
 *
 * Only {@link LlmException} counts as a provider failure; any other exception is a bug and propagates untouched.
 */
public class CircuitBreakerLlmClient implements LlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerLlmClient.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final LlmClient delegate;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private boolean trialInFlight;

    public CircuitBreakerLlmClient(LlmClient delegate, int failureThreshold, Duration openDuration, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate);
        this.openDuration = Objects.requireNonNull(openDuration);
        this.clock = Objects.requireNonNull(clock);
        if (failureThreshold < 1 || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("Invalid circuit breaker configuration");
        }
        this.failureThreshold = failureThreshold;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        admit();
        try {
            LlmResponse response = delegate.complete(request);
            onSuccess();
            return response;
        } catch (LlmException failure) {
            onFailure();
            throw failure;
        } catch (RuntimeException bug) {
            releaseTrial();
            throw bug;
        }
    }

    /** Decides whether this call may reach the provider, moving OPEN to HALF_OPEN when the window has passed. */
    private synchronized void admit() {
        if (state == State.OPEN) {
            Instant reopensAt = openedAt.plus(openDuration);
            if (clock.instant().isBefore(reopensAt)) {
                throw new LlmException("Circuit open for provider " + delegate.provider() + " until " + reopensAt
                        + "; skipping it", false);
            }
            state = State.HALF_OPEN;
            LOGGER.info("Circuit for provider {} is half-open; sending one trial call", delegate.provider());
        }
        if (state == State.HALF_OPEN) {
            if (trialInFlight) {
                throw new LlmException("Circuit half-open for provider " + delegate.provider()
                        + "; a trial call is already in flight", false);
            }
            trialInFlight = true;
        }
    }

    private synchronized void onSuccess() {
        if (state != State.CLOSED) {
            LOGGER.info("Circuit for provider {} closed again", delegate.provider());
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
        trialInFlight = false;
    }

    private synchronized void onFailure() {
        trialInFlight = false;
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
            LOGGER.warn("Circuit for provider {} opened after {} consecutive failures; skipping it for {}",
                    delegate.provider(), consecutiveFailures, openDuration);
        }
    }

    private synchronized void releaseTrial() {
        trialInFlight = false;
    }

    public synchronized State state() {
        return state;
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    public LlmClient delegate() {
        return delegate;
    }
}
