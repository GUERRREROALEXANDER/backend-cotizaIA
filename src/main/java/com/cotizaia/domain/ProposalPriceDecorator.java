package com.cotizaia.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Abstract Decorator (project.txt section 6 pattern 6). It holds the wrapped
 * {@link PricedProposal}, forwards {@link #subtotal()} untouched, and leaves
 * {@link #total()} to each concrete extra.
 *
 * <p>Extras self-describe through {@link #extraType()} and {@link #value()} and
 * report their signed money delta through {@link #amount()}, which is derived
 * here as {@code total - delegate.total()}. Because the delta is computed from
 * the running total, stacking is compounding and order-independent for
 * percentage extras (multiplication commutes), while
 * {@link #recordOn(Proposal)} lets the chain persist exactly what it applied.
 *
 * <p>This class is the whole extension point: <b>adding a new extra is a new
 * subclass only.</b> No enum, registry or switch has to change.
 */
public abstract class ProposalPriceDecorator implements PricedProposal {

    /** The wrapped proposal, either the base or the next decorator inside. */
    protected final PricedProposal delegate;

    protected ProposalPriceDecorator(PricedProposal delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public BigDecimal subtotal() {
        return delegate.subtotal();
    }

    /** Kind stored in {@code proposal_extras.type}; declared by the extra itself. */
    public abstract String extraType();

    /**
     * Rate as a fraction for percentage extras (0.25) or the flat fee for fixed
     * ones, stored in {@code proposal_extras.percent_or_fixed}. Non-negative.
     */
    public abstract BigDecimal value();

    /** Signed money delta this wrapper adds to the running total. */
    public BigDecimal amount() {
        return money(total().subtract(delegate.total()));
    }

    /**
     * Persists the whole chain onto the proposal, innermost extra first, so the
     * {@code proposal_extras} rows are the audit trail of this decoration. The
     * proposal's total is re-derived from those rows, so after recording it
     * equals {@link #total()}.
     */
    public final void recordOn(Proposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        if (delegate instanceof ProposalPriceDecorator inner) {
            inner.recordOn(proposal);
        }
        proposal.addExtra(extraType(), value(), amount());
    }

    protected static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
