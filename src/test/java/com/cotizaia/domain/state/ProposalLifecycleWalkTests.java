package com.cotizaia.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.ProposalStatus;
import org.junit.jupiter.api.Test;

class ProposalLifecycleWalkTests {

    @Test
    void theHappyPathEndsWithAContract() {
        ProposalState state = ProposalStates.of(ProposalStatus.RECEIVED);
        ProposalEvent[] path = {ProposalEvent.START_ANALYSIS, ProposalEvent.QUOTE, ProposalEvent.SUBMIT_FOR_REVIEW,
                ProposalEvent.SEND, ProposalEvent.NEGOTIATE, ProposalEvent.ACCEPT, ProposalEvent.ISSUE_CONTRACT};

        for (ProposalEvent event : path) {
            state = state.handle(event);
            assertThat(state.status()).isEqualTo(event.target());
        }

        assertThat(state.isTerminal()).isTrue();
    }

    @Test
    void aSentProposalCanBeAcceptedWithoutNegotiating() {
        ProposalState sent = ProposalStates.of(ProposalStatus.SENT);

        assertThat(sent.handle(ProposalEvent.ACCEPT).status()).isEqualTo(ProposalStatus.ACCEPTED);
    }

    @Test
    void theQuoteCanOnlyBeEditedBeforeItIsAccepted() {
        assertThat(ProposalStates.of(ProposalStatus.QUOTED).allowsQuoteEdits()).isTrue();
        assertThat(ProposalStates.of(ProposalStatus.IN_REVIEW).allowsQuoteEdits()).isTrue();
        assertThat(ProposalStates.of(ProposalStatus.NEGOTIATING).allowsQuoteEdits()).isTrue();
        assertThat(ProposalStates.of(ProposalStatus.SENT).allowsQuoteEdits()).isFalse();
        assertThat(ProposalStates.of(ProposalStatus.ACCEPTED).allowsQuoteEdits()).isFalse();
    }

    @Test
    void aReviewCannotBeSkipped() {
        ProposalState quoted = ProposalStates.of(ProposalStatus.QUOTED);

        assertThat(quoted.canTransitionTo(ProposalStatus.SENT)).isFalse();
        assertThatThrownBy(() -> quoted.handle(ProposalEvent.SEND))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void rejectedAndExpiredProposalsAreFinal() {
        assertThat(ProposalStates.of(ProposalStatus.REJECTED).isTerminal()).isTrue();
        assertThat(ProposalStates.of(ProposalStatus.EXPIRED).isTerminal()).isTrue();
        assertThat(ProposalStates.of(ProposalStatus.REJECTED).allowedTransitions()).isEmpty();
    }
}
