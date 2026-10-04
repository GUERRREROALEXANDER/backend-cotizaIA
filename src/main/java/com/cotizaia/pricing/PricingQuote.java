package com.cotizaia.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Holds a strategy's provisional quote for subsequent human approval. */
public record PricingQuote(PricingModel model, BigDecimal hourlyRate, List<PricedLine> lines,
        BigDecimal subtotal, List<PaymentMilestone> milestones, String rationale) {

    public PricingQuote {
        lines = List.copyOf(lines);
        milestones = List.copyOf(milestones);
        BigDecimal milestoneTotal = milestones.stream()
                .map(PaymentMilestone::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (milestoneTotal.compareTo(subtotal) != 0) {
            throw new IllegalArgumentException("milestones must sum to subtotal");
        }
    }
}
