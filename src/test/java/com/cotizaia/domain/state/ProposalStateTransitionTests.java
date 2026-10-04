package com.cotizaia.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.ProposalStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Full lifecycle contract for the State roles (project.txt section 6 pattern 9). */
class ProposalStateTransitionTests {
    private static final EnumMap<ProposalStatus, Set<ProposalStatus>> EXPECTED = new EnumMap<>(ProposalStatus.class);

    static {
        EXPECTED.put(ProposalStatus.RECEIVED, EnumSet.of(ProposalStatus.ANALYZING, ProposalStatus.EXPIRED));
        EXPECTED.put(ProposalStatus.ANALYZING, EnumSet.of(ProposalStatus.QUOTED, ProposalStatus.EXPIRED));
        EXPECTED.put(ProposalStatus.QUOTED, EnumSet.of(ProposalStatus.IN_REVIEW, ProposalStatus.EXPIRED));
        EXPECTED.put(ProposalStatus.IN_REVIEW, EnumSet.of(ProposalStatus.SENT,
                ProposalStatus.REJECTED, ProposalStatus.EXPIRED));
        EXPECTED.put(ProposalStatus.SENT, EnumSet.of(ProposalStatus.NEGOTIATING, ProposalStatus.ACCEPTED,
                ProposalStatus.REJECTED, ProposalStatus.EXPIRED));
        EXPECTED.put(ProposalStatus.NEGOTIATING, EnumSet.of(ProposalStatus.ACCEPTED,
                ProposalStatus.REJECTED, ProposalStatus.EXPIRED));
        EXPECTED.put(ProposalStatus.ACCEPTED, EnumSet.of(ProposalStatus.CONTRACT_ISSUED));
        EXPECTED.put(ProposalStatus.CONTRACT_ISSUED, EnumSet.noneOf(ProposalStatus.class));
        EXPECTED.put(ProposalStatus.REJECTED, EnumSet.noneOf(ProposalStatus.class));
        EXPECTED.put(ProposalStatus.EXPIRED, EnumSet.noneOf(ProposalStatus.class));
    }

    static Stream<Arguments> matrix() {
        return Stream.of(ProposalStatus.values()).flatMap(from ->
                Stream.of(ProposalStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("matrix")
    void checksEveryPair(ProposalStatus from, ProposalStatus to) {
        assertThat(ProposalStates.of(from).canTransitionTo(to)).isEqualTo(EXPECTED.get(from).contains(to));
        assertThat(from.canTransitionTo(to)).isEqualTo(EXPECTED.get(from).contains(to));
    }

    @Test
    void illegalMovesDescribeTheAllowedTargets() {
        assertIllegal(ProposalStatus.ANALYZING, ProposalStatus.CONTRACT_ISSUED,
                "Illegal proposal transition: ANALYZING -> CONTRACT_ISSUED (allowed from ANALYZING: [QUOTED, EXPIRED])");
        assertIllegal(ProposalStatus.RECEIVED, ProposalStatus.SENT,
                "Illegal proposal transition: RECEIVED -> SENT (allowed from RECEIVED: [ANALYZING, EXPIRED])");
        assertIllegal(ProposalStatus.REJECTED, ProposalStatus.ACCEPTED,
                "Illegal proposal transition: REJECTED -> ACCEPTED (allowed from REJECTED: [])");
        assertIllegal(ProposalStatus.CONTRACT_ISSUED, ProposalStatus.EXPIRED,
                "Illegal proposal transition: CONTRACT_ISSUED -> EXPIRED (allowed from CONTRACT_ISSUED: [])");
    }

    private static void assertIllegal(ProposalStatus from, ProposalStatus to, String message) {
        assertThatThrownBy(() -> ProposalStates.of(from).handle(ProposalEvent.leadingTo(to)))
                .isInstanceOf(InvalidStateTransitionException.class).hasMessage(message);
    }

    @Test
    void quoteEditsAndTerminalStatesMatchLifecycle() {
        for (ProposalStatus status : ProposalStatus.values()) {
            ProposalState state = ProposalStates.of(status);
            assertThat(state.allowsQuoteEdits()).isEqualTo(EnumSet.of(ProposalStatus.QUOTED,
                    ProposalStatus.IN_REVIEW, ProposalStatus.NEGOTIATING).contains(status));
            assertThat(state.isTerminal()).isEqualTo(EnumSet.of(ProposalStatus.CONTRACT_ISSUED,
                    ProposalStatus.REJECTED, ProposalStatus.EXPIRED).contains(status));
            assertThat(state.allowedTransitions()).containsExactlyInAnyOrderElementsOf(EXPECTED.get(status));
        }
    }
}
