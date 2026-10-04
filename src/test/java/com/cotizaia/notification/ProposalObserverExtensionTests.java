package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.NotificationRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.NotificationService;
import com.cotizaia.service.ProposalWorkflowService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uses a separate Spring context so an extra Observer bean proves extension
 * while the normal integration context still expects exactly three records.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(ProposalObserverExtensionTests.ExtraObserverConfiguration.class)
class ProposalObserverExtensionTests {

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private ProposalWorkflowService workflow;

    @Test
    void fourthBeanWritesFourthRow() {
        Agency agency = agencies.saveAndFlush(new Agency("Extension Agency"));
        Client client = clients.saveAndFlush(new Client(agency, "Client", "extension@example.co"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM,
                "A website", "{}", Instant.now()));
        Proposal proposal = proposals.saveAndFlush(new Proposal.Builder()
                .brief(brief).status(ProposalStatus.QUOTED).build());

        workflow.transition(proposal.getId(), ProposalEvent.SUBMIT_FOR_REVIEW);

        assertThat(notifications.findByProposalIdOrderByIdAsc(proposal.getId()))
                .extracting(notification -> notification.getChannel())
                .containsExactlyInAnyOrder("EMAIL", "DASHBOARD", "ANALYTICS", "AUDIT_TEST");
    }

    @TestConfiguration
    static class ExtraObserverConfiguration {

        @Bean
        ProposalObserver auditTestObserver(NotificationService notifications,
                AgencyRepository agencies, ProposalRepository proposals) {
            return new ProposalObserver() {

                @Override
                public String channel() {
                    return "AUDIT_TEST";
                }

                @Override
                public void onStateChanged(ProposalStateChangedEvent event) {
                    notifications.record(agencies.getReferenceById(event.agencyId()),
                            proposals.getReferenceById(event.proposalId()), channel(),
                            "PROPOSAL_STATUS_CHANGED", Map.of("to", event.to()));
                }
            };
        }
    }
}
