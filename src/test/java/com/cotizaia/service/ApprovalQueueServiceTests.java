package com.cotizaia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.domain.Agency;
import com.cotizaia.domain.Brief;
import com.cotizaia.domain.BriefChannel;
import com.cotizaia.domain.Client;
import com.cotizaia.domain.ContractStatus;
import com.cotizaia.domain.Phase;
import com.cotizaia.domain.Proposal;
import com.cotizaia.domain.ProposalDocument;
import com.cotizaia.domain.ProposalStatus;
import com.cotizaia.domain.QuotedItem;
import com.cotizaia.domain.Role;
import com.cotizaia.domain.ServiceCatalog;
import com.cotizaia.facade.QuotationFacade;
import com.cotizaia.facade.QuotationResult;
import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.PaymentRepository;
import com.cotizaia.repository.ProposalDocumentRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.RoleRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "llm.provider=stub")
@ActiveProfiles("test")
class ApprovalQueueServiceTests {

    @Autowired
    private ApprovalQueueService service;

    @Autowired
    private QuotationFacade facade;

    @Autowired
    private AgencyRepository agencies;

    @Autowired
    private ClientRepository clients;

    @Autowired
    private BriefRepository briefs;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private ServiceCatalogRepository catalogs;

    @Autowired
    private ProposalRepository proposals;

