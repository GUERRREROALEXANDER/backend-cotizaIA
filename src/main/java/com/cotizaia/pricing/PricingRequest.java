package com.cotizaia.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Freezes estimated lines and the agency-derived rate for one strategy calculation. */
public record PricingRequest(List<PricingLine> lines, BigDecimal hourlyRate) {

    public PricingRequest {
        if (lines == null || hourlyRate == null || hourlyRate.signum() <= 0) {
            throw new IllegalArgumentException("lines and a positive hourlyRate are required");
        }
        lines = List.copyOf(lines);
    }
}
