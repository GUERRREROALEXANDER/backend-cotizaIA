package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTests {

    private final MovableClock clock = new MovableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToTheCapacityInsideOneWindow() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("ip:1", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        RateLimiter.Decision refused = limiter.tryAcquire("ip:1", 3, Duration.ofMinutes(1));

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void retryAfterShrinksAsTheWindowEnds() {
        limiter.tryAcquire("ip:1", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofSeconds(45));

        assertThat(limiter.tryAcquire("ip:1", 1, Duration.ofMinutes(1)).retryAfterSeconds()).isEqualTo(15);
    }

    @Test
    void retryAfterIsNeverZeroWhileRefused() {
        limiter.tryAcquire("ip:1", 1, Duration.ofSeconds(1));
        clock.advance(Duration.ofMillis(900));

        assertThat(limiter.tryAcquire("ip:1", 1, Duration.ofSeconds(1)).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void aNewWindowStartsWhenTheOldOneEnds() {
        limiter.tryAcquire("ip:1", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("ip:1", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void eachKeyHasItsOwnBudget() {
        limiter.tryAcquire("agency:1", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("agency:1", 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("agency:2", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void aMissingOrInvalidWindowFallsBackToOneMinute() {
        limiter.tryAcquire("ip:1", 1, null);

        assertThat(limiter.tryAcquire("ip:1", 1, Duration.ZERO).retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void zeroCapacityRefusesEverything() {
        assertThat(limiter.tryAcquire("ip:1", 0, Duration.ofMinutes(1)).allowed()).isFalse();
    }

    @Test
    void manyKeysDoNotBreakTheLimiterWhenOldWindowsAreSwept() {
        for (int i = 0; i < 600; i++) {
            limiter.tryAcquire("ip:" + i, 1, Duration.ofSeconds(1));
        }
        clock.advance(Duration.ofSeconds(2));

        assertThat(limiter.tryAcquire("ip:0", 1, Duration.ofSeconds(1)).allowed()).isTrue();
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-11-20T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