    @Autowired
    private ProposalDocumentRepository documents;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void reviewEditsRegenerateDocumentsBeforeSendAndSimulatedAcceptance() {
        Brief brief = fixture();
        Long agencyId = brief.getClient().getAgency().getId();
        QuotationResult result = facade.processBrief(brief.getId());
        Long proposalId = result.proposalId();
        assertThat(service.queue(agencyId)).extracting(Proposal::getId).containsExactly(proposalId);
        assertThatThrownBy(() -> service.send(agencyId, proposalId)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Proposal must be approved before sending");
        service.approve(agencyId, proposalId);
        ReviewSnapshot before = snapshot(proposalId);
        List<ProposalDocument> originalDocuments = documents.findByProposalIdOrderByTypeAsc(proposalId);
        Proposal edited = service.adjustHours(agencyId, proposalId, before.itemId(), new BigDecimal("400"));
        assertThat(edited.isApproved()).isFalse();
        assertThat(edited.getTotal()).isGreaterThan(before.total());
        ReviewSnapshot after = snapshot(proposalId);
        assertThat(after.lastStartWeek()).isGreaterThan(before.lastStartWeek());
        assertThatThrownBy(() -> service.send(agencyId, proposalId)).isInstanceOf(IllegalStateException.class);
        service.approve(agencyId, proposalId);
        assertThat(service.get(agencyId, proposalId).getApprovedAt()).isNotNull();
        List<ProposalDocument> regenerated = documents.findByProposalIdOrderByTypeAsc(proposalId);
        assertThat(regenerated).hasSize(3);
        for (int index = 0; index < regenerated.size(); index++) {
            ProposalDocument previous = originalDocuments.get(index);
            ProposalDocument current = regenerated.get(index);
            assertThat(current.getId()).isEqualTo(previous.getId());
            assertThat(current.getGeneratedAt()).isAfter(previous.getGeneratedAt());
            assertThat(current.getContent()).isNotEqualTo(previous.getContent());
        }
        assertThat(service.send(agencyId, proposalId).getStatus()).isEqualTo(ProposalStatus.SENT);
        assertThat(service.queue(agencyId)).isEmpty();
        assertThatThrownBy(() -> service.adjustHours(agencyId, proposalId, after.itemId(), BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SENT");
        ProposalAcceptanceService.AcceptanceResult accepted = service.accept(agencyId, proposalId);
        assertThat(accepted.proposalStatus()).isEqualTo(ProposalStatus.CONTRACT_ISSUED);
        assertThat(accepted.contractStatus()).isEqualTo(ContractStatus.ISSUED);
        assertThat(accepted.simulated()).isTrue();
        assertThat(accepted.depositAmount()).isEqualByComparingTo(edited.getTotal().multiply(new BigDecimal("0.50")));
        assertThat(payments.findByProposalId(proposalId)).hasSize(1);
    }

    @Test
    void queueOrdersOldestFirstAndWorkflowDelegatesNegotiationRejectionAndExpiry() {
        Brief firstBrief = fixture();
        Long agencyId = firstBrief.getClient().getAgency().getId();
        QuotationResult first = facade.processBrief(firstBrief.getId());
        Brief secondBrief = briefs.saveAndFlush(new Brief(firstBrief.getClient(), BriefChannel.WEB_FORM,
                firstBrief.getRawText(), "{}", Instant.now()));
        QuotationResult second = facade.processBrief(secondBrief.getId());
        assertThat(service.queue(agencyId)).extracting(Proposal::getId)
                .containsExactly(first.proposalId(), second.proposalId());
        service.approve(agencyId, first.proposalId());
        service.send(agencyId, first.proposalId());
        assertThat(service.negotiate(agencyId, first.proposalId()).getStatus()).isEqualTo(ProposalStatus.NEGOTIATING);
        assertThat(service.reject(agencyId, first.proposalId()).getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(service.expire(agencyId, second.proposalId()).getStatus()).isEqualTo(ProposalStatus.EXPIRED);
        assertThat(service.queue(agencyId)).isEmpty();
    }

    @Test
    void everyProposalOperationRejectsAnotherAgency() {
        Brief brief = fixture();
        QuotationResult result = facade.processBrief(brief.getId());
        Long proposalId = result.proposalId();
        Long otherAgency = agencies.saveAndFlush(new Agency("Other " + UUID.randomUUID())).getId();
        Long itemId = snapshot(proposalId).itemId();
        assertThat(service.queue(otherAgency)).isEmpty();
        List<Runnable> operations = List.of(
                () -> service.get(otherAgency, proposalId),
                () -> service.adjustHours(otherAgency, proposalId, itemId, BigDecimal.ONE),
                () -> service.approve(otherAgency, proposalId),
                () -> service.send(otherAgency, proposalId),
                () -> service.negotiate(otherAgency, proposalId),
                () -> service.reject(otherAgency, proposalId),
                () -> service.expire(otherAgency, proposalId),
                () -> service.accept(otherAgency, proposalId));
        for (Runnable operation : operations) {
            assertThatThrownBy(operation::run).isInstanceOf(NoSuchElementException.class);
        }
        assertThat(proposals.findById(proposalId).orElseThrow().getStatus()).isEqualTo(ProposalStatus.IN_REVIEW);
    }

    private ReviewSnapshot snapshot(Long proposalId) {
        return transactions.execute(status -> {
            Proposal proposal = proposals.findById(proposalId).orElseThrow();
            QuotedItem item = proposal.getItems().get(0);
            int lastStart = proposal.getSchedulePlan().getPhases().stream()
                    .mapToInt(Phase::getStartWeek).max().orElseThrow();
            return new ReviewSnapshot(item.getId(), proposal.getTotal(), lastStart);
        });
    }

    private Brief fixture() {
        String suffix = UUID.randomUUID().toString();
        Agency agency = agencies.saveAndFlush(new Agency("Approval " + suffix));
        Client client = clients.saveAndFlush(new Client(agency, "Client", suffix + "@example.com"));
        Role role = new Role(agency, "Developer");
        role.addRate(100000);
        roles.saveAndFlush(role);
        ServiceCatalog catalog = new ServiceCatalog(agency, "Desarrollo web");
        catalog.addRequirementType("Diseno responsive", "pagina, web, sitio", new BigDecimal("24.00"));
        catalog.addRequirementType("Menu digital", "menu, carta", new BigDecimal("12.00"));
        catalog.addRequirementType("Sistema de reservas", "reserva, reservas, agenda", new BigDecimal("32.00"));
        catalogs.saveAndFlush(catalog);
        return briefs.saveAndFlush(new Brief(client, BriefChannel.WEB_FORM,
                "Necesito una pagina web para restaurante con menu digital y reservas", "{}", Instant.now()));
    }

    private record ReviewSnapshot(Long itemId, BigDecimal total, int lastStartWeek) {
    }
}
