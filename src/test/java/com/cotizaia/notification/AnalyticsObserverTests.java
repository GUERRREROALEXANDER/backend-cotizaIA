package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalyticsObserverTests {

    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final ProposalRepository proposals = mock(ProposalRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AnalyticsObserver observer = new AnalyticsObserver(agencies, proposals, notifications);

    @Test
    void usesTheAnalyticsChannel() {
        assertThat(observer.channel()).isEqualTo("ANALYTICS");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsTheTransitionWithItsTimeAndTotal() {
        Agency agency = mock(Agency.class);
        Proposal proposal = mock(Proposal.class);
        when(agencies.getReferenceById(2L)).thenReturn(agency);
        when(proposals.getReferenceById(7L)).thenReturn(proposal);
        Instant changedAt = Instant.parse("2026-11-20T10:00:00Z");

        observer.onStateChanged(new ProposalStateChangedEvent(7L, 2L, 5L, "Cliente", ProposalStatus.SENT,
                ProposalStatus.ACCEPTED, changedAt, new BigDecimal("950000.00")));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notifications).record(eq(agency), eq(proposal), eq("ANALYTICS"), eq("PROPOSAL_STATUS_CHANGED"),
                payload.capture());
        assertThat(payload.getValue())
                .containsEntry("proposalId", 7L)
                .containsEntry("from", ProposalStatus.SENT)
                .containsEntry("to", ProposalStatus.ACCEPTED)
                .containsEntry("changedAt", changedAt)
                .containsEntry("total", new BigDecimal("950000.00"))
                .doesNotContainKey("clientName");
    }
}
