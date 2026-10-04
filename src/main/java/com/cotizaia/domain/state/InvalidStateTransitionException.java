package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;
import java.util.Set;

/**
 * Carries a rejected State transition for API reporting (project.txt section 6 pattern 9).
 * The API maps this domain rejection to HTTP 422.
 */
public class InvalidStateTransitionException extends IllegalStateException {
    private final ProposalStatus from;
    private final ProposalEvent event;
    private final ProposalStatus to;

    public InvalidStateTransitionException(ProposalStatus from, ProposalEvent event,
            ProposalStatus to, Set<ProposalStatus> allowed) {
        super("Illegal proposal transition: " + from + " -> " + to
                + " (allowed from " + from + ": " + allowed + ")");
        this.from = from;
        this.event = event;
        this.to = to;
    }

    public ProposalStatus getFrom() {
        return from;
    }

    public ProposalEvent getEvent() {
        return event;
    }

    public ProposalStatus getTo() {
        return to;
    }
}
