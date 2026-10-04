package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** An accepted offer awaits contract issuance (project.txt section 6 pattern 9: State). */
final class AcceptedState extends AbstractProposalState {
    AcceptedState() {
        super(ProposalStatus.ACCEPTED);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case ISSUE_CONTRACT -> event.target();
            default -> null;
        };
    }
}
