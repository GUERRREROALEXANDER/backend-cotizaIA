package com.cotizaia.service;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.repository.ProposalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns agency-scoped human review (project.txt sections 2, 4 and 5).
 * Approval regenerates the final documents from reviewed hours, and sending requires
 * that approval so a changed quotation cannot silently bypass its human reviewer.
 */
@Service
@Transactional
public class ApprovalQueueService {

    private final ProposalRepository proposals;

    private final DocumentService documents;

    private final ProposalWorkflowService workflow;

    private final ProposalAcceptanceService acceptance;

    public ApprovalQueueService(ProposalRepository proposals, DocumentService documents,
            ProposalWorkflowService workflow, ProposalAcceptanceService acceptance) {
        this.proposals = proposals;
        this.documents = documents;
        this.workflow = workflow;
        this.acceptance = acceptance;
    }

    @Transactional(readOnly = true)
    public List<Proposal> queue(Long agencyId) {
        return proposals.findByStatusAndBriefClientAgencyIdOrderByCreatedAtAsc(ProposalStatus.IN_REVIEW, agencyId);
    }

    @Transactional(readOnly = true)
    public Proposal get(Long agencyId, Long proposalId) {
        return proposals.findByIdAndBriefClientAgencyId(proposalId, agencyId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
    }

    public Proposal adjustHours(Long agencyId, Long proposalId, Long itemId, BigDecimal hours) {
        Proposal proposal = get(agencyId, proposalId);
        if (!proposal.getState().allowsQuoteEdits()) {
            throw new IllegalStateException("Quote hours cannot be edited in " + proposal.getStatus());
        }
        QuotedItem item = proposal.getItems().stream().filter(candidate -> candidate.getId().equals(itemId))
                .findFirst().orElseThrow(() -> new NoSuchElementException("Quoted item not found: " + itemId));
        proposal.editHours(item, hours);
        return proposals.saveAndFlush(proposal);
    }

    public Proposal approve(Long agencyId, Long proposalId) {
        Proposal proposal = get(agencyId, proposalId);
        proposal.approve(Instant.now());
        documents.generateFor(proposalId, null);
        return proposal;
    }

    public Proposal send(Long agencyId, Long proposalId) {
        Proposal proposal = get(agencyId, proposalId);
        if (!proposal.isApproved()) {
            throw new IllegalStateException("Proposal must be approved before sending");
        }
        return workflow.transition(proposalId, ProposalEvent.SEND);
    }

    public Proposal negotiate(Long agencyId, Long proposalId) {
        get(agencyId, proposalId);
        return workflow.transition(proposalId, ProposalEvent.NEGOTIATE);
    }

    public Proposal reject(Long agencyId, Long proposalId) {
        get(agencyId, proposalId);
        return workflow.transition(proposalId, ProposalEvent.REJECT);
    }

    public Proposal expire(Long agencyId, Long proposalId) {
        get(agencyId, proposalId);
        return workflow.transition(proposalId, ProposalEvent.EXPIRE);
    }

    public ProposalAcceptanceService.AcceptanceResult accept(Long agencyId, Long proposalId) {
        get(agencyId, proposalId);
        return acceptance.accept(proposalId);
    }
}
