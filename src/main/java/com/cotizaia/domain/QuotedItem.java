package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One priced line of a {@link Proposal}: a requirement from the brief charged
 * at a role rate. It is a composed child, so it is only created through
 * {@link Proposal#addItem} and never without its aggregate root.
 *
 * <p>Design pattern - the constructor is a factory that derives the invariant
 * {@code lineTotal = hours * unitPrice} instead of accepting it, and the line is
 * immutable afterwards (no setters for hours, unitPrice or lineTotal). That is
 * what lets {@link Proposal} guarantee "totals recompute from items" and makes a
 * stored line total that disagrees with its inputs impossible.
 */
@Entity
@Table(name = "quoted_items")
public class QuotedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private ExtractedRequirement requirement;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hours;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected QuotedItem() {
    }

    QuotedItem(Proposal proposal, ExtractedRequirement requirement, BigDecimal hours, BigDecimal unitPrice) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        if (requirement == null) {
            throw new IllegalArgumentException("requirement must not be null");
        }
        if (hours == null || hours.signum() < 0) {
            throw new IllegalArgumentException("hours must not be null or negative");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be null or negative");
        }
        this.proposal = proposal;
        this.requirement = requirement;
        this.hours = money(hours);
        this.unitPrice = money(unitPrice);
        this.lineTotal = money(this.hours.multiply(this.unitPrice));
    }

    /** Detaches the line when its proposal drops it, keeping both sides in sync. */
    void detach() {
        this.proposal = null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public ExtractedRequirement getRequirement() {
        return requirement;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
