package com.cotizaia.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Validated input for agency operations. */
public record RequirementTypeRequest(@NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal estimatedHours) {
}
