package com.cotizaia.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Validated input for agency operations. */
public record AdjustHoursRequest(@NotNull @DecimalMin("0.0")
        @Digits(integer = 8, fraction = 2) BigDecimal hours) {
}
