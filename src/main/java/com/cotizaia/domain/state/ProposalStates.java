package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;
import java.util.EnumMap;

/**
 * Registry of stateless shared ConcreteStates (project.txt section 6 pattern 9: State).
 * Sharing instances keeps transition rules independent of each Proposal's persistence lifecycle.
 */
public final class ProposalStates {
    private static final EnumMap<ProposalStatus, ProposalState> STATES = new EnumMap<>(ProposalStatus.class);

    static {
        register(new ReceivedState());
        register(new AnalyzingState());
        register(new QuotedState());
        register(new InReviewState());
        register(new SentState());
        register(new NegotiatingState());
        register(new AcceptedState());
        register(new ContractIssuedState());
        register(new RejectedState());
        register(new ExpiredState());
    }

    private ProposalStates() {
    }

    private static void register(ProposalState state) {
        STATES.put(state.status(), state);
    }

    public static ProposalState of(ProposalStatus status) {
        ProposalState state = STATES.get(status);
        if (state == null) {
            throw new IllegalArgumentException("Unknown proposal status: " + status);
        }
        return state;
    }
}
