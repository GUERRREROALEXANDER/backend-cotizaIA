package com.cotizaia.api;

import com.cotizaia.domain.Rate;
import java.math.BigDecimal;
import java.time.Instant;

/** Public projection without persistence internals. */
public record RateResponse(Long id, Long roleId, BigDecimal copPerHour,
        Instant effectiveFrom, Instant effectiveTo) {

    public static RateResponse from(Rate rate) {
        return new RateResponse(rate.getId(), rate.getRole().getId(), BigDecimal.valueOf(rate.getCopPerHour()),
                rate.getEffectiveFrom(), rate.getEffectiveTo());
    }
}
