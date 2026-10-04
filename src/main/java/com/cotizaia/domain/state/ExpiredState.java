package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The offer's validity has ended without completion (project.txt section 6 pattern 9: State). */
final class ExpiredState extends AbstractProposalState {
    ExpiredState() {
        super(ProposalStatus.EXPIRED);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        // terminal: accepts no event
        return null;
    }
}
