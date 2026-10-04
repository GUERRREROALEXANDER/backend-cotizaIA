package com.cotizaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * One recorded extra of a {@link Proposal} (project.txt section 9 schema:
 * Propuesta -> Extra). It is the persisted footprint of a
 * {@link ProposalPriceDecorator}: {@code type} is the decorator's kind,
 * {@code percentOrFixed} its rate, and {@code amount} the signed money delta it
 * contributed to the total.
 *
 * <p>It is a composed child, so it is only created through
 * {@link Proposal#addExtra} and never without its aggregate root. The
 * constructor enforces the invariants: a non-blank type, a non-negative rate
 * magnitude, and a non-null amount (which may be negative for a discount).
 */
@Entity
@Table(name = "proposal_extras")
public class ProposalExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "percent_or_fixed", nullable = false, precision = 12, scale = 2)
    private BigDecimal percentOrFixed;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProposalExtra() {
    }

    ProposalExtra(Proposal proposal, String type, BigDecimal percentOrFixed, BigDecimal amount) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (percentOrFixed == null || percentOrFixed.signum() < 0) {
            throw new IllegalArgumentException("percentOrFixed must not be null or negative");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        this.proposal = proposal;
        this.type = type;
        this.percentOrFixed = money(percentOrFixed);
        this.amount = money(amount);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Detaches the extra when its proposal drops it, keeping both sides in sync. */
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

    public String getType() {
        return type;
    }

    public BigDecimal getPercentOrFixed() {
        return percentOrFixed;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
