package com.cotizaia.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.ProposalStatus;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProposalEventTests {

    @ParameterizedTest
    @EnumSource(value = ProposalStatus.class, names = "RECEIVED", mode = EnumSource.Mode.EXCLUDE)
    void everyStatusButReceivedIsReachedByExactlyOneEvent(ProposalStatus target) {
        assertThat(ProposalEvent.leadingTo(target).target()).isEqualTo(target);
        assertThat(Arrays.stream(ProposalEvent.values()).filter(event -> event.target() == target)).hasSize(1);
    }

    @Test
    void nothingLeadsBackToReceived() {
        assertThatThrownBy(() -> ProposalEvent.leadingTo(ProposalStatus.RECEIVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RECEIVED");
    }

    @Test
    void eventNamesMatchTheProposalEndpoints() {
        assertThat(ProposalEvent.SEND.target()).isEqualTo(ProposalStatus.SENT);
        assertThat(ProposalEvent.NEGOTIATE.target()).isEqualTo(ProposalStatus.NEGOTIATING);
        assertThat(ProposalEvent.REJECT.target()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(ProposalEvent.EXPIRE.target()).isEqualTo(ProposalStatus.EXPIRED);
        assertThat(ProposalEvent.ACCEPT.target()).isEqualTo(ProposalStatus.ACCEPTED);
    }
}
