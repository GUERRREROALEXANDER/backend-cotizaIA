package com.cotizaia.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Validated input for agency operations. */
public record UpdateRateRequest(@NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 18, fraction = 0) BigDecimal copPerHour) {
}
