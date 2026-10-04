package com.cotizaia.domain;

import java.math.BigDecimal;

/**
 * Concrete Decorator: extended warranty adds a flat fee (project.txt section 6
 * pattern 6). Unlike the percentage extras, a flat extra is order-sensitive
 * (a surcharge applied before it inflates the fee), which is exactly why the
 * decorator chain is explicit: the caller chooses the stack order.
 */
public final class ExtendedWarrantyDecorator extends ProposalPriceDecorator {

    /** Extra kind persisted in {@code proposal_extras.type}. */
    public static final String TYPE = "EXTENDED_WARRANTY";

    /** Default warranty fee when the caller does not set an agency price. */
    private static final BigDecimal DEFAULT_FEE = new BigDecimal("150.00");

    private final BigDecimal fee;

    public ExtendedWarrantyDecorator(PricedProposal delegate) {
        this(delegate, DEFAULT_FEE);
    }

    public ExtendedWarrantyDecorator(PricedProposal delegate, BigDecimal fee) {
        super(delegate);
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException("fee must not be null or negative");
        }
        this.fee = money(fee);
    }

    @Override
    public BigDecimal total() {
        return money(delegate.total().add(fee));
    }

    @Override
    public String extraType() {
        return TYPE;
    }

    @Override
    public BigDecimal value() {
        return fee;
    }
}
