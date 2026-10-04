package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The offer was rejected and its lifecycle is closed (project.txt section 6 pattern 9: State). */
final class RejectedState extends AbstractProposalState {
    RejectedState() {
        super(ProposalStatus.REJECTED);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        // terminal: accepts no event
        return null;
    }
}
