package com.cotizaia.pricing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Prices fixed packages with the agency's risk buffer (project.txt section 6).
 * This keeps scope risk in an explicit configuration rather than an AI-selected final price.
 *
 * <p>Design pattern - <b>Strategy</b>: this ConcreteStrategy implements
 * {@link PricingStrategy} and is selected by {@link PricingStrategyResolver}.
 */
@Component
public class FixedPriceStrategy extends AbstractPricingStrategy {

    private final PricingProperties properties;

    public FixedPriceStrategy(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public PricingModel model() {
        return PricingModel.FIXED;
    }

    @Override
    public PricingQuote price(PricingRequest request) {
        return quote(request, BigDecimal.ONE.add(properties.fixedRiskBuffer()),
                List.of(new MilestoneSpec("Anticipo", new BigDecimal("0.50")),
                        new MilestoneSpec("Entrega", new BigDecimal("0.50"))),
                "Fixed scope with agency risk buffer");
    }
}
