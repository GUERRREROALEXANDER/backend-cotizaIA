package com.cotizaia.api;

import com.cotizaia.pricing.PricingModel;

/** Public projection without persistence internals. */
public record PricingModelResponse(PricingModel pricingModel) {

    public static PricingModelResponse from(PricingModel model) {
        return new PricingModelResponse(model);
    }
}
