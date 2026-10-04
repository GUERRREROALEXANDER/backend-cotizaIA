package com.cotizaia.domain.state;

import com.cotizaia.domain.ProposalStatus;
import java.util.Set;

/**
 * Design pattern - <b>State</b>: Context {@code Proposal} delegates lifecycle
 * behavior to State {@code ProposalState} and its ConcreteStates (project.txt
 * section 6 pattern 9). {@link ProposalStates} registers stateless shared
 * instances. {@code ProposalStatus} remains the persisted value so the database
 * stores a stable status while these objects own transition behavior.
 */
public interface ProposalState {
    ProposalStatus status();
    ProposalState handle(ProposalEvent event);
    boolean canTransitionTo(ProposalStatus target);
    Set<ProposalStatus> allowedTransitions();
    boolean allowsQuoteEdits();
    boolean isTerminal();
}
