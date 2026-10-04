package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalExtraRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
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
 * Persistence acceptance for issue #9: the {@code proposal_extras} table exists
 * and stores the extras a decorator chain records on a proposal aggregate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProposalExtraPersistenceTests {

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
    private ProposalExtraRepository proposalExtraRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v9CreatesProposalExtrasTable() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '9' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE LOWER(table_name) = 'proposal_extras' AND LOWER(table_schema) = 'public'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void persistsExtrasRecordedByADecoratorChain() {
        Fixture fixture = fixture("Agencia Extras");

        Proposal proposal = proposalRepository.saveAndFlush(fixture.proposal());
        ProposalPriceDecorator priced =
                new DiscountDecorator(new UrgencyDecorator(new BaseProposalPrice(proposal)));
        priced.recordOn(proposal);
        proposalRepository.saveAndFlush(proposal);

        Proposal reloaded = proposalRepository.findById(proposal.getId()).orElseThrow();
        assertThat(reloaded.getSubtotal()).isEqualByComparingTo("750.00");
        assertThat(reloaded.getTotal()).isEqualByComparingTo("843.75");
        assertThat(reloaded.getAppliedExtras()).hasSize(2);
        assertThat(reloaded.getAppliedExtras().get(0).getType()).isEqualTo("URGENCY");
        assertThat(reloaded.getAppliedExtras().get(0).getPercentOrFixed()).isEqualByComparingTo("0.25");
        assertThat(reloaded.getAppliedExtras().get(0).getAmount()).isEqualByComparingTo("187.50");
        assertThat(reloaded.getAppliedExtras().get(1).getType()).isEqualTo("DISCOUNT");
        assertThat(reloaded.getAppliedExtras().get(1).getAmount()).isEqualByComparingTo("-93.75");
    }

    @Test
    void findsExtrasByProposalOrdered() {
        Fixture fixture = fixture("Agencia Orden");
        Proposal proposal = proposalRepository.saveAndFlush(fixture.proposal());
        new DiscountDecorator(new UrgencyDecorator(new BaseProposalPrice(proposal))).recordOn(proposal);
        proposalRepository.saveAndFlush(proposal);

        List<ProposalExtra> extras =
                proposalExtraRepository.findByProposalIdOrderByIdAsc(proposal.getId());

        assertThat(extras).extracting(ProposalExtra::getType)
                .containsExactly("URGENCY", "DISCOUNT");
    }

    @Test
    void cascadeDeletesExtrasWithTheirProposal() {
        Fixture fixture = fixture("Agencia Cascade Extras");
        Proposal proposal = proposalRepository.saveAndFlush(fixture.proposal());
        new UrgencyDecorator(new BaseProposalPrice(proposal)).recordOn(proposal);
        proposalRepository.saveAndFlush(proposal);
        Long proposalId = proposal.getId();

        proposalRepository.delete(proposal);
        proposalRepository.flush();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_extras WHERE proposal_id = ?", Integer.class, proposalId);
        assertThat(remaining).isZero();
    }

    @Test
    void rejectsUnknownProposalForExtraByForeignKey() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO proposal_extras (proposal_id, type, percent_or_fixed, amount, created_at)"
                        + " VALUES (?, 'URGENCY', 0.25, 10, CURRENT_TIMESTAMP)",
                -1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativePercentOrFixedByCheckConstraint() {
        Fixture fixture = fixture("Agencia Check Extra");
        Proposal proposal = proposalRepository.saveAndFlush(fixture.proposal());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO proposal_extras (proposal_id, type, percent_or_fixed, amount, created_at)"
                        + " VALUES (?, 'DISCOUNT', -0.10, -75, CURRENT_TIMESTAMP)",
                proposal.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(String agencyName) {
        Agency agency = agencyRepository.saveAndFlush(new Agency(agencyName));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente " + agencyName, agencyName + "@extras.co"));
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
                .addItem(requirement, new BigDecimal("10"), new BigDecimal("50"))
                .addItem(requirement, new BigDecimal("2.5"), new BigDecimal("100"))
                .build();
        return new Fixture(proposal);
    }

    private record Fixture(Proposal proposal) {
    }
}
