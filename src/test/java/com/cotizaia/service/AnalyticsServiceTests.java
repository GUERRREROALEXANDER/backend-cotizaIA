package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ProposalRepository.SentTiming;
import com.cotizaia.repository.ProposalRepository.StatusCount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTests {

    private static final Instant RECEIVED = Instant.parse("2026-11-20T10:00:00Z");

    private final ProposalRepository proposals = mock(ProposalRepository.class);
    private final AnalyticsService service = new AnalyticsService(proposals);

    private static StatusCount count(ProposalStatus status, long count) {
        StatusCount row = mock(StatusCount.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getCount()).thenReturn(count);
        return row;
    }

    private static SentTiming sentAfterMinutes(long minutes) {
        SentTiming row = mock(SentTiming.class);
        when(row.getReceivedAt()).thenReturn(RECEIVED);
        when(row.getSentAt()).thenReturn(RECEIVED.plusSeconds(minutes * 60));
        return row;
    }

    @Test
    void anAgencyWithoutProposalsHasZerosAndNoAverage() {
        AnalyticsService.Summary summary = service.get(1L);

        assertThat(summary.totalProposals()).isZero();
        assertThat(summary.acceptanceRate()).isEqualByComparingTo("0");
        assertThat(summary.averageResponseTimeMinutes()).isNull();
        assertThat(summary.proposalsByStatus()).hasSize(ProposalStatus.values().length)
                .allSatisfy((status, value) -> assertThat(value).isZero());
    }

    @Test
    void acceptanceRateIsAcceptedOverSent() {
        List<SentTiming> timings = List.of(sentAfterMinutes(10), sentAfterMinutes(20), sentAfterMinutes(30));
        when(proposals.firstSentTimes(1L, ProposalStatus.SENT)).thenReturn(timings);
        when(proposals.countReached(1L, ProposalStatus.ACCEPTED)).thenReturn(1L);

        AnalyticsService.Summary summary = service.get(1L);

        assertThat(summary.sentCount()).isEqualTo(3);
        assertThat(summary.acceptedCount()).isEqualTo(1);
        assertThat(summary.acceptanceRate()).isEqualByComparingTo("0.3333");
    }

    @Test
    void averageResponseTimeIsInMinutes() {
        List<SentTiming> timings = List.of(sentAfterMinutes(10), sentAfterMinutes(20));
        when(proposals.firstSentTimes(1L, ProposalStatus.SENT)).thenReturn(timings);

        assertThat(service.get(1L).averageResponseTimeMinutes()).isEqualByComparingTo("15");
    }

    @Test
    void statusCountsAddUpToTheTotal() {
        List<StatusCount> rows = List.of(count(ProposalStatus.IN_REVIEW, 2), count(ProposalStatus.SENT, 3));
        when(proposals.countStatuses(1L)).thenReturn(rows);

        AnalyticsService.Summary summary = service.get(1L);

        assertThat(summary.proposalsByStatus()).containsEntry(ProposalStatus.IN_REVIEW, 2L)
                .containsEntry(ProposalStatus.SENT, 3L).containsEntry(ProposalStatus.ACCEPTED, 0L);
        assertThat(summary.totalProposals()).isEqualTo(5);
    }
}
