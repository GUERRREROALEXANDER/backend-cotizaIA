package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The quote has been sent to the client for a decision (project.txt section 6 pattern 9: State). */
final class SentState extends AbstractProposalState {
    SentState() {
        super(ProposalStatus.SENT);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case NEGOTIATE -> event.target();
            case ACCEPT -> event.target();
            case REJECT -> event.target();
            case EXPIRE -> event.target();
            default -> null;
        };
    }
}
