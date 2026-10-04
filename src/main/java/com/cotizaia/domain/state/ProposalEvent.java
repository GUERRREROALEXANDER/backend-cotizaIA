package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;

/**
 * Events request lifecycle changes, while statuses represent persisted outcomes
 * (project.txt section 6 pattern 9). A State decides whether an event is accepted.
 */
public enum ProposalEvent {
    START_ANALYSIS(ProposalStatus.ANALYZING), QUOTE(ProposalStatus.QUOTED),
    SUBMIT_FOR_REVIEW(ProposalStatus.IN_REVIEW), SEND(ProposalStatus.SENT),
    NEGOTIATE(ProposalStatus.NEGOTIATING), ACCEPT(ProposalStatus.ACCEPTED),
    ISSUE_CONTRACT(ProposalStatus.CONTRACT_ISSUED), REJECT(ProposalStatus.REJECTED),
    EXPIRE(ProposalStatus.EXPIRED);

    private final ProposalStatus target;

    ProposalEvent(ProposalStatus target) {
        this.target = target;
    }

    public ProposalStatus target() {
        return target;
    }

    public static ProposalEvent leadingTo(ProposalStatus target) {
        for (ProposalEvent event : values()) {
            if (event.target == target) {
                return event;
            }
        }
        throw new IllegalArgumentException("No event leads to " + target);
    }
}
