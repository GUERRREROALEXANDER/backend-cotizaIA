package com.cotizaia.notification;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.NotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver for the agency dashboard feed (project.txt section 6: Observer).
 * Its persisted sentence gives staff a readable account of each accepted move.
 */
@Component
public class DashboardObserver implements ProposalObserver {

    private final AgencyRepository agencies;
    private final ProposalRepository proposals;
    private final NotificationService notifications;

    public DashboardObserver(AgencyRepository agencies, ProposalRepository proposals,
            NotificationService notifications) {
        this.agencies = agencies;
        this.proposals = proposals;
        this.notifications = notifications;
    }

    @Override
    public String channel() {
        return "DASHBOARD";
    }

    @Override
    public void onStateChanged(ProposalStateChangedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("proposalId", event.proposalId());
        payload.put("briefId", event.briefId());
        payload.put("clientName", event.clientName());
        payload.put("from", event.from());
        payload.put("to", event.to());
        payload.put("message", "La propuesta de " + event.clientName() + " cambio a " + event.to() + ".");
        notifications.record(agencies.getReferenceById(event.agencyId()),
                proposals.getReferenceById(event.proposalId()), channel(), "PROPOSAL_STATUS_CHANGED", payload);
    }
}
