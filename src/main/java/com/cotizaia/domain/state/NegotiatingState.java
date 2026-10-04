package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The sent quote is under negotiation and can be revised (project.txt section 6 pattern 9: State). */
final class NegotiatingState extends AbstractProposalState {
    NegotiatingState() {
        super(ProposalStatus.NEGOTIATING);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case ACCEPT -> event.target();
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
