package com.cotizaia.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/** Validated input for agency operations. */
public record RateRequest(@NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 18, fraction = 0) BigDecimal copPerHour, Instant effectiveFrom, Instant effectiveTo) {
}
