package com.cotizaia.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.ProposalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProposalStatesRegistryTests {

    @ParameterizedTest
    @EnumSource(ProposalStatus.class)
    void everyStatusHasOneSharedStateObject(ProposalStatus status) {
        ProposalState state = ProposalStates.of(status);

        assertThat(state.status()).isEqualTo(status);
        assertThat(ProposalStates.of(status)).isSameAs(state);
    }

    @Test
    void anUnknownStatusIsRejected() {
        assertThatThrownBy(() -> ProposalStates.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingEventIsAnIllegalTransitionThatNamesTheAllowedMoves() {
        ProposalState inReview = ProposalStates.of(ProposalStatus.IN_REVIEW);

        assertThatThrownBy(() -> inReview.handle(null))
                .isInstanceOfSatisfying(InvalidStateTransitionException.class, error -> {
                    assertThat(error.getFrom()).isEqualTo(ProposalStatus.IN_REVIEW);
                    assertThat(error.getEvent()).isNull();
                    assertThat(error.getTo()).isNull();
                })
                .hasMessageContaining("SENT");
    }

    @Test
    void handlingAnEventReturnsTheRegisteredTargetState() {
        ProposalState sent = ProposalStates.of(ProposalStatus.IN_REVIEW).handle(ProposalEvent.SEND);

        assertThat(sent).isSameAs(ProposalStates.of(ProposalStatus.SENT));
    }

    @Test
    void allowedTransitionsCannotBeModified() {
        assertThatThrownBy(() -> ProposalStates.of(ProposalStatus.SENT).allowedTransitions()
                .add(ProposalStatus.RECEIVED)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void noStateCanGoBackToReceived() {
        for (ProposalStatus status : ProposalStatus.values()) {
            assertThat(ProposalStates.of(status).canTransitionTo(ProposalStatus.RECEIVED)).isFalse();
            assertThat(ProposalStates.of(status).canTransitionTo(null)).isFalse();
        }
    }
}
