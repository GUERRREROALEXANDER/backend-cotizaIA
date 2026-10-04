package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks Subject attach, detach, and ordered synchronous dispatch without Spring. */
class ProposalSubjectTests {

    @Test
    void dispatchesInAttachmentOrderAndStopsAfterDetach() {
        List<String> calls = new ArrayList<>();
        ProposalObserver first = observer("FIRST", calls);
        ProposalObserver second = observer("SECOND", calls);
        ProposalSubject subject = new ProposalSubject(List.of(first));
        subject.attach(second);

        subject.notifyObservers(null);
        assertThat(calls).containsExactly("FIRST", "SECOND");
        assertThat(subject.observers()).containsExactly(first, second);
        assertThatThrownBy(() -> subject.observers().add(first))
                .isInstanceOf(UnsupportedOperationException.class);

        calls.clear();
        subject.detach(first);
        subject.notifyObservers(null);
        assertThat(calls).containsExactly("SECOND");
    }

    private ProposalObserver observer(String channel, List<String> calls) {
        return new ProposalObserver() {

            @Override
            public String channel() {
                return channel;
            }

            @Override
            public void onStateChanged(ProposalStateChangedEvent event) {
                calls.add(channel);
            }
        };
    }
}
