package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.UserRole;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OwnerEmailObserverTests {

    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ProposalRepository proposals = mock(ProposalRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OwnerEmailObserver observer = new OwnerEmailObserver(agencies, users, proposals, notifications);

    private final Agency agency = mock(Agency.class);
    private final Proposal proposal = mock(Proposal.class);

    private static final ProposalStateChangedEvent EVENT = new ProposalStateChangedEvent(7L, 2L, 5L, "Cliente",
            ProposalStatus.SENT, ProposalStatus.NEGOTIATING, Instant.parse("2026-11-20T10:00:00Z"), BigDecimal.TEN);

    @BeforeEach
    void references() {
        when(agencies.getReferenceById(2L)).thenReturn(agency);
        when(proposals.getReferenceById(7L)).thenReturn(proposal);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> recordedPayload() {
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notifications).record(eq(agency), eq(proposal), eq("EMAIL"), eq("PROPOSAL_STATUS_CHANGED"),
                payload.capture());
        return payload.getValue();
    }

    @Test
    void usesTheEmailChannel() {
        assertThat(observer.channel()).isEqualTo("EMAIL");
    }

    @Test
    void theOwnerIsTheRecipientAndTheEmailIsMarkedSimulated() {
        AppUser owner = mock(AppUser.class);
        when(owner.getEmail()).thenReturn("owner@demo.co");
        when(users.findByAgencyIdAndRole(2L, UserRole.OWNER)).thenReturn(Optional.of(owner));

        observer.onStateChanged(EVENT);

        Map<String, Object> payload = recordedPayload();
        assertThat(payload).containsEntry("to", "owner@demo.co").containsEntry("simulated", true);
        assertThat((String) payload.get("subject")).contains("7");
        assertThat((String) payload.get("body")).contains("SENT").contains("NEGOTIATING");
    }

    @Test
    void anAgencyWithoutOwnerStillGetsTheRecordWithoutRecipient() {
        when(users.findByAgencyIdAndRole(2L, UserRole.OWNER)).thenReturn(Optional.empty());

        observer.onStateChanged(EVENT);

        assertThat(recordedPayload()).containsEntry("to", null);
    }
}
