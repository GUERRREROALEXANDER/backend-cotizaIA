package com.cotizaia.service;

import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ProposalRepository.SentTiming;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes agency metrics from persisted proposal history (project.txt section 2).
 * First-send timestamps and distinct lifecycle counts keep retries and later states from distorting the dashboard.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final ProposalRepository proposals;

    public AnalyticsService(ProposalRepository proposals) {
        this.proposals = proposals;
    }

    public Summary get(Long agencyId) {
        Map<ProposalStatus, Long> counts = new EnumMap<>(ProposalStatus.class);
        for (ProposalStatus status : ProposalStatus.values()) {
            counts.put(status, 0L);
        }
        proposals.countStatuses(agencyId).forEach(row -> counts.put(row.getStatus(), row.getCount()));
        List<SentTiming> timings = proposals.firstSentTimes(agencyId, ProposalStatus.SENT);
        long sent = timings.size();
        long accepted = proposals.countReached(agencyId, ProposalStatus.ACCEPTED);
        BigDecimal rate = sent == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(accepted).divide(BigDecimal.valueOf(sent), 4, RoundingMode.HALF_UP);
        BigDecimal average = null;
        if (sent != 0) {
            BigDecimal milliseconds = timings.stream()
                    .map(row -> BigDecimal.valueOf(Duration.between(row.getReceivedAt(), row.getSentAt()).toMillis()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            average = milliseconds.divide(BigDecimal.valueOf(sent).multiply(BigDecimal.valueOf(60000)),
                    4, RoundingMode.HALF_UP);
        }
        return new Summary(average, rate, Map.copyOf(counts), sent, accepted,
                counts.values().stream().mapToLong(Long::longValue).sum());
    }

    /** Calculated dashboard values with null response time when nothing has been sent. */
    public record Summary(BigDecimal averageResponseTimeMinutes, BigDecimal acceptanceRate,
            Map<ProposalStatus, Long> proposalsByStatus, long sentCount, long acceptedCount, long totalProposals) {
    }
}
