package com.cotizaia.pricing;

/** Strategy role: pricing models can change without changing quote callers (project.txt section 6). */
public interface PricingStrategy {

    PricingModel model();

    PricingQuote price(PricingRequest request);
}
