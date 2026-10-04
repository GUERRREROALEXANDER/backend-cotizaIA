package com.cotizaia.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Auditable child of a proposal (project.txt section 6 pattern 9: State).
 * The Context creates a row only after its ConcreteState accepts an event.
 */
@Entity
@Table(name = "proposal_status_changes")
public class ProposalStatusChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private ProposalStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ProposalStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ProposalStatusChange() {
    }

    ProposalStatusChange(Proposal proposal, ProposalStatus fromStatus,
            ProposalStatus toStatus, Instant changedAt) {
        if (proposal == null || fromStatus == null || toStatus == null || changedAt == null
                || fromStatus == toStatus) {
            throw new IllegalArgumentException("valid status change is required");
        }
        this.proposal = proposal;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public ProposalStatus getFromStatus() {
        return fromStatus;
    }

    public ProposalStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
