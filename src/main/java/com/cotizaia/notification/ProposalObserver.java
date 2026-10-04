package com.cotizaia.notification;

/**
 * Observer role for proposal state changes (project.txt section 6: Observer).
 * A new channel joins dispatch by implementing this interface as a Spring bean.
 */
public interface ProposalObserver {

    String channel();

    void onStateChanged(ProposalStateChangedEvent event);
}
