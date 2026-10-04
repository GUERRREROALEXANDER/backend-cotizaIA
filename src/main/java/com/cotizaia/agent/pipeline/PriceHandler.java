package com.cotizaia.agent.pipeline;

import com.cotizaia.pricing.PricingLine;
import com.cotizaia.pricing.PricingService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Delegates provisional pricing to the agency strategy (project.txt section 2) so AI never chooses the final price.
 * Empty scope passes to the ambiguity gate without invoking pricing or inventing billable lines.
 *
 * <p>Design pattern - <b>Chain of Responsibility</b>: this ConcreteHandler calculates the in-memory quote;
 * {@link PipelineHandler} forwards it to ambiguity checking while human approval remains a later operation.
 */
@Component
public class PriceHandler extends PipelineHandler {

    private final PricingService pricingService;

    public PriceHandler(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Override
    public String name() {
        return "Price";
    }

    @Override
    protected HandlerResult process(PipelineContext context) {
        if (context.getRequirements().isEmpty()) {
            return HandlerResult.proceed("nothing to price");
        }
        List<PricingLine> lines = context.getRequirements().stream()
                .map(draft -> new PricingLine(draft.key(), draft.getDescription(), draft.getEstimatedHours()))
                .toList();
        context.setQuote(pricingService.quote(context.getAgencyId(), null, lines));
        return HandlerResult.proceed("provisional quote calculated");
    }
}
