package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTests {

    private final RateLimitProperties properties = new RateLimitProperties();

    @Test
    void limitsAreOnByDefault() {
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    void loginAllowsTenTriesEveryFifteenMinutes() {
        assertThat(properties.getAuth().getCapacity()).isEqualTo(10);
        assertThat(properties.getAuth().getWindow()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void aiCallsAllowThirtyPerMinute() {
        assertThat(properties.getAi().getCapacity()).isEqualTo(30);
        assertThat(properties.getAi().getWindow()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void bucketsCanBeTunedFromConfiguration() {
        RateLimitProperties.Bucket bucket = new RateLimitProperties.Bucket();
        bucket.setCapacity(3);
        bucket.setWindow(Duration.ofSeconds(30));
        properties.setAuth(bucket);
        properties.setEnabled(false);

        assertThat(properties.getAuth().getCapacity()).isEqualTo(3);
        assertThat(properties.getAuth().getWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.isEnabled()).isFalse();
    }
}
