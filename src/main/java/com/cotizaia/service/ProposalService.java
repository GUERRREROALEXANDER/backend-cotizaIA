package com.cotizaia.service;

import com.cotizaia.domain.Payment;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.repository.ProposalRepository;
import java.math.BigDecimal;
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

    public Detail adjustHours(Long agencyId, Long id, Long itemId, BigDecimal hours) {
        return detail(approval.adjustHours(agencyId, id, itemId, hours));
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
        proposal.getItems().forEach(item -> Hibernate.initialize(item.getRequirement()));
        // The getters return unmodifiable views, on which Hibernate.initialize() is a no-op; size() reaches the
        // underlying persistent collection and loads it inside this transaction.
        proposal.getAppliedExtras().size();
        proposal.getStatusHistory().size();
        if (proposal.getSchedulePlan() != null) {
            proposal.getSchedulePlan().getPhases().size();
        }
        return new Detail(proposal, documents.list(proposal.getId()),
                payments.findDeposit(proposal.getId()).orElse(null));
    }

    /** Loaded associations needed by the proposal detail projection. */
    public record Detail(Proposal proposal, List<ProposalDocument> documents, Payment deposit) {
    }
}
