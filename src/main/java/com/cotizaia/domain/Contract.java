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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for the contract behind a {@link Proposal} (project.txt
 * section 4 payment flow; section 9 schema: Propuesta -> Contrato -> Clausula).
 *
 * <p>Design patterns:
 * <ul>
 *   <li><b>Factory Method</b> - a contract is never built with a public
 *       constructor; the only way in is {@link #draftFor(Proposal)}, which
 *       starts it in {@code DRAFT} and sets the owning side of the association.
 *       This is what makes "draft generated with the proposal" the single,
 *       controlled birth path.</li>
 *   <li><b>State</b> - {@link #issue(String, BigDecimal, Instant)} delegates the
 *       legal-transition rules to {@link ContractStatus}, so an already-issued
 *       contract cannot be issued again.</li>
 *   <li><b>Composition</b> - it composes its {@link Clause} children: one save
 *       writes the contract and its clauses, and dropping the contract drops
 *       them too.</li>
 * </ul>
 *
 * <p>A draft carries no issue or payment evidence. The final flip to
 * {@code ISSUED} goes through {@link #issue}, which refuses to run without a
 * simulated payment reference, amount and timestamp, and writes the database's
 * paired {@code ck_contracts_issue} invariant. That is what enforces "final
 * ISSUED only after simulated payment" in the domain and in storage. The
 * accept-plus-payment transaction itself lives in the service layer.
 */
@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One contract per proposal; the contract is a composed child. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;

    /** Null while DRAFT; set together with the simulated payment on issue. */
    @Column(name = "issued_at")
    private Instant issuedAt;

    /** Simulated payment evidence recorded atomically with the ISSUED flip. */
    @Column(name = "payment_reference", length = 80)
    private String paymentReference;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Clause> clauses = new ArrayList<>();

    protected Contract() {
    }

    private Contract(Proposal proposal) {
        this.proposal = proposal;
        this.status = ContractStatus.DRAFT;
    }

    /**
     * Factory method on the aggregate: a draft contract never exists without its
     * proposal, and it is born unissued so it can be reviewed before payment.
     */
    public static Contract draftFor(Proposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        return new Contract(proposal);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Appends a clause, assigning the next display order. The clause is a
     * composed child, so the owning side is set here and it is never saved
     * without this contract.
     */
    public Clause addClause(String text) {
        Clause clause = new Clause(this, text, clauses.size() + 1);
        clauses.add(clause);
        return clause;
    }

    /** Drops a clause when the contract no longer needs it. */
    public void removeClause(Clause clause) {
        if (clauses.remove(clause)) {
            clause.detach();
        }
    }

    /**
     * Issues the final contract: records the simulated payment evidence and
     * flips {@code DRAFT -> ISSUED} in one step. Every payment field is
     * validated before the status changes, so a rejected issue leaves the draft
     * exactly as it was (no half-recorded payment, still unissued).
     *
     * @throws IllegalArgumentException when the payment evidence is incomplete.
     * @throws IllegalStateException    when the contract is not a draft.
     */
    public void issue(String paymentReference, BigDecimal paidAmount, Instant paidAt) {
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("payment reference is required to issue the contract");
        }
        if (paidAmount == null || paidAmount.signum() < 0) {
            throw new IllegalArgumentException("paid amount must not be null or negative");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("paidAt is required to issue the contract");
        }
        if (!status.canTransitionTo(ContractStatus.ISSUED)) {
            throw new IllegalStateException("Illegal contract transition: " + status + " -> ISSUED");
        }
        this.paymentReference = paymentReference;
        this.paidAmount = paidAmount.setScale(2, RoundingMode.HALF_UP);
        this.paidAt = paidAt;
        this.issuedAt = Instant.now();
        this.status = ContractStatus.ISSUED;
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Unmodifiable view: clauses are appended through {@link #addClause}. */
    public List<Clause> getClauses() {
        return Collections.unmodifiableList(clauses);
    }
}
