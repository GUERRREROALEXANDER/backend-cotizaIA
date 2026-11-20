package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
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

class DashboardObserverTests {

    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final ProposalRepository proposals = mock(ProposalRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final DashboardObserver observer = new DashboardObserver(agencies, proposals, notifications);

    private final Agency agency = mock(Agency.class);
    private final Proposal proposal = mock(Proposal.class);

    private static final ProposalStateChangedEvent EVENT = new ProposalStateChangedEvent(7L, 2L, 5L, "Panadería La Espiga",
            ProposalStatus.IN_REVIEW, ProposalStatus.SENT, Instant.parse("2026-11-20T10:00:00Z"), new BigDecimal("1200000"));

    @Test
    void usesTheDashboardChannel() {
        assertThat(observer.channel()).isEqualTo("DASHBOARD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsAStatusChangeTheDashboardCanShow() {
        when(agencies.getReferenceById(2L)).thenReturn(agency);
        when(proposals.getReferenceById(7L)).thenReturn(proposal);

        observer.onStateChanged(EVENT);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notifications).record(eq(agency), eq(proposal), eq("DASHBOARD"), eq("PROPOSAL_STATUS_CHANGED"),
                payload.capture());
        assertThat(payload.getValue())
                .containsEntry("proposalId", 7L)
                .containsEntry("briefId", 5L)
                .containsEntry("clientName", "Panadería La Espiga")
                .containsEntry("from", ProposalStatus.IN_REVIEW)
                .containsEntry("to", ProposalStatus.SENT);
        assertThat((String) payload.getValue().get("message")).contains("Panadería La Espiga").contains("SENT");
    }

    @Test
    void neverLoadsTheWholeAgencyOrProposal() {
        when(agencies.getReferenceById(2L)).thenReturn(agency);
        when(proposals.getReferenceById(7L)).thenReturn(proposal);

        observer.onStateChanged(EVENT);

        verify(agencies).getReferenceById(2L);
        verify(proposals).getReferenceById(7L);
        verify(notifications).record(eq(agency), eq(proposal), eq("DASHBOARD"), eq("PROPOSAL_STATUS_CHANGED"), anyMap());
    }
}
