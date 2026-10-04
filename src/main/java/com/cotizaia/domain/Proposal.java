package com.cotizaia.domain;

import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.domain.state.ProposalState;
import com.cotizaia.domain.state.ProposalStates;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
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
 *   <li><b>State</b> - Context {@code Proposal} delegates events to State
 *       {@link ProposalState}; ConcreteStates in {@code domain.state} own the
 *       legal transitions and reject illegal jumps.</li>
 * </ul>
 *
 * <p>It composes its {@link QuotedItem} children and its structured
 * {@link ProposalExtra} extras: a single save persists the whole graph and
 * removing the proposal removes them. {@code subtotal} is recomputed from the
 * lines and {@code total} is the subtotal plus the extras by
 * {@link #recomputeTotals()}, both exposed read-only, so a stored total that
 * disagrees with its inputs cannot be produced by the domain.
 *
 * <p><b>Decorator integration.</b> The extras are the persisted result of a
 * {@link PricedProposal} chain: each {@link ProposalPriceDecorator} records its
 * signed delta through {@link #addExtra}, so after a decoration the total here
 * equals the decorator's {@link PricedProposal#total()} (project.txt section 6
 * pattern 6).
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

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProposalExtra> appliedExtras = new ArrayList<>();

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProposalStatusChange> statusHistory = new ArrayList<>();

    /**
     * The proposal's smart schedule (project.txt section 5, section 9 schema:
     * Propuesta -> Cronograma). Optional: a proposal in review may not have one
     * yet. Kept here so an hour edit can cascade into it.
     */
    @OneToOne(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Schedule schedulePlan;

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

    /**
     * Records one applied extra (project.txt section 9: Propuesta -> Extra).
     * Called by {@link ProposalPriceDecorator#recordOn}, which supplies the
     * decorator's type, rate magnitude and signed delta.
     */
    public ProposalExtra addExtra(String type, BigDecimal percentOrFixed, BigDecimal amount) {
        ProposalExtra extra = new ProposalExtra(this, type, percentOrFixed, amount);
        appliedExtras.add(extra);
        recomputeTotals();
        return extra;
    }

    /** Drops an extra and immediately reconciles the total. */
    public void removeExtra(ProposalExtra extra) {
        if (appliedExtras.remove(extra)) {
            extra.detach();
            recomputeTotals();
        }
    }

    /**
     * Sum of hours across the current quoted lines; the schedule derives every
     * phase duration from this.
     */
    public BigDecimal getTotalQuotedHours() {
        BigDecimal sum = BigDecimal.ZERO;
        for (QuotedItem item : items) {
            sum = sum.add(item.getHours());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Approves a human adjustment of one quoted line's hours and cascades it
     * (project.txt section 5: "Si el humano cambia las horas al aprobar, el
     * cronograma se recalcula en cascada"). The line is immutable, so the edit
     * is applied by replacing it on the aggregate with the same requirement,
     * unit price and new hours; adding it recomputes the totals, and then the
     * attached schedule re-derives every phase duration and downstream shift.
     */
    public QuotedItem editHours(QuotedItem item, BigDecimal newHours) {
        if (!items.contains(item)) {
            throw new IllegalArgumentException("item does not belong to this proposal");
        }
        if (newHours == null || newHours.signum() < 0) {
            throw new IllegalArgumentException("hours must not be null or negative");
        }
        int index = items.indexOf(item);
        QuotedItem edited = new QuotedItem(this, item.getRequirement(), newHours, item.getUnitPrice());
        items.set(index, edited);
        item.detach();
        recomputeTotals();
        // Cascade: the schedule derives from hours, so it must follow the edit.
        if (schedulePlan != null) {
            schedulePlan.recalculate();
        }
        return edited;
    }

    /**
     * Attaches (or replaces) the proposal's smart schedule and places it. The
     * owning side is set here, mirroring how {@link #addItem} owns lines.
     */
    public Schedule attachSchedule(BigDecimal hoursPerWeek) {
        if (schedulePlan != null) {
            schedulePlan.detach();
        }
        Schedule schedule = Schedule.forProposal(this, hoursPerWeek);
        this.schedulePlan = schedule;
        schedule.recalculate();
        return schedule;
    }

    public Schedule getSchedulePlan() {
        return schedulePlan;
    }

    /** Moves the proposal through its lifecycle; illegal jumps are rejected. */
    public ProposalStatusChange transitionTo(ProposalStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("next status must not be null");
        }
        if (next == ProposalStatus.RECEIVED) {
            throw new InvalidStateTransitionException(status, null, next, getState().allowedTransitions());
        }
        return apply(ProposalEvent.leadingTo(next));
    }

    /** Delegates an event to the current State and records the accepted transition in the audit trail. */
    public ProposalStatusChange apply(ProposalEvent event) {
        ProposalStatus from = status;
        ProposalState next = getState().handle(event);
        status = next.status();
        ProposalStatusChange change = new ProposalStatusChange(this, from, status, Instant.now());
        statusHistory.add(change);
        return change;
    }

    /** Resolves the State that owns lifecycle rules for the persisted status. */
    public ProposalState getState() {
        return ProposalStates.of(status);
    }

    /** Returns the read-only audit trail of transitions accepted by the State. */
    public List<ProposalStatusChange> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    /**
     * Single source of truth for the amounts: the subtotal is re-derived from
     * the current lines and the total adds every recorded extra, so no caller
     * can leave them out of sync with their inputs.
     */
    private void recomputeTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (QuotedItem item : items) {
            sum = sum.add(item.getLineTotal());
        }
        this.subtotal = money(sum);
        BigDecimal extraTotal = BigDecimal.ZERO;
        for (ProposalExtra extra : appliedExtras) {
            extraTotal = extraTotal.add(extra.getAmount());
        }
        this.total = money(this.subtotal.add(extraTotal));
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

    /** Unmodifiable view: extras are appended through {@link #addExtra}. */
    public List<ProposalExtra> getAppliedExtras() {
        return Collections.unmodifiableList(appliedExtras);
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
