package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.AppUser;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.ExtractedRequirement;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.domain.UserRole;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.AppUserRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.NotificationRepository;
import com.cotizaia.repository.PaymentRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies atomic demo acceptance and deposit persistence (project.txt section 4). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProposalAcceptanceServiceTests {

    @Autowired private AgencyRepository agencies;

    @Autowired private AppUserRepository users;

    @Autowired private BriefRepository briefs;

    @Autowired private ClientRepository clients;

    @Autowired private ContractRepository contracts;

    @Autowired private ExtractedRequirementRepository requirements;

    @Autowired private NotificationRepository notifications;

    @Autowired private PaymentRepository payments;

    @Autowired private ProposalRepository proposals;

    @Autowired private ServiceCatalogRepository catalogs;

    @Autowired private ProposalAcceptanceService acceptance;

    @Test
    void acceptsWithOneSimulatedDepositAndNotifications() {
        Proposal proposal = proposal("Accepted agency", true);
        ProposalAcceptanceService.AcceptanceResult result = acceptance.accept(proposal.getId());
        assertThat(result.proposalStatus()).isEqualTo(ProposalStatus.CONTRACT_ISSUED);
        assertThat(result.contractStatus()).isEqualTo(ContractStatus.ISSUED);
        assertThat(result.depositAmount()).isEqualByComparingTo("617283.95");
        assertThat(result.providerRef()).startsWith("SIM-");
        assertThat(result.simulated()).isTrue();
        assertThat(payments.findByProposalId(proposal.getId())).hasSize(1);
        assertThat(notifications.findByProposalIdOrderByIdAsc(proposal.getId()))
                .filteredOn(row -> row.getPayload().contains("ACCEPTED")
                        || row.getPayload().contains("CONTRACT_ISSUED"))
                .hasSize(6);
        assertThatThrownBy(() -> acceptance.accept(proposal.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByProposalId(proposal.getId())).hasSize(1);
    }

    @Test
    void refusesReviewWithoutIssuingOrRecordingDeposit() {
        Proposal proposal = proposal("Review agency", false);
        assertThatThrownBy(() -> acceptance.accept(proposal.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByProposalId(proposal.getId())).isEmpty();
        assertThat(contracts.findByProposalId(proposal.getId())).isEmpty();
    }

    private Proposal proposal(String name, boolean sent) {
        Agency agency = agencies.saveAndFlush(new Agency(name));
        users.saveAndFlush(new AppUser(agency, "Owner", name.replace(' ', '.') + "@example.co", UserRole.OWNER));
        Client client = clients.saveAndFlush(new Client(agency, "Client", "client@example.co"));
        Brief brief = briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM, "A website", "{}", Instant.now()));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Web");
        catalog.addRequirementType("Website", "Build", new BigDecimal("20.00"));
        catalog = catalogs.saveAndFlush(catalog);
        ExtractedRequirement requirement = requirements.saveAndFlush(new ExtractedRequirement(brief,
                catalog.getRequirementTypes().get(0), "Website", new BigDecimal("20.00"),
                new BigDecimal("0.9000")));
        Proposal proposal = new Proposal.Builder().brief(brief)
                .addItem(requirement, BigDecimal.ONE, new BigDecimal("1234567.89")).build();
        proposal.transitionTo(ProposalStatus.ANALYZING);
        proposal.transitionTo(ProposalStatus.QUOTED);
        proposal.transitionTo(ProposalStatus.IN_REVIEW);
        if (sent) {
            proposal.transitionTo(ProposalStatus.SENT);
        }
        return proposals.saveAndFlush(proposal);
    }
}
