package com.cotizaia.agent.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

/**
 * Reads the provider's {@code Retry-After} hint so retries can wait as long as
 * the vendor asks instead of hammering a per-minute quota.
 */
public final class RetryAfter {

    private RetryAfter() {
    }

    /** Parses delay-seconds (integer or decimal); blank or malformed values yield empty. */
    public static Optional<Duration> parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal seconds = new BigDecimal(headerValue.trim());
            if (seconds.signum() < 0) {
                return Optional.empty();
            }
            long millis = seconds.multiply(BigDecimal.valueOf(1000))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            return Optional.of(Duration.ofMillis(millis));
        } catch (NumberFormatException | ArithmeticException malformed) {
            return Optional.empty();
        }
    }
}
