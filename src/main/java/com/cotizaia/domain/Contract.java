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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root for the contract behind a {@link Proposal} (project.txt
 * section 4 acceptance flow; section 9 schema: Propuesta -> Contrato ->
 * Clausula).
 *
 * <p>Design patterns:
 * <ul>
 *   <li><b>Factory Method</b> - a contract is never built with a public
 *       constructor; the only way in is {@link #draftFor(Proposal)}, which
 *       starts it in {@code DRAFT} and sets the owning side of the association.
 *       This is what makes "draft generated with the proposal" the single,
 *       controlled birth path.</li>
 *   <li><b>State</b> - {@link #issue()} delegates the
 *       legal-transition rules to {@link ContractStatus}, so an already-issued
 *       contract cannot be issued again.</li>
 *   <li><b>Composition</b> - it composes its {@link Clause} children: one save
 *       writes the contract and its clauses, and dropping the contract drops
 *       them too.</li>
 * </ul>
 *
 * <p>A draft carries no issue evidence. The flip to {@code ISSUED} goes through
 * {@link #issue}, which writes the database's paired
 * {@code ck_contracts_issue} invariant. Payment is a later aggregate of its
 * own (issue #12) and leaves no trace on this row. The accept-plus-issue
 * transaction itself lives in the service layer.
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

    /** Null while DRAFT; set on issue. */
    @Column(name = "issued_at")
    private Instant issuedAt;

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
     * proposal, and it is born unissued so it can be reviewed before acceptance.
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
     * Issues the contract: flips {@code DRAFT -> ISSUED} in one step and
     * stamps the issue time.
     *
     * @throws IllegalStateException when the contract is not a draft.
     */
    public void issue() {
        if (!status.canTransitionTo(ContractStatus.ISSUED)) {
            throw new IllegalStateException("Illegal contract transition: " + status + " -> ISSUED");
        }
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Unmodifiable view: clauses are appended through {@link #addClause}. */
    public List<Clause> getClauses() {
        return Collections.unmodifiableList(clauses);
    }
}
