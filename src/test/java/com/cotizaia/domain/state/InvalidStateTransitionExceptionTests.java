package com.cotizaia.domain.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.ProposalStatus;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class InvalidStateTransitionExceptionTests {

    @Test
    void theMessageNamesBothStatusesAndTheAllowedOnes() {
        InvalidStateTransitionException exception = new InvalidStateTransitionException(ProposalStatus.QUOTED,
                ProposalEvent.SEND, ProposalStatus.SENT,
                EnumSet.of(ProposalStatus.IN_REVIEW, ProposalStatus.EXPIRED));

        assertThat(exception.getMessage()).contains("QUOTED -> SENT").contains("IN_REVIEW").contains("EXPIRED");
    }

    @Test
    void keepsTheDetailsForTheApiError() {
        InvalidStateTransitionException exception = new InvalidStateTransitionException(ProposalStatus.QUOTED,
                ProposalEvent.SEND, ProposalStatus.SENT, EnumSet.noneOf(ProposalStatus.class));

        assertThat(exception.getFrom()).isEqualTo(ProposalStatus.QUOTED);
        assertThat(exception.getEvent()).isEqualTo(ProposalEvent.SEND);
        assertThat(exception.getTo()).isEqualTo(ProposalStatus.SENT);
    }

    @Test
    void isAnIllegalStateSoExistingHandlersCatchIt() {
        assertThat(new InvalidStateTransitionException(ProposalStatus.SENT, null, null, EnumSet.noneOf(
                ProposalStatus.class))).isInstanceOf(IllegalStateException.class);
    }
}
