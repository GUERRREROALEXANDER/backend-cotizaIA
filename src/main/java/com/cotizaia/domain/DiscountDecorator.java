package com.cotizaia.domain;

import java.math.BigDecimal;

/**
 * Concrete Decorator: a commercial discount takes 10% off whatever amount is
 * wrapped. Its {@link #amount()} is negative, which is why
 * {@code proposal_extras.amount} allows negative values.
 */
public final class DiscountDecorator extends ProposalPriceDecorator {

    /** Extra kind persisted in {@code proposal_extras.type}. */
    public static final String TYPE = "DISCOUNT";

    /** 10% expressed as a fraction. */
    private static final BigDecimal RATE = new BigDecimal("0.10");

    public DiscountDecorator(PricedProposal delegate) {
        super(delegate);
    }

    @Override
    public BigDecimal total() {
        return money(delegate.total().multiply(BigDecimal.ONE.subtract(RATE)));
    }

    @Override
    public String extraType() {
        return TYPE;
    }

    @Override
    public BigDecimal value() {
        return RATE;
    }
}
