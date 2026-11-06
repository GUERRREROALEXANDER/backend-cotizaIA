package com.cotizaia.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fixed-window counter (spec 08). Each key gets a window that starts
 * on its first request; requests inside the window increment a counter and are
 * rejected once the capacity is exhausted, with the number of seconds left in
 * the window as {@code Retry-After}.
 *
 * <p>The clock is injected so window boundaries are testable. Counters live in
 * one instance only; a multi-instance deployment needs a shared store
 * (Redis/Bucket4j), which is documented as a known limitation.</p>
 */
public class RateLimiter {

    private static final long SWEEP_EVERY = 256;

    private final Clock clock;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private final AtomicLong acquisitions = new AtomicLong();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Consumes one slot for {@code key}. When the window is full the call is
     * rejected and carries at least one second of retry-after, so clients do
     * not hammer a window that is about to turn over.
     */
    public Decision tryAcquire(String key, int capacity, Duration window) {
        Instant now = clock.instant();
        Duration length = window == null || window.isZero() || window.isNegative()
                ? Duration.ofMinutes(1)
                : window;
        long[] retryAfter = {0L};

        windows.compute(key, (ignored, current) -> {
            Window active = current;
            if (active == null || !now.isBefore(active.windowEnd)) {
                active = new Window(now.plus(length));
            }
            if (active.count < capacity) {
                active.count++;
                retryAfter[0] = 0L;
            } else {
                retryAfter[0] = Math.max(1L, Duration.between(now, active.windowEnd).getSeconds());
            }
            return active;
        });

        sweepExpired(now);
        return new Decision(retryAfter[0] == 0L, retryAfter[0]);
    }

    /**
     * Lazy eviction: a bounded sweep removes windows that have already ended,
     * so one-off keys (every new IP) do not grow the map forever while the
     * common path stays O(1) per request.
     */
    private void sweepExpired(Instant now) {
        if (acquisitions.incrementAndGet() % SWEEP_EVERY != 0L) {
            return;
        }
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().windowEnd));
    }

    /** Outcome of one {@link #tryAcquire}: allowed, or how long to wait in seconds. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Window {

        private final Instant windowEnd;

        private int count;

        private Window(Instant windowEnd) {
            this.windowEnd = windowEnd;
        }
    }
}
