package com.cotizaia.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTests {

    @Test
    void missingValuesFallBackToSafeDefaults() {
        SecurityProperties properties = new SecurityProperties("secret", null, null, null);

        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(properties.refreshTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.issuer()).isEqualTo("cotizaia");
    }

    @Test
    void aBlankIssuerAlsoFallsBack() {
        assertThat(new SecurityProperties("secret", null, "  ").issuer()).isEqualTo("cotizaia");
    }

    @Test
    void theShortConstructorKeepsTheSevenDayRefresh() {
        assertThat(new SecurityProperties("secret", Duration.ofMinutes(15), "x").refreshTtl())
                .isEqualTo(Duration.ofDays(7));
    }

    @Test
    void lifetimesMustBePositive() {
        assertThatThrownBy(() -> new SecurityProperties("s", Duration.ZERO, null, null))
                .hasMessageContaining("security.jwt.ttl");
        assertThatThrownBy(() -> new SecurityProperties("s", Duration.ofMinutes(-1), null, null))
                .hasMessageContaining("security.jwt.ttl");
        assertThatThrownBy(() -> new SecurityProperties("s", null, null, Duration.ZERO))
                .hasMessageContaining("refresh-ttl");
    }
}
