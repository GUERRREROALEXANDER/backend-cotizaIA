package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationTests {

    private static final Instant SENT = Instant.parse("2026-11-20T10:00:00Z");

    private final Agency agency = mock(Agency.class);

    private Proposal proposalOfAgency(long agencyId) {
        Proposal proposal = mock(Proposal.class, RETURNS_DEEP_STUBS);
        when(proposal.getBrief().getClient().getAgency().getId()).thenReturn(agencyId);
        return proposal;
    }

    @Test
    void aDashboardNotificationKeepsItsChannelAndPayload() {
        when(agency.getId()).thenReturn(2L);
        Proposal proposal = proposalOfAgency(2L);

        Notification notification = new Notification(agency, proposal, "DASHBOARD", "PROPOSAL_STATUS_CHANGED",
                "{\"to\":\"SENT\"}", SENT);

        assertThat(notification.getChannel()).isEqualTo("DASHBOARD");
        assertThat(notification.getPayload()).isEqualTo("{\"to\":\"SENT\"}");
        assertThat(notification.getProposal()).isSameAs(proposal);
    }

    @Test
    void aNotificationCanExistWithoutAProposal() {
        Notification notification = new Notification(agency, null, "EMAIL", "VERIFY_EMAIL", "{}", SENT);

        assertThat(notification.getProposal()).isNull();
    }

    @Test
    void aProposalOfAnotherAgencyIsRefused() {
        when(agency.getId()).thenReturn(2L);
        Proposal foreign = proposalOfAgency(3L);

        assertThatThrownBy(() -> new Notification(agency, foreign, "DASHBOARD", "X", "{}", SENT))
                .hasMessageContaining("belong to agency");
    }

    @Test
    void channelAndEventTypeHaveLengthLimits() {
        assertThatThrownBy(() -> new Notification(agency, null, "C".repeat(41), "X", "{}", SENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification(agency, null, "EMAIL", "E".repeat(61), "{}", SENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Notification(agency, null, "C".repeat(40), "E".repeat(60), "{}", SENT).getChannel())
                .hasSize(40);
    }

    @Test
    void agencyPayloadAndTimeAreRequired() {
        assertThatThrownBy(() -> new Notification(null, null, "EMAIL", "X", "{}", SENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification(agency, null, "EMAIL", "X", " ", SENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Notification(agency, null, "EMAIL", "X", "{}", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
