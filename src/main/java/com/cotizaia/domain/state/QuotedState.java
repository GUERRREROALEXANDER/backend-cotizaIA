package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** A priced quote is ready for review and can still be edited (project.txt section 6 pattern 9: State). */
final class QuotedState extends AbstractProposalState {
    QuotedState() {
        super(ProposalStatus.QUOTED);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case SUBMIT_FOR_REVIEW -> event.target();
            case EXPIRE -> event.target();
            default -> null;
        };
    }

    @Override
    public boolean allowsQuoteEdits() {
        return true;
    }
}
