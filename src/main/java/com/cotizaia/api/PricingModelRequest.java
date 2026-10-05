package com.cotizaia.api;

import com.cotizaia.pricing.PricingModel;
import jakarta.validation.constraints.NotNull;

/** Validated input for agency operations. */
public record PricingModelRequest(@NotNull PricingModel pricingModel) {
}
