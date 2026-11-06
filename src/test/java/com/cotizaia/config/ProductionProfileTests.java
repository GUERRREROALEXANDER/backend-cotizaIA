package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Spec 08: production hides API docs and internals by default; the limiter's windows behave. */
class ProductionProfileTests {

    @Test
    void prodProfileHidesDocsAndInternalsByDefault() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("prod", new ClassPathResource("application-prod.yml"));
        Map<String, Object> prod = new java.util.HashMap<>();
        sources.forEach(source -> ((Map<?, ?>) source.getSource()).forEach((k, v) -> prod.put(k.toString(), v.toString())));

        assertThat(prod.get("springdoc.api-docs.enabled")).hasToString("${SWAGGER_ENABLED:false}");
        assertThat(prod.get("springdoc.swagger-ui.enabled")).hasToString("${SWAGGER_ENABLED:false}");
        assertThat(prod.get("management.endpoints.web.exposure.include")).hasToString("health");
        assertThat(prod.get("management.endpoint.health.show-details")).hasToString("never");
        assertThat(prod.get("server.error.include-stacktrace")).hasToString("never");
        assertThat(prod.get("server.error.include-message")).hasToString("never");
        assertThat(prod.get("server.forward-headers-strategy")).hasToString("framework");
        // No default: production must refuse to start without a real signing key (dev generates a random one).
        assertThat(prod.get("security.jwt.secret")).hasToString("${JWT_SECRET}");
    }

    @Test
    void limiterAllowsCapacityPerWindowThenTellsHowLongToWait() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
        RateLimiter limiter = new RateLimiter(clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("ip:1", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        RateLimiter.Decision blocked = limiter.tryAcquire("ip:1", 3, Duration.ofMinutes(1));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(limiter.tryAcquire("ip:2", 3, Duration.ofMinutes(1)).allowed()).isTrue();

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("ip:1", 3, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
