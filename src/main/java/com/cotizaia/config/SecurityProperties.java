package com.cotizaia.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External JWT settings keep signing material out of source control and make
 * session lifetime configurable for the agency access boundary (project.txt section 2).
 */
@ConfigurationProperties("security.jwt")
public record SecurityProperties(String secret, Duration ttl, String issuer) {

    public SecurityProperties {
        ttl = ttl == null ? Duration.ofHours(8) : ttl;
        issuer = issuer == null || issuer.isBlank() ? "cotizaia" : issuer;
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("security.jwt.ttl must be positive");
        }
    }
}
