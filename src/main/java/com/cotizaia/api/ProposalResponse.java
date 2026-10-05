package com.cotizaia.api;

import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.pricing.PricingModel;
import java.math.BigDecimal;
import java.time.Instant;

/** Summary for agency lists and the human approval queue. */
public record ProposalResponse(Long id, Long briefId, ProposalStatus status, BigDecimal subtotal,
        BigDecimal total, PricingModel pricingModel, Instant createdAt, Instant approvedAt) {

    public static ProposalResponse from(Proposal proposal) {
        return new ProposalResponse(proposal.getId(), proposal.getBrief().getId(), proposal.getStatus(),
                proposal.getSubtotal(), proposal.getTotal(), proposal.getPricingModel(),
                proposal.getCreatedAt(), proposal.getApprovedAt());
    }
}
