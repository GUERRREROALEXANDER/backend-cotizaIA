package com.cotizaia.service;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatusChange;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.notification.ProposalStateChangedEvent;
import com.cotizaia.notification.ProposalSubject;
import com.cotizaia.repository.ProposalRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application entry point for proposal moves (project.txt section 6: State and Observer).
 * It persists a legal transition before synchronously notifying every observer
 * within the same transaction, keeping status and notification rows atomic.
 */
@Service
public class ProposalWorkflowService {

    private final ProposalRepository proposals;
    private final ProposalSubject subject;

    public ProposalWorkflowService(ProposalRepository proposals, ProposalSubject subject) {
        this.proposals = proposals;
        this.subject = subject;
    }

    @Transactional
    public Proposal transition(Long proposalId, ProposalEvent event) {
        Proposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Proposal not found: " + proposalId));
        ProposalStatusChange change = proposal.apply(event);
        proposals.saveAndFlush(proposal);
        publish(proposal, change);
        return proposal;
    }

    public void publish(Proposal proposal, ProposalStatusChange change) {
        subject.notifyObservers(ProposalStateChangedEvent.from(proposal, change));
    }
}
