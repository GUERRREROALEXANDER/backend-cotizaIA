package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The quote awaits human approval and can still be edited (project.txt section 6 pattern 9: State). */
final class InReviewState extends AbstractProposalState {
    InReviewState() {
        super(ProposalStatus.IN_REVIEW);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case SEND -> event.target();
            case REJECT -> event.target();
            case EXPIRE -> event.target();
            default -> null;
        };
    }

    @Override
    public boolean allowsQuoteEdits() {
        return true;
    }
}
