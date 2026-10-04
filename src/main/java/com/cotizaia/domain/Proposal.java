package com.cotizaia.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for a priced offer over a {@link Brief} (project.txt section 9
 * schema: Propuesta -> ItemCotizado; section 6 pattern 2 Builder, pattern 9
 * State).
 *
 * <p>Design patterns:
 * <ul>
 *   <li><b>Builder</b> - the proposal has many optional fields (terms,
 *       schedule, extras, validity date) and several required children, so the
 *       only way in is {@link Proposal.Builder}, which validates the required
 *       brief at {@link Builder#build()}. There is no public or telescoping
 *       constructor.</li>
 *   <li><b>State</b> - {@link #transitionTo(ProposalStatus)} delegates the
 *       legal-transition rules to {@link ProposalStatus}, so an illegal jump is
 *       rejected in the domain.</li>
 * </ul>
 *
 * <p>It composes its {@link QuotedItem} children: a single save persists the
 * whole graph and removing the proposal removes them. {@code subtotal} and
 * {@code total} are recomputed from the lines by {@link #recomputeTotals()} and
 * exposed read-only, so a stored total that disagrees with the items cannot be
 * produced by the domain.
 */
@Entity
@Table(name = "proposals")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brief_id", nullable = false)
    private Brief brief;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column
    private String terms;

    @Column
    private String schedule;

    @Column
    private String extras;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuotedItem> items = new ArrayList<>();

    protected Proposal() {
    }

    private Proposal(Builder builder) {
        this.brief = builder.brief;
        this.status = builder.status;
        this.validUntil = builder.validUntil;
        this.terms = builder.terms;
        this.schedule = builder.schedule;
        this.extras = builder.extras;
        this.subtotal = money(BigDecimal.ZERO);
        this.total = money(BigDecimal.ZERO);
        for (ItemSpec spec : builder.itemSpecs) {
            addItem(spec.requirement(), spec.hours(), spec.unitPrice());
        }
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Factory method on the aggregate: the line never exists without its
     * proposal and the owning side of the association is set here.
     */
    public QuotedItem addItem(ExtractedRequirement requirement, BigDecimal hours, BigDecimal unitPrice) {
        QuotedItem item = new QuotedItem(this, requirement, hours, unitPrice);
        items.add(item);
        recomputeTotals();
        return item;
    }

    /** Drops a line and immediately reconciles the totals. */
    public void removeItem(QuotedItem item) {
        if (items.remove(item)) {
            item.detach();
            recomputeTotals();
        }
    }

    /** Moves the proposal through its lifecycle; illegal jumps are rejected. */
    public void transitionTo(ProposalStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("next status must not be null");
        }
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal proposal transition: " + status + " -> " + next);
        }
        this.status = next;
    }

    /**
     * Single source of truth for the amounts: both are re-derived from the
     * current lines, so no caller can leave them out of sync with the items.
     */
    private void recomputeTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (QuotedItem item : items) {
            sum = sum.add(item.getLineTotal());
        }
        this.subtotal = money(sum);
        this.total = this.subtotal;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Brief getBrief() {
        return brief;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public String getTerms() {
        return terms;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getExtras() {
        return extras;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Unmodifiable view: lines are appended through {@link #addItem}. */
    public List<QuotedItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /** Input for one line carried by the Builder until the aggregate exists. */
    private record ItemSpec(ExtractedRequirement requirement, BigDecimal hours, BigDecimal unitPrice) {
    }

    /**
     * Fluent builder for the aggregate (project.txt section 6 pattern 2). Only
     * the brief is required; everything else has a default or stays null, and
     * {@link #build()} is the single gate that enforces the required field.
     */
    public static class Builder {

        private final List<ItemSpec> itemSpecs = new ArrayList<>();
        private Brief brief;
        private ProposalStatus status = ProposalStatus.RECEIVED;
        private Instant validUntil;
        private String terms;
        private String schedule;
        private String extras;

        public Builder brief(Brief brief) {
            this.brief = brief;
            return this;
        }

        public Builder status(ProposalStatus status) {
            this.status = status;
            return this;
        }

        public Builder validUntil(Instant validUntil) {
            this.validUntil = validUntil;
            return this;
        }

        public Builder terms(String terms) {
            this.terms = terms;
            return this;
        }

        public Builder schedule(String schedule) {
            this.schedule = schedule;
            return this;
        }

        public Builder extras(String extras) {
            this.extras = extras;
            return this;
        }

        public Builder addItem(ExtractedRequirement requirement, BigDecimal hours, BigDecimal unitPrice) {
            this.itemSpecs.add(new ItemSpec(requirement, hours, unitPrice));
            return this;
        }

        public Proposal build() {
            if (brief == null) {
                throw new IllegalStateException("brief is required");
            }
            if (status == null) {
                status = ProposalStatus.RECEIVED;
            }
            return new Proposal(this);
        }
    }
}
