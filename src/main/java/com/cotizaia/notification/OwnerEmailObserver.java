package com.cotizaia.notification;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.UserRole;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.NotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ConcreteObserver for simulated owner email (project.txt section 6: Observer).
 * Persisting the message even without an owner preserves an auditable event;
 * a future mail adapter can replace the simulation without changing dispatch.
 */
@Component
public class OwnerEmailObserver implements ProposalObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwnerEmailObserver.class);
    private static final String EVENT_TYPE = "PROPOSAL_STATUS_CHANGED";

    private final AgencyRepository agencies;
    private final AppUserRepository users;
    private final ProposalRepository proposals;
    private final NotificationService notifications;

    public OwnerEmailObserver(AgencyRepository agencies, AppUserRepository users,
            ProposalRepository proposals, NotificationService notifications) {
        this.agencies = agencies;
        this.users = users;
        this.proposals = proposals;
        this.notifications = notifications;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public void onStateChanged(ProposalStateChangedEvent event) {
        Agency agency = agencies.getReferenceById(event.agencyId());
        Proposal proposal = proposals.getReferenceById(event.proposalId());
        String recipient = users.findByAgencyIdAndRole(event.agencyId(), UserRole.OWNER)
                .map(AppUser::getEmail).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", recipient);
        payload.put("subject", "Cambio de estado de propuesta " + event.proposalId());
        payload.put("body", "La propuesta cambio de " + event.from() + " a " + event.to() + ".");
        payload.put("simulated", true);
        notifications.record(agency, proposal, channel(), EVENT_TYPE, payload);
        LOGGER.info("SIMULATED owner email for proposal {} to {}", event.proposalId(), recipient);
    }
}
