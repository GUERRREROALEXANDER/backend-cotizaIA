package com.cotizaia.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotizaia.repository.AgencyRepository;
import com.cotizaia.repository.BriefRepository;
import com.cotizaia.repository.ClientRepository;
import com.cotizaia.repository.ExtractedRequirementRepository;
import com.cotizaia.repository.ProposalRepository;
import com.cotizaia.repository.ScheduleRepository;
import com.cotizaia.repository.ServiceCatalogRepository;
import com.cotizaia.service.ScheduleService;
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
 * Persistence acceptance for issue #10: the {@code schedules}, {@code phases}
 * and {@code phase_dependencies} tables exist, the aggregate round-trips, and
 * the hour edit cascades through the stored schedule.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchedulePersistenceTests {

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
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v10CreatesScheduleTables() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        for (String table : new String[] {"schedules", "phases", "phase_dependencies"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables"
                            + " WHERE LOWER(table_name) = LOWER(?) AND LOWER(table_schema) = 'public'",
                    Integer.class,
                    table);
            assertThat(count).as("table %s exists", table).isEqualTo(1);
        }
    }

    @Test
    void persistsScheduleWithPhasesAndRoundTripsPlacement() {
        Fixture fixture = fixture("Agencia Cronograma");
        Schedule schedule = scheduleService.createFor(
                fixture.proposal().getId(),
                new BigDecimal("40"),
                List.of(new ScheduleService.PhaseSpec("Design UX", new BigDecimal("0.2500")),
                        new ScheduleService.PhaseSpec("Development", new BigDecimal("0.5000")),
                        new ScheduleService.PhaseSpec("Integration", new BigDecimal("0.1250")),
                        new ScheduleService.PhaseSpec("QA + launch", new BigDecimal("0.1250"))));

        Schedule reloaded = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getProposal().getId()).isEqualTo(fixture.proposal().getId());
        assertThat(reloaded.getTotalHours()).isEqualByComparingTo("120.00");
        assertThat(reloaded.getPhases()).hasSize(4);
        assertThat(reloaded.getPhases()).extracting(Phase::getWeeks)
                .containsExactly(1, 2, 1, 1);
        assertThat(reloaded.getPhases()).extracting(Phase::getStartWeek)
                .containsExactly(1, 1, 1, 1);
    }

    @Test
    void hourEditAtApprovalCascadesThroughThePersistedSchedule() {
        Fixture fixture = fixture("Agencia Cascada");
        Schedule schedule = scheduleService.createFor(
                fixture.proposal().getId(),
                new BigDecimal("40"),
                List.of(new ScheduleService.PhaseSpec("Design UX", new BigDecimal("0.2500")),
                        new ScheduleService.PhaseSpec("Development", new BigDecimal("0.5000")),
                        new ScheduleService.PhaseSpec("Integration", new BigDecimal("0.1250")),
                        new ScheduleService.PhaseSpec("QA + launch", new BigDecimal("0.1250"))));
        scheduleService.addDependency(
                schedule.getProposal().getId(),
                schedule.getPhases().get(1).getId(),
                schedule.getPhases().get(0).getId());
        scheduleService.addDependency(
                schedule.getProposal().getId(),
                schedule.getPhases().get(2).getId(),
                schedule.getPhases().get(1).getId());
        scheduleService.addDependency(
                schedule.getProposal().getId(),
                schedule.getPhases().get(3).getId(),
                schedule.getPhases().get(2).getId());

        Long designItemId = fixture.proposal().getItems().get(0).getId();
        scheduleService.editHours(fixture.proposal().getId(), designItemId, new BigDecimal("90.00"));

        Schedule reloaded = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertThat(reloaded.getTotalHours()).isEqualByComparingTo("180.00");
        assertThat(reloaded.getPhases().get(0).getWeeks()).isEqualTo(2);
        assertThat(reloaded.getPhases().get(1).getStartWeek()).isEqualTo(3);
        assertThat(reloaded.getPhases().get(1).getEndWeek()).isEqualTo(5);
        assertThat(reloaded.getPhases().get(3).getStartWeek()).isEqualTo(7);
    }

    @Test
    void cascadeDeletesPhasesAndDependenciesWithTheirSchedule() {
        Fixture fixture = fixture("Agencia Cascade V10");
        Schedule schedule = scheduleService.createFor(
                fixture.proposal().getId(),
                new BigDecimal("40"),
                List.of(new ScheduleService.PhaseSpec("Design UX", new BigDecimal("0.2500")),
                        new ScheduleService.PhaseSpec("Development", new BigDecimal("0.7500"))));
        scheduleService.addDependency(
                schedule.getProposal().getId(),
                schedule.getPhases().get(1).getId(),
                schedule.getPhases().get(0).getId());
        Long scheduleId = schedule.getId();
        Long dependentPhaseId = schedule.getPhases().get(1).getId();
        Long prerequisitePhaseId = schedule.getPhases().get(0).getId();

        jdbcTemplate.update("DELETE FROM schedules WHERE id = ?", scheduleId);

        Integer phases = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phases WHERE schedule_id = ?", Integer.class, scheduleId);
        // Scoped to this schedule's phases: other tests (e.g. the HTTP end-to-end flow) commit their own schedules.
        Integer edges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phase_dependencies WHERE phase_id IN (?, ?) OR depends_on_phase_id IN (?, ?)",
                Integer.class, dependentPhaseId, prerequisitePhaseId, dependentPhaseId, prerequisitePhaseId);
        assertThat(phases).isZero();
        assertThat(edges).isZero();
    }

    @Test
    void rejectsSelfDependencyByCheckConstraint() {
        Fixture fixture = fixture("Agencia Check Edge");
        Schedule schedule = scheduleService.createFor(
                fixture.proposal().getId(),
                new BigDecimal("40"),
                List.of(new ScheduleService.PhaseSpec("Design UX", new BigDecimal("1.0000"))));
        Long phaseId = schedule.getPhases().get(0).getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO phase_dependencies (phase_id, depends_on_phase_id) VALUES (?, ?)",
                phaseId, phaseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsASecondScheduleForTheSameProposal() {
        Fixture fixture = fixture("Agencia Unico Cronograma");
        scheduleService.createFor(
                fixture.proposal().getId(),
                new BigDecimal("40"),
                List.of(new ScheduleService.PhaseSpec("Design UX", new BigDecimal("1.0000"))));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO schedules (proposal_id, total_hours, hours_per_week, created_at)"
                        + " VALUES (?, 10, 40, CURRENT_TIMESTAMP)",
                fixture.proposal().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture(String agencyName) {
        Agency agency = agencyRepository.saveAndFlush(new Agency(agencyName));
        Client client = clientRepository.saveAndFlush(
                new Client(agency, "Cliente " + agencyName, agencyName + "@cronograma.co"));
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
                .addItem(requirement, new BigDecimal("30"), new BigDecimal("50"))
                .addItem(requirement, new BigDecimal("90"), new BigDecimal("50"))
                .build();
        proposal = proposalRepository.saveAndFlush(proposal);
        return new Fixture(proposal);
    }

    private record Fixture(Proposal proposal) {
    }
}
