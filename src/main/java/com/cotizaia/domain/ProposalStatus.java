package com.cotizaia.domain;

import com.cotizaia.domain.state.ProposalStates;

/**
 * Lifecycle of a {@link Proposal} (project.txt section 6 pattern 9: State;
 * section 2 "Hacer seguimiento del estado"). Stored in
 * {@code proposals.status} and constrained by {@code ck_proposals_status}.
 *
 * <p>Design pattern - <b>State</b>: this persisted value delegates lifecycle
 * decisions to the corresponding concrete state in {@code domain.state}.
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
     * Delegates to the State objects as the single source of transition rules.
     */
    public boolean canTransitionTo(ProposalStatus next) {
        return ProposalStates.of(this).canTransitionTo(next);
    }
}
