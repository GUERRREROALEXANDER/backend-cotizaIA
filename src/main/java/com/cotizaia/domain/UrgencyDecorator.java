package com.cotizaia.domain;

import java.math.BigDecimal;

/**
 * Concrete Decorator: urgent delivery adds a 25% surcharge on whatever amount
 * is wrapped, so it composes with other extras without a dedicated
 * "urgent and discounted" class (project.txt section 6 pattern 6).
 */
public final class UrgencyDecorator extends ProposalPriceDecorator {

    /** Extra kind persisted in {@code proposal_extras.type}. */
    public static final String TYPE = "URGENCY";

    /** +25% expressed as a fraction. */
    private static final BigDecimal SURCHARGE = new BigDecimal("0.25");

    public UrgencyDecorator(PricedProposal delegate) {
        super(delegate);
    }

    @Override
    public BigDecimal total() {
        return money(delegate.total().multiply(BigDecimal.ONE.add(SURCHARGE)));
    }

    @Override
    public String extraType() {
        return TYPE;
    }

    @Override
    public BigDecimal value() {
        return SURCHARGE;
    }
}
