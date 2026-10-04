package com.cotizaia.pricing;

import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps each model to exactly one strategy (project.txt section 6).
 * Rejecting duplicate registrations at startup prevents ambiguous agency quotes.
 *
 * <p>Design pattern - <b>Strategy</b>: this selector connects the Context
 * {@link PricingService} to registered ConcreteStrategies through {@link PricingStrategy}.
 */
@Component
public class PricingStrategyResolver {

    private final EnumMap<PricingModel, PricingStrategy> strategies = new EnumMap<>(PricingModel.class);

    public PricingStrategyResolver(List<PricingStrategy> strategies) {
        for (PricingStrategy strategy : strategies) {
            if (this.strategies.putIfAbsent(strategy.model(), strategy) != null) {
                throw new IllegalStateException("Duplicate pricing strategy: " + strategy.model());
            }
        }
    }

    public PricingStrategy forModel(PricingModel model) {
        PricingStrategy strategy = strategies.get(model);
        if (strategy == null) {
            throw new IllegalStateException("Missing pricing strategy: " + model);
        }
        return strategy;
    }
}
