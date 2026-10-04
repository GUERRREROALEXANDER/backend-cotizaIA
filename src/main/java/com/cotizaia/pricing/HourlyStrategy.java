package com.cotizaia.pricing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Bills work at the agency's blended hourly rate (project.txt section 6).
 * The rate comes from the agency catalog, keeping the provisional quote under human control.
 *
 * <p>Design pattern - <b>Strategy</b>: this ConcreteStrategy implements
 * {@link PricingStrategy} and is selected by {@link PricingStrategyResolver}.
 */
@Component
public class HourlyStrategy extends AbstractPricingStrategy {

    public HourlyStrategy(PricingProperties properties) {
    }

    @Override
    public PricingModel model() {
        return PricingModel.HOURLY;
    }

    @Override
    public PricingQuote price(PricingRequest request) {
        return quote(request, BigDecimal.ONE,
                List.of(new MilestoneSpec("Pago por horas ejecutadas", BigDecimal.ONE)),
                "Hours at the agency's blended rate");
    }
}
