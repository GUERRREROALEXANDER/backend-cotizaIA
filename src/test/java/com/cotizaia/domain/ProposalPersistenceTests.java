package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence acceptance for issue #8: the proposal tables exist, the aggregate
 * writes its quoted items, and the stored totals always match the lines.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProposalPersistenceTests {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void v8CreatesProposalTables() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        for (String table : new String[] {"proposals", "quoted_items"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE LOWER(table_name) = LOWER(?) AND LOWER(table_schema) = 'public'",
                    Integer.class,
                    table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }
    }

    @Test
    void persistsProposalWithItemsAndDerivedTotals() {
        Fixture fixture = fixture("Agencia Propuesta");

        Proposal proposal = new Proposal.Builder()
                .brief(fixture.brief())
                .validUntil(Instant.parse("2026-03-01T00:00:00Z"))
                .terms("50% deposit")
                .addItem(fixture.requirement(), new BigDecimal("10"), new BigDecimal("50"))
                .addItem(fixture.requirement(), new BigDecimal("2.5"), new BigDecimal("100"))
                .build();
        Proposal saved = proposalRepository.saveAndFlush(proposal);

        assertThat(saved.getId()).isNotNull();
        Proposal reloaded = proposalRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getBrief().getId()).isEqualTo(fixture.brief().getId());
        assertThat(reloaded.getStatus()).isEqualTo(ProposalStatus.RECEIVED);
        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.getSubtotal()).isEqualByComparingTo("750.00");
        assertThat(reloaded.getTotal()).isEqualByComparingTo("750.00");
        assertThat(reloaded.getTerms()).isEqualTo("50% deposit");
    }

    @Test
    void persistedTotalAlwaysMatchesSumOfLineTotals() {
        Fixture fixture = fixture("Agencia Totales");

        Proposal saved = proposalRepository.saveAndFlush(new Proposal.Builder()
                .brief(fixture.brief())
                .addItem(fixture.requirement(), new BigDecimal("3"), new BigDecimal("120.50"))
                .addItem(fixture.requirement(), new BigDecimal("7"), new BigDecimal("80.25"))
                .build());

        BigDecimal storedTotal = jdbcTemplate.queryForObject(
                "SELECT total FROM proposals WHERE id = ?", BigDecimal.class, saved.getId());
        BigDecimal sumOfLines = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(line_total), 0) FROM quoted_items WHERE proposal_id = ?",
                BigDecimal.class,
                saved.getId());

        assertThat(storedTotal).isEqualByComparingTo(sumOfLines);
        assertThat(storedTotal).isEqualByComparingTo("923.25");
    }

    @Test
    void scopesProposalLookupByBriefAgency() {
        Fixture fixture = fixture("Agencia Scope Propuesta");
        Proposal proposal = proposalRepository.saveAndFlush(
                new Proposal.Builder().brief(fixture.brief()).build());

        assertThat(proposalRepository.findByIdAndBriefClientAgencyId(
                        proposal.getId(), fixture.brief().getClient().getAgency().getId()))
                .isPresent();
        assertThat(proposalRepository.findByIdAndBriefClientAgencyId(proposal.getId(), -1L))
                .isEmpty();
    }

    @Test
    void cascadeDeletesQuotedItemsWithTheirProposal() {
        Fixture fixture = fixture("Agencia Cascade");

        Proposal proposal = proposalRepository.saveAndFlush(new Proposal.Builder()
                .brief(fixture.brief())
                .addItem(fixture.requirement(), BigDecimal.ONE, new BigDecimal("100"))
                .build());
        Long proposalId = proposal.getId();

        proposalRepository.delete(proposal);
        proposalRepository.flush();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quoted_items WHERE proposal_id = ?", Integer.class, proposalId);
        assertThat(remaining).isZero();
    }

    @Test
    void rejectsUnknownBriefForProposalByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO proposals (brief_id, status, subtotal, total, created_at)"
                        + " VALUES (?, 'RECEIVED', 0, 0, CURRENT_TIMESTAMP)",
                -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownRequirementForQuotedItemByForeignKey() {
        Fixture fixture = fixture("Agencia FK Requisito");
        Proposal proposal = proposalRepository.saveAndFlush(
                new Proposal.Builder().brief(fixture.brief()).build());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO quoted_items (proposal_id, requirement_id, hours, unit_price, line_total)"
                        + " VALUES (?, ?, 1, 10, 10)",
                proposal.getId(), -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownProposalStatusByCheckConstraint() {
        Fixture fixture = fixture("Agencia Check Estado");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO proposals (brief_id, status, subtotal, total, created_at)"
                        + " VALUES (?, 'EXPLODED', 0, 0, CURRENT_TIMESTAMP)",
                fixture.brief().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativeQuotedItemValuesByCheckConstraint() {
        Fixture fixture = fixture("Agencia Check Linea");
        Proposal proposal = proposalRepository.saveAndFlush(
                new Proposal.Builder().brief(fixture.brief()).build());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO quoted_items (proposal_id, requirement_id, hours, unit_price, line_total)"
                        + " VALUES (?, ?, -1, 10, -10)",
                proposal.getId(), fixture.requirement().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(String agencyName) {
        Agency agency = agencyRepository.saveAndFlush(new Agency(agencyName));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente " + agencyName, agencyName + "@propuesta.co"));
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
        return new Fixture(brief, requirement);
    }

    private record Fixture(Brief brief, ExtractedRequirement requirement) {
    }
}
