package com.cotizaia.domain;

/**
 * Lifecycle of a {@link Proposal} (project.txt section 6 pattern 9: State;
 * section 2 "Hacer seguimiento del estado"). Stored in
 * {@code proposals.status} and constrained by {@code ck_proposals_status}.
 *
 * <p>Each state owns the set of states it may move to, so an illegal jump
 * (e.g. {@code RECEIVED -> CONTRACT_ISSUED}) is rejected by the domain instead
 * of relying on callers or the API. Adding a state is an additive change: one
 * constant here plus the new {@code CHECK} value in the migration.
 */
public enum ProposalStatus {
    RECEIVED,
    ANALYZING,
    QUOTED,
    IN_REVIEW,
    SENT,
    NEGOTIATING,
    ACCEPTED,
    CONTRACT_ISSUED,
    REJECTED,
    EXPIRED;

    /**
     * The transitions allowed by the project's proposal diagram. Terminal states
     * accept nothing further; {@code EXPIRED} is reachable from every open state.
     */
    public boolean canTransitionTo(ProposalStatus next) {
        return switch (this) {
            case RECEIVED -> next == ANALYZING || next == EXPIRED;
            case ANALYZING -> next == QUOTED || next == EXPIRED;
            case QUOTED -> next == IN_REVIEW || next == EXPIRED;
            case IN_REVIEW -> next == SENT || next == REJECTED || next == EXPIRED;
            case SENT -> next == NEGOTIATING || next == ACCEPTED || next == REJECTED || next == EXPIRED;
            case NEGOTIATING -> next == ACCEPTED || next == REJECTED || next == EXPIRED;
            case ACCEPTED -> next == CONTRACT_ISSUED;
            case CONTRACT_ISSUED, REJECTED, EXPIRED -> false;
        };
    }
}
