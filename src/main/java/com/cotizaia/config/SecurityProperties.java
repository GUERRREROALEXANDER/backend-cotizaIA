package com.cotizaia.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * External JWT and session settings keep signing material out of source
 * control and make the lifetime boundary configurable (spec 02): a stolen
 * access token dies quickly (application.yml defaults JWT_TTL to 15 minutes)
 * while the refresh cookie outlives it (default 7 days) until rotation or
 * revocation ends it.
 */
@ConfigurationProperties("security.jwt")
public record SecurityProperties(String secret, Duration ttl, String issuer, Duration refreshTtl) {

    @ConstructorBinding
    public SecurityProperties {
        ttl = ttl == null ? Duration.ofHours(8) : ttl;
        refreshTtl = refreshTtl == null ? Duration.ofDays(7) : refreshTtl;
        issuer = issuer == null || issuer.isBlank() ? "cotizaia" : issuer;
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("security.jwt.ttl must be positive");
        }
        if (refreshTtl.isNegative() || refreshTtl.isZero()) {
            throw new IllegalArgumentException("security.jwt.refresh-ttl must be positive");
        }
    }

    /** Convenience for callers that do not configure the refresh lifetime; the P7D default applies. */
    public SecurityProperties(String secret, Duration ttl, String issuer) {
        this(secret, ttl, issuer, null);
    }
}
