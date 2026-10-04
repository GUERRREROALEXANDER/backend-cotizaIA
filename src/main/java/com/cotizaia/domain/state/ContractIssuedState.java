package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/** The contract has been issued and the proposal is complete (project.txt section 6 pattern 9: State). */
final class ContractIssuedState extends AbstractProposalState {
    ContractIssuedState() {
        super(ProposalStatus.CONTRACT_ISSUED);
    }

    @Override
    protected ProposalStatus next(ProposalEvent event) {
        // terminal: accepts no event
        return null;
    }
}
