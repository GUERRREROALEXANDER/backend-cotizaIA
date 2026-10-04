package com.cotizaia.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.Notification;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.UserRole;
import com.cotizaia.domain.state.InvalidStateTransitionException;
import com.cotizaia.domain.state.ProposalEvent;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.NotificationRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.service.ProposalWorkflowService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that each accepted State move writes the three Observer records
 * and a rejected move writes none (project.txt section 6: State and Observer).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProposalObserverIntegrationTests {

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private AppUserRepository users;

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
    void acceptedMoveWritesThreeNotificationsAndHistory() {
        Proposal proposal = quotedProposal("Owner Agency");

        workflow.transition(proposal.getId(), ProposalEvent.SUBMIT_FOR_REVIEW);

        List<Notification> rows = notifications.findByProposalIdOrderByIdAsc(proposal.getId());
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder("EMAIL", "DASHBOARD", "ANALYTICS");
        assertThat(rows).allSatisfy(row -> assertThat(row.getEventType())
                .isEqualTo("PROPOSAL_STATUS_CHANGED"));
        assertThat(rows.stream().filter(row -> row.getChannel().equals("EMAIL")).findFirst().orElseThrow()
                .getPayload()).contains("owner@agency.co", "\"simulated\":true");
        assertThat(proposal.getStatusHistory()).hasSize(1);
        assertThat(proposal.getStatusHistory().get(0).getToStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
    }

    @Test
    void rejectedMoveWritesNoNotification() {
        Proposal proposal = quotedProposal("Invalid Agency");

        assertThatThrownBy(() -> workflow.transition(proposal.getId(), ProposalEvent.ISSUE_CONTRACT))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(notifications.countByProposalId(proposal.getId())).isZero();
        assertThat(proposal.getStatusHistory()).isEmpty();
    }

    private Proposal quotedProposal(String name) {
        Agency agency = agencies.saveAndFlush(new Agency(name));
        users.saveAndFlush(new AppUser(agency, "Owner", "owner@agency.co", UserRole.OWNER));
        Client client = clients.saveAndFlush(new Client(agency, "Client", "client@agency.co"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM,
                "A website", "{}", Instant.now()));
        return proposals.saveAndFlush(new Proposal.Builder().brief(brief).status(ProposalStatus.QUOTED).build());
    }
}
