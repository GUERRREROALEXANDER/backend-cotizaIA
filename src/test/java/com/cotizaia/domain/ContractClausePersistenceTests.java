package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ContractRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import com.cotizaia.service.ContractService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence acceptance for issue #11: the {@code contracts} and {@code clauses}
 * tables exist, a draft round-trips unissued, and the accept-plus-payment
 * service flips the contract to ISSUED atomically with the payment record.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContractClausePersistenceTests {

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private BriefRepository briefRepository;

    @Autowired
    private ServiceCatalogRepository catalogRepository;

    @Autowired
    private ExtractedRequirementRepository extractedRequirementRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ContractService contractService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v11CreatesContractTables() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '11' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        for (String table : new String[] {"contracts", "clauses"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE LOWER(table_name) = LOWER(?) AND LOWER(table_schema) = 'public'",
                    Integer.class,
                    table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }
    }

    @Test
    void draftExistsBeforeAcceptanceButIsNotIssued() {
        Proposal proposal = proposal("Agencia Borrador");

        Contract draft = contractService.generateDraft(
                proposal.getId(), List.of(new ContractService.ClauseSpec("Alcance del proyecto")));

        Contract reloaded = contractRepository.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ContractStatus.DRAFT);
        assertThat(reloaded.getIssuedAt()).isNull();
        assertThat(reloaded.getPaymentReference()).isNull();
        assertThat(reloaded.getClauses()).extracting(Clause::getText)
                .containsExactly("Alcance del proyecto");
    }

    @Test
    void generateDraftSeedsStandardClausesWhenNoneAreGiven() {
        Proposal proposal = proposal("Agencia Clauses");

        Contract draft = contractService.generateDraft(proposal.getId(), null);

        assertThat(draft.getClauses()).hasSize(5);
        assertThat(draft.getClauses()).extracting(Clause::getOrder).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void acceptPlusPaymentFlipsContractToIssuedAtomically() {
        Proposal proposal = proposalWithStatus("Agencia Emision", ProposalStatus.SENT);
        Contract draft = contractService.generateDraft(proposal.getId(), null);

        Instant paidAt = Instant.parse("2026-02-01T10:00:00Z");
        contractService.acceptAndIssue(
                proposal.getId(), "SIM-PAY-777", new BigDecimal("250.00"), paidAt);

        Contract reloaded = contractRepository.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ContractStatus.ISSUED);
        assertThat(reloaded.getIssuedAt()).isNotNull();
        assertThat(reloaded.getPaymentReference()).isEqualTo("SIM-PAY-777");
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo("250.00");
        assertThat(reloaded.getPaidAt()).isEqualTo(paidAt);

        // The proposal is advanced in the same transaction and the payment
        // evidence is stored, not only in memory.
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.CONTRACT_ISSUED);
        String storedReference = jdbcTemplate.queryForObject(
                "SELECT payment_reference FROM contracts WHERE id = ?", String.class, draft.getId());
        assertThat(storedReference).isEqualTo("SIM-PAY-777");
    }

    @Test
    void rejectedAcceptanceLeavesTheContractDraftWithNoPaymentRecord() {
        Proposal proposal = proposalWithStatus("Agencia Rechazo", ProposalStatus.SENT);
        Contract draft = contractService.generateDraft(proposal.getId(), null);

        assertThatThrownBy(() -> contractService.acceptAndIssue(
                proposal.getId(), "  ", new BigDecimal("250.00"), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM contracts WHERE id = ?", String.class, draft.getId());
        String reference = jdbcTemplate.queryForObject(
                "SELECT payment_reference FROM contracts WHERE id = ?", String.class, draft.getId());
        assertThat(status).isEqualTo("DRAFT");
        assertThat(reference).isNull();
        assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
                .isEqualTo(ProposalStatus.SENT);
    }

    @Test
    void rejectsASecondDraftForTheSameProposal() {
        Proposal proposal = proposalWithStatus("Agencia Unico Contrato", ProposalStatus.SENT);
        contractService.generateDraft(proposal.getId(), null);

        assertThatThrownBy(() -> contractService.generateDraft(proposal.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void rejectsIssuedStatusWithoutPaymentEvidenceByCheckConstraint() {
        Proposal proposal = proposalWithStatus("Agencia Check Contrato", ProposalStatus.SENT);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO contracts (proposal_id, status, created_at)"
                        + " VALUES (?, 'ISSUED', CURRENT_TIMESTAMP)",
                proposal.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownProposalForContractByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO contracts (proposal_id, status, created_at)"
                        + " VALUES (?, 'DRAFT', CURRENT_TIMESTAMP)",
                -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateClauseOrderWithinAContract() {
        Proposal proposal = proposalWithStatus("Agencia Check Clauses", ProposalStatus.SENT);
        Contract draft = contractService.generateDraft(
                proposal.getId(), List.of(new ContractService.ClauseSpec("Primera")));
        Long contractId = draft.getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO clauses (contract_id, clause_order, text) VALUES (?, 1, 'Duplicada')",
                contractId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadeDeletesContractAndClausesWithTheirProposal() {
        Proposal proposal = proposalWithStatus("Agencia Cascade Contrato", ProposalStatus.SENT);
        Contract draft = contractService.generateDraft(proposal.getId(), null);
        Long proposalId = proposal.getId();
        Long contractId = draft.getId();

        jdbcTemplate.update("DELETE FROM proposals WHERE id = ?", proposalId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contracts WHERE id = ?", Integer.class, contractId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clauses WHERE contract_id = ?", Integer.class, contractId)).isZero();
    }

    @Test
    void scopesContractLookupByProposalAgency() {
        Proposal proposal = proposalWithStatus("Agencia Scope Contrato", ProposalStatus.SENT);
        Contract draft = contractService.generateDraft(proposal.getId(), null);
        Long agencyId = proposal.getBrief().getClient().getAgency().getId();

        assertThat(contractRepository.findByIdAndProposalBriefClientAgencyId(draft.getId(), agencyId))
                .isPresent();
        assertThat(contractRepository.findByIdAndProposalBriefClientAgencyId(draft.getId(), -1L))
                .isEmpty();
    }

    private Proposal proposal(String agencyName) {
        return proposalWithStatus(agencyName, ProposalStatus.QUOTED);
    }

    private Proposal proposalWithStatus(String agencyName, ProposalStatus status) {
        Agency agency = agencyRepository.saveAndFlush(new Agency(agencyName));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente " + agencyName, agencyName + "@contrato.co"));
        Brief brief = briefRepository.saveAndFlush(new Brief(
                client, BriefChannel.WEB_FORM, "Necesito una web con reservas", "{}", Instant.now()));
        ServiceCatalog catalog = new ServiceCatalog(agency, "Web " + agencyName);
        catalog.addRequirementType("Online reservations", "Booking flow", new BigDecimal("24.00"));
        catalog = catalogRepository.saveAndFlush(catalog);
        ExtractedRequirement requirement = extractedRequirementRepository.saveAndFlush(
                new ExtractedRequirement(
                        brief,
                        catalog.getRequirementTypes().get(0),
                        "Reservas en linea",
                        new BigDecimal("20.00"),
                        new BigDecimal("0.9200")));
        Proposal proposal = new Proposal.Builder()
                .brief(brief)
                .status(status)
                .addItem(requirement, new BigDecimal("10"), new BigDecimal("50"))
                .build();
        return proposalRepository.saveAndFlush(proposal);
    }
}
