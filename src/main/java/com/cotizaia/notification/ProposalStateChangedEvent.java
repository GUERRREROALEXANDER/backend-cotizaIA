package com.cotizaia.notification;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.ProposalStatusChange;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable snapshot passed to lifecycle observers (project.txt section 6: Observer).
 * It carries the agency and client context so observers need no domain mutation.
 */
public record ProposalStateChangedEvent(Long proposalId, Long agencyId, Long briefId, String clientName,
        ProposalStatus from, ProposalStatus to, Instant changedAt, BigDecimal total) {

    public static ProposalStateChangedEvent from(Proposal proposal, ProposalStatusChange change) {
        if (proposal == null || change == null || change.getProposal() != proposal) {
            throw new IllegalArgumentException("matching proposal and change are required");
        }
        return new ProposalStateChangedEvent(proposal.getId(),
                proposal.getBrief().getClient().getAgency().getId(), proposal.getBrief().getId(),
                proposal.getBrief().getClient().getName(), change.getFromStatus(), change.getToStatus(),
                change.getChangedAt(), proposal.getTotal());
    }
}
