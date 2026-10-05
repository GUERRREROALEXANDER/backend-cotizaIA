package com.cotizaia.api;

import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.service.AnalyticsService.Summary;
import java.math.BigDecimal;
import java.util.Map;

/** Dashboard metrics derived from agency proposal rows. */
public record AnalyticsResponse(BigDecimal averageResponseTimeMinutes, BigDecimal acceptanceRate,
        Map<ProposalStatus, Long> proposalsByStatus, long sentCount, long acceptedCount, long totalProposals) {

    public static AnalyticsResponse from(Summary summary) {
        return new AnalyticsResponse(summary.averageResponseTimeMinutes(), summary.acceptanceRate(),
                summary.proposalsByStatus(), summary.sentCount(), summary.acceptedCount(), summary.totalProposals());
    }
}
