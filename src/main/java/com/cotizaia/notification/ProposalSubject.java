package com.cotizaia.notification;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * Subject role in the Observer pattern (project.txt section 6: Observer).
 * Spring supplies every observer bean. Dispatch is synchronous in the caller's
 * transaction; an observer exception propagates and rolls back the transition
 * so a state change cannot commit without all its notification records.
 */
@Component
public class ProposalSubject {

    private final CopyOnWriteArrayList<ProposalObserver> observers;

    public ProposalSubject(List<ProposalObserver> observers) {
        this.observers = new CopyOnWriteArrayList<>(observers);
    }

    public void attach(ProposalObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        observers.addIfAbsent(observer);
    }

    public void detach(ProposalObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(ProposalStateChangedEvent event) {
        for (ProposalObserver observer : observers) {
            observer.onStateChanged(event);
        }
    }

    public List<ProposalObserver> observers() {
        return Collections.unmodifiableList(observers);
    }
}
