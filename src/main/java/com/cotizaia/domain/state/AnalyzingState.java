package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The received brief is being analyzed before pricing (project.txt section 6 pattern 9: State). */
final class AnalyzingState extends AbstractProposalState {
    AnalyzingState() {
        super(ProposalStatus.ANALYZING);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        return switch (event) {
            case QUOTE -> event.target();
            case EXPIRE -> event.target();
            default -> null;
        };
    }
}
