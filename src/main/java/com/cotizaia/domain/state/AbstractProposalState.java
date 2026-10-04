package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Shared State mechanics (project.txt section 6 pattern 9). Each concrete state's
 * {@link #next} is the single source of its rules; {@link #allowedTransitions}
 * derives its result from those same event decisions.
 */
public abstract class AbstractProposalState implements ProposalState {
    private final ProposalStatus status;

    protected AbstractProposalState(ProposalStatus status) {
        this.status = status;
    }

    @Override
    public ProposalStatus status() {
        return status;
    }

    protected abstract ProposalStatus next(ProposalEvent event);

    @Override
    public ProposalState handle(ProposalEvent event) {
        ProposalStatus target = event == null ? null : next(event);
        if (target == null) {
            throw new InvalidStateTransitionException(status, event,
                    event == null ? null : event.target(), allowedTransitions());
        }
        return ProposalStates.of(target);
    }

    @Override
    public boolean canTransitionTo(ProposalStatus target) {
        if (target == null || target == ProposalStatus.RECEIVED) {
            return false;
        }
        return next(ProposalEvent.leadingTo(target)) != null;
    }

    @Override
    public Set<ProposalStatus> allowedTransitions() {
        EnumSet<ProposalStatus> allowed = EnumSet.noneOf(ProposalStatus.class);
        for (ProposalEvent event : ProposalEvent.values()) {
            ProposalStatus target = next(event);
            if (target != null) {
                allowed.add(target);
            }
        }
        return Collections.unmodifiableSet(allowed);
    }

    @Override
    public boolean allowsQuoteEdits() {
        return false;
    }

    @Override
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
