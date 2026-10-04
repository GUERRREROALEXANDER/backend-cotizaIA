package com.cotizaia.pricing;

import java.math.BigDecimal;

/** Carries catalog-estimated hours and a stable requirement key into pricing. */
public record PricingLine(String key, String label, BigDecimal hours) {

    public PricingLine {
        if (key == null || key.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("key and label must not be blank");
        }
        if (hours == null || hours.signum() < 0) {
            throw new IllegalArgumentException("hours must not be negative");
        }
    }
}
