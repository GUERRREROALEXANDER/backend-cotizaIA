package com.cotizaia.notification;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.NotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver for lifecycle metrics (project.txt section 6: Observer).
 * Recording the transition snapshot lets analytics audit counts and amounts later.
 */
@Component
public class AnalyticsObserver implements ProposalObserver {

    private final AgencyRepository agencies;
    private final ProposalRepository proposals;
    private final NotificationService notifications;

    public AnalyticsObserver(AgencyRepository agencies, ProposalRepository proposals,
            NotificationService notifications) {
        this.agencies = agencies;
        this.proposals = proposals;
        this.notifications = notifications;
    }

    @Override
    public String channel() {
        return "ANALYTICS";
    }

    @Override
    public void onStateChanged(ProposalStateChangedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("proposalId", event.proposalId());
        payload.put("from", event.from());
        payload.put("to", event.to());
        payload.put("changedAt", event.changedAt());
        payload.put("total", event.total());
        notifications.record(agencies.getReferenceById(event.agencyId()),
                proposals.getReferenceById(event.proposalId()), channel(), "PROPOSAL_STATUS_CHANGED", payload);
    }
}
