package com.cotizaia.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Shares monetary rounding and reconciliation across concrete Strategy implementations. */
abstract class AbstractPricingStrategy implements PricingStrategy {

    protected static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    protected PricingQuote quote(PricingRequest request, BigDecimal factor, List<MilestoneSpec> schedule,
            String rationale) {
        BigDecimal unitPrice = money(request.hourlyRate().multiply(factor));
        List<PricedLine> lines = request.lines().stream()
                .map(line -> new PricedLine(line.key(), line.label(), line.hours(), unitPrice,
                        money(line.hours().multiply(unitPrice))))
                .toList();
        BigDecimal subtotal = money(lines.stream()
                .map(PricedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<PaymentMilestone> milestones = new ArrayList<>();
        BigDecimal allocated = money(BigDecimal.ZERO);
        for (int index = 0; index < schedule.size(); index++) {
            MilestoneSpec spec = schedule.get(index);
            BigDecimal amount = index == schedule.size() - 1
                    ? subtotal.subtract(allocated)
                    : money(subtotal.multiply(spec.share()));
            milestones.add(new PaymentMilestone(spec.name(), spec.share(), amount));
            allocated = allocated.add(amount);
        }
        return new PricingQuote(model(), money(request.hourlyRate()), lines, subtotal, milestones, rationale);
    }

    protected record MilestoneSpec(String name, BigDecimal share) {
    }
}
