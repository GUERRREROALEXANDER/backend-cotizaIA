package com.cotizaia.pricing;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds pricing margins separately from agency rates (project.txt section 2).
 * Defaults keep each ConcreteStrategy predictable when no override is configured.
 */
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(BigDecimal fixedRiskBuffer, BigDecimal phasedCoordinationOverhead) {

    public PricingProperties {
        fixedRiskBuffer = fixedRiskBuffer == null ? new BigDecimal("0.20") : fixedRiskBuffer;
        phasedCoordinationOverhead = phasedCoordinationOverhead == null
                ? new BigDecimal("0.10") : phasedCoordinationOverhead;
        if (fixedRiskBuffer.signum() < 0 || phasedCoordinationOverhead.signum() < 0) {
            throw new IllegalArgumentException("pricing margins must not be negative");
        }
    }
}
