package com.cotizaia.service;

import com.cotizaia.domain.Payment;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalCommandRecord;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.repository.ProposalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads proposal review views inside application transactions (project.txt sections 2 and 5).
 * Existing review operations and their associated documents are captured together without requiring an open web session.
 */
@Service
@Transactional
public class ProposalService {

    private final ApprovalQueueService approval;

    private final ProposalRepository proposals;

    private final DocumentService documents;

    private final PaymentService payments;

    public ProposalService(ApprovalQueueService approval, ProposalRepository proposals,
            DocumentService documents, PaymentService payments) {
        this.approval = approval;
        this.proposals = proposals;
        this.documents = documents;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<Proposal> list(Long agencyId, ProposalStatus status) {
        if (status != null) {
            return proposals.findByStatusAndBriefClientAgencyIdOrderByCreatedAtAsc(status, agencyId);
        }
        return proposals.findByBriefClientAgencyIdOrderByCreatedAtDesc(agencyId);
    }

    @Transactional(readOnly = true)
    public List<Proposal> queue(Long agencyId) {
        return approval.queue(agencyId);
    }

    @Transactional(readOnly = true)
    public Detail get(Long agencyId, Long id) {
        return detail(approval.get(agencyId, id));
    }

    /**
     * Records the client's first view of a sent offer ("vista", Spec 15).
     * Agency-scoped: a foreign or unknown proposal is a 404. Only a
     * {@code SENT} or {@code NEGOTIATING} offer can be viewed (any other state
     * is a 409). Idempotent and status-neutral: {@link Proposal#markViewed}
     * keeps the first timestamp and never changes the lifecycle status.
     */
    public Detail viewed(Long agencyId, Long id) {
        Proposal proposal = approval.get(agencyId, id);
        if (proposal.getStatus() != ProposalStatus.SENT
                && proposal.getStatus() != ProposalStatus.NEGOTIATING) {
            throw new IllegalStateException(
                    "Proposal can only be viewed in SENT or NEGOTIATING, not " + proposal.getStatus());
        }
        // TIMESTAMP WITH TIME ZONE has microsecond precision on both H2 and
        // PostgreSQL. Truncating Instant.now() (nanosecond precision) makes the
        // value set here identical to the value read back, so the first response
        // and every later idempotent response expose the exact same timestamp.
        proposal.markViewed(Instant.now().truncatedTo(ChronoUnit.MICROS));
        proposals.saveAndFlush(proposal);
        return detail(proposal);
    }

    public Detail adjustHours(Long agencyId, Long id, Long itemId, BigDecimal hours) {
        return detail(approval.adjustHours(agencyId, id, itemId, hours));
    }

    public Detail addExtra(Long agencyId, Long id, String type) {
        return detail(approval.addExtra(agencyId, id, type));
    }

    public Detail removeExtra(Long agencyId, Long id, Long extraId) {
        return detail(approval.removeExtra(agencyId, id, extraId));
    }

    public Detail undo(Long agencyId, Long id) {
        return detail(approval.undo(agencyId, id));
    }

    public Detail revise(Long agencyId, Long id) {
        return detail(approval.revise(agencyId, id));
    }

    @Transactional(readOnly = true)
    public List<ProposalCommandRecord> commands(Long agencyId, Long id) {
        return approval.commands(agencyId, id);
    }

    public Detail approve(Long agencyId, Long id) {
        return detail(approval.approve(agencyId, id));
    }

    public Detail send(Long agencyId, Long id) {
        return detail(approval.send(agencyId, id));
    }

    public Detail negotiate(Long agencyId, Long id) {
        return detail(approval.negotiate(agencyId, id));
    }

    public Detail reject(Long agencyId, Long id) {
        return detail(approval.reject(agencyId, id));
    }

    public Detail expire(Long agencyId, Long id) {
        return detail(approval.expire(agencyId, id));
    }

    private Detail detail(Proposal proposal) {
        proposal.getItems().forEach(item -> {
            Hibernate.initialize(item.getRequirement());
            Hibernate.initialize(item.getRequirement().getRequirementType());
        });
        // The getters return unmodifiable views, on which Hibernate.initialize() is a no-op; size() reaches the
        // underlying persistent collection and loads it inside this transaction.
        proposal.getAppliedExtras().size();
        proposal.getStatusHistory().size();
        if (proposal.getSchedulePlan() != null) {
            proposal.getSchedulePlan().getPhases().size();
        }
        Payment deposit = payments.findDeposit(proposal.getId()).orElse(null);
        if (deposit != null) {
            // The response mapper derives projectActive from the deposit's
            // contract status; initialize that lazy association here, inside
            // the transaction, because open-in-view is disabled.
            Hibernate.initialize(deposit.getContract());
        }
        return new Detail(proposal, documents.list(proposal.getId()), deposit);
    }

    /** Loaded associations needed by the proposal detail projection. */
    public record Detail(Proposal proposal, List<ProposalDocument> documents, Payment deposit) {
    }
}
