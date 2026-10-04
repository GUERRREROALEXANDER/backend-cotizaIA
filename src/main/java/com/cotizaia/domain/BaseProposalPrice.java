package com.cotizaia.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Concrete component of the Decorator pattern: wraps the item-derived
 * {@link Proposal#getSubtotal()} and returns it unchanged. {@link
 * ProposalPriceDecorator}s are stacked on top of this base, so a caller starts
 * from the quoted work and layers commercial extras one wrapper at a time.
 *
 * <p>Taking the subtotal (not the proposal total) as the base is deliberate:
 * the base must always be the pure item sum, or stacking decorators over an
 * already-adjusted total would double-count earlier extras.
 */
public final class BaseProposalPrice implements PricedProposal {

    private final BigDecimal subtotal;

    public BaseProposalPrice(Proposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        this.subtotal = money(proposal.getSubtotal());
    }

    /** Direct amount, useful when the quote did not come from a persisted aggregate. */
    public BaseProposalPrice(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() < 0) {
            throw new IllegalArgumentException("subtotal must not be null or negative");
        }
        this.subtotal = money(subtotal);
    }

    @Override
    public BigDecimal subtotal() {
        return subtotal;
    }

    @Override
    public BigDecimal total() {
        return subtotal;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
