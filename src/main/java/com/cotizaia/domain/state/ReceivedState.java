package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** A new brief has been received and awaits analysis (project.txt section 6 pattern 9: State). */
final class ReceivedState extends AbstractProposalState {
    ReceivedState() {
        super(ProposalStatus.RECEIVED);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case START_ANALYSIS -> event.target();
            case EXPIRE -> event.target();
            default -> null;
        };
    }
}
