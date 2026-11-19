package com.cotizaia.service;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalCommandRecord;
import com.cotizaia.domain.ProposalExtra;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.command.AddExtraCommand;
import com.cotizaia.service.command.AdjustHoursCommand;
import com.cotizaia.service.command.CommandHistory;
import com.cotizaia.service.command.RemoveExtraCommand;
import com.cotizaia.domain.BaseProposalPrice;
import com.cotizaia.domain.DiscountDecorator;
import com.cotizaia.domain.ExtendedWarrantyDecorator;
import com.cotizaia.domain.PricedProposal;
import com.cotizaia.domain.ProposalPriceDecorator;
import com.cotizaia.domain.UrgencyDecorator;
import com.cotizaia.config.CurrentActor;
import com.cotizaia.mail.ProposalDocumentEmailEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.context.ApplicationEventPublisher;
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

    /** Flat fee applied by the EXTENDED_WARRANTY extra, the only fixed-price decorator. */
    static final BigDecimal WARRANTY_FEE = new BigDecimal("500000.00");

    private final ProposalRepository proposals;

    private final DocumentService documents;

    private final ProposalWorkflowService workflow;

    private final ProposalAcceptanceService acceptance;

    private final CommandHistory commandHistory;

    private final ApplicationEventPublisher events;

    private final CurrentActor actor;

    public ApprovalQueueService(ProposalRepository proposals, DocumentService documents,
            ProposalWorkflowService workflow, ProposalAcceptanceService acceptance,
            CommandHistory commandHistory, ApplicationEventPublisher events, CurrentActor actor) {
        this.proposals = proposals;
        this.documents = documents;
        this.workflow = workflow;
        this.acceptance = acceptance;
        this.commandHistory = commandHistory;
        this.events = events;
        this.actor = actor;
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
        commandHistory.run(proposal,
                new AdjustHoursCommand(item.getRequirement().getId(), item.getHours(), hours));
        return proposals.saveAndFlush(proposal);
    }

    /**
     * Applies one price extra through the real Decorator chain and records it as
     * a reversible command. The chain starts at the item-derived subtotal and is
     * rebuilt over the already-applied extras (in application order), so the new
     * wrapper compounds exactly like the decorators do in the domain and its
     * signed delta is measured against the running total.
     */
    public Proposal addExtra(Long agencyId, Long proposalId, String type) {
        Proposal proposal = get(agencyId, proposalId);
        requireExtrasEditable(proposal);
        if (proposal.getAppliedExtras().stream().anyMatch(extra -> extra.getType().equals(type))) {
            throw new IllegalStateException("Extra already applied: " + type);
        }
        PricedProposal chain = new BaseProposalPrice(proposal);
        for (ProposalExtra applied : proposal.getAppliedExtras()) {
            chain = decoratorFor(applied.getType(), chain, applied.getPercentOrFixed());
        }
        ProposalPriceDecorator extra = decoratorFor(type, chain,
                ExtendedWarrantyDecorator.TYPE.equals(type) ? WARRANTY_FEE : BigDecimal.ZERO);
        commandHistory.run(proposal, new AddExtraCommand(extra.extraType(), extra.value(), extra.amount()));
        documents.generateFor(proposalId, null);
        return proposals.saveAndFlush(proposal);
    }

    /**
     * Drops one applied extra and recomputes the total from the remaining
     * signed deltas, recording the removal as a reversible command.
     */
    public Proposal removeExtra(Long agencyId, Long proposalId, Long extraId) {
        Proposal proposal = get(agencyId, proposalId);
        requireExtrasEditable(proposal);
        ProposalExtra extra = proposal.getAppliedExtras().stream()
                .filter(candidate -> candidate.getId().equals(extraId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Extra not found: " + extraId));
        commandHistory.run(proposal, new RemoveExtraCommand(extra.getId(), extra.getType(),
                extra.getPercentOrFixed(), extra.getAmount()));
        documents.generateFor(proposalId, null);
        return proposals.saveAndFlush(proposal);
    }

    /** Extras touch the commercial offer only while it is still being reviewed and not yet approved. */
    private void requireExtrasEditable(Proposal proposal) {
        if (proposal.getStatus() != ProposalStatus.IN_REVIEW || proposal.isApproved()) {
            throw new IllegalStateException("Extras can only be changed while the proposal is in review and not approved");
        }
    }

    /** Builds the real decorator for one extra kind; a new kind needs no change here beyond a new case. */
    private ProposalPriceDecorator decoratorFor(String type, PricedProposal delegate, BigDecimal value) {
        return switch (type) {
            case UrgencyDecorator.TYPE -> new UrgencyDecorator(delegate);
            case DiscountDecorator.TYPE -> new DiscountDecorator(delegate);
            case ExtendedWarrantyDecorator.TYPE -> new ExtendedWarrantyDecorator(delegate, value);
            default -> throw new IllegalArgumentException("Unknown extra type: " + type);
        };
    }

    public Proposal undo(Long agencyId, Long proposalId) {
        Proposal proposal = get(agencyId, proposalId);
        if (!proposal.getState().allowsQuoteEdits()) {
            throw new InvalidStateTransitionException(proposal.getStatus(), null,
                    ProposalStatus.IN_REVIEW, proposal.getState().allowedTransitions());
        }
        commandHistory.undoLast(proposal);
        return proposals.saveAndFlush(proposal);
    }

    public List<ProposalCommandRecord> commands(Long agencyId, Long proposalId) {
        get(agencyId, proposalId);
        return commandHistory.list(proposalId);
    }

    /**
     * Turns a negotiated offer into its next revision: the copy goes back to
     * review and the original is expired through the normal lifecycle path, so
     * history and observers record the move while the client's version stays
     * frozen for audit.
     */
    public Proposal revise(Long agencyId, Long proposalId) {
        Proposal original = get(agencyId, proposalId);
        if (original.getStatus() != ProposalStatus.NEGOTIATING) {
            throw new InvalidStateTransitionException(original.getStatus(), null,
                    ProposalStatus.IN_REVIEW, original.getState().allowedTransitions());
        }
        Proposal revision = original.copy();
        proposals.saveAndFlush(revision);
        workflow.transition(proposalId, ProposalEvent.EXPIRE);
        documents.generateFor(revision.getId(), null);
        return revision;
    }

    public Proposal approve(Long agencyId, Long proposalId) {
        Proposal proposal = get(agencyId, proposalId);
        proposal.approve(Instant.now());
        documents.generateFor(proposalId, null);
        return proposal;
    }

    /**
     * Sends an approved proposal and asks for its PDF to be mailed to the person who sent it
     * (spec 16). The event is published inside this transaction and delivered to the Observer
     * only after the commit, so a mail failure can never undo the send.
     */
    public Proposal send(Long agencyId, Long proposalId) {
        Proposal proposal = get(agencyId, proposalId);
        if (!proposal.isApproved()) {
            throw new IllegalStateException("Proposal must be approved before sending");
        }
        Proposal sent = workflow.transition(proposalId, ProposalEvent.SEND);
        events.publishEvent(ProposalDocumentEmailEvent.sent(proposalId, actor.userId().orElse(null)));
        return sent;
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
