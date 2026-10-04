package com.cotizaia.pricing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Prices long projects with coordination overhead and phase payments (project.txt section 6).
 * Explicit phase shares make the provisional payment schedule auditable by a human.
 *
 * <p>Design pattern - <b>Strategy</b>: this ConcreteStrategy implements
 * {@link PricingStrategy} and is selected by {@link PricingStrategyResolver}.
 */
@Component
public class PhasedStrategy extends AbstractPricingStrategy {

    private final PricingProperties properties;

    public PhasedStrategy(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public PricingModel model() {
        return PricingModel.PHASED;
    }

    @Override
    public PricingQuote price(PricingRequest request) {
        return quote(request, BigDecimal.ONE.add(properties.phasedCoordinationOverhead()),
                List.of(new MilestoneSpec("Discovery & design", new BigDecimal("0.20")),
                        new MilestoneSpec("Development", new BigDecimal("0.50")),
                        new MilestoneSpec("QA", new BigDecimal("0.15")),
                        new MilestoneSpec("Launch", new BigDecimal("0.15"))),
                "Long project with phase coordination overhead");
    }
}
